[CmdletBinding()]
param(
    [string]$EnvFile,
    [string]$ProjectName = 'job-search-command-center',
    [switch]$Runtime
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$temporaryEnvFile = $null
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $defaultEnvFile = Join-Path $workspace 'workspace/.env'
    if (Test-Path -LiteralPath $defaultEnvFile -PathType Leaf) {
        $EnvFile = $defaultEnvFile
    } elseif ($Runtime) {
        throw 'Runtime acceptance requires -EnvFile or a workspace/.env created by setup.'
    } else {
        $temporaryFolder = Join-Path $workspace 'tmp/local-only-config'
        New-Item -ItemType Directory -Path $temporaryFolder -Force | Out-Null
        $temporaryEnvFile = Join-Path $temporaryFolder ([Guid]::NewGuid().ToString('N') + '.env')
        $dataRoot = (Join-Path $temporaryFolder 'data').Replace('\', '/')
        [IO.File]::WriteAllLines($temporaryEnvFile, @(
            "APP_DATA_ROOT=$dataRoot",
            'POSTGRES_PASSWORD=local-only-config-validation'
        ), [Text.UTF8Encoding]::new($false))
        $EnvFile = $temporaryEnvFile
    }
}
$EnvFile = [IO.Path]::GetFullPath($EnvFile)
if (!(Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "Environment file not found: $EnvFile"
}

$checks = [System.Collections.Generic.List[object]]::new()
function Add-Check([string]$Name, [bool]$Passed, [string]$Evidence) {
    $checks.Add([ordered]@{ name = $Name; passed = $Passed; evidence = $Evidence })
    if (!$Passed) { throw "Local-only acceptance failed: $Name. $Evidence" }
}

function Invoke-Compose([string[]]$Arguments) {
    $output = & docker compose --project-name $ProjectName --env-file $EnvFile --file (Join-Path $workspace 'compose.yaml') @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed: $($Arguments -join ' ')" }
    return $output
}

function Get-Service([object]$Config, [string]$Name) {
    $property = $Config.services.PSObject.Properties[$Name]
    if ($null -eq $property) { throw "Compose service is missing: $Name" }
    return $property.Value
}

function Get-PropertyCount([object]$Value, [string]$Name) {
    $property = $Value.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) { return 0 }
    return @($property.Value).Count
}

Push-Location $workspace
try {
    $configJson = (Invoke-Compose @('config', '--format', 'json')) -join "`n"
    $config = ConvertFrom-Json -InputObject $configJson
    $network = $config.networks.PSObject.Properties['local_only']
    Add-Check 'Compose local_only network exists' ($null -ne $network) 'compose.yaml networks.local_only'
    Add-Check 'Compose local_only network is internal' ([bool]$network.Value.internal) 'internal=true'

    $postgres = Get-Service $config 'postgres'
    $api = Get-Service $config 'api'
    $portal = Get-Service $config 'portal'
    $gateway = Get-Service $config 'gateway'
    Add-Check 'PostgreSQL has no published host port' ((Get-PropertyCount $postgres 'ports') -eq 0) 'published ports=0'
    Add-Check 'API has no direct host port' ((Get-PropertyCount $api 'ports') -eq 0) 'published ports=0'
    Add-Check 'Portal has no direct host port' ((Get-PropertyCount $portal 'ports') -eq 0) 'published ports=0'
    $gatewayPorts = @($gateway.ports)
    $expectedGatewayTargets = @(3000, 8080)
    $loopbackGatewayPorts = @($gatewayPorts | Where-Object { $_.host_ip -eq '127.0.0.1' -and [int]$_.target -in $expectedGatewayTargets })
    $broadGatewayPorts = @($gatewayPorts | Where-Object { $_.host_ip -ne '127.0.0.1' })
    Add-Check 'Gateway publishes only portal and API on IPv4 loopback' ($gatewayPorts.Count -eq 2 -and $loopbackGatewayPorts.Count -eq 2 -and $broadGatewayPorts.Count -eq 0) "ports=$($gatewayPorts | ConvertTo-Json -Compress)"

    foreach ($entry in @(
        @{ name = 'PostgreSQL'; service = $postgres; requireDrop = $false },
        @{ name = 'API'; service = $api; requireDrop = $true },
        @{ name = 'Portal'; service = $portal; requireDrop = $true }
    )) {
        $networks = @($entry.service.networks.PSObject.Properties.Name)
        Add-Check "$($entry.name) uses only local_only network" ($networks.Count -eq 1 -and $networks[0] -eq 'local_only') ($networks -join ',')
        $securityOptions = @($entry.service.security_opt)
        Add-Check "$($entry.name) enables no-new-privileges" ($securityOptions -contains 'no-new-privileges:true') ($securityOptions -join ',')
        if ($entry.requireDrop) {
            Add-Check "$($entry.name) drops all Linux capabilities" (@($entry.service.cap_drop) -contains 'ALL') (@($entry.service.cap_drop) -join ',')
        }
    }

    $gatewayNetworks = @($gateway.networks.PSObject.Properties.Name | Sort-Object)
    Add-Check 'Gateway is the only dual-network ingress boundary' ($gatewayNetworks.Count -eq 2 -and $gatewayNetworks[0] -eq 'ingress' -and $gatewayNetworks[1] -eq 'local_only') ($gatewayNetworks -join ',')
    Add-Check 'Gateway enables no-new-privileges' (@($gateway.security_opt) -contains 'no-new-privileges:true') (@($gateway.security_opt) -join ',')
    Add-Check 'Gateway drops all Linux capabilities' (@($gateway.cap_drop) -contains 'ALL') (@($gateway.cap_drop) -join ',')
    Add-Check 'Gateway root filesystem is read-only' ([bool]$gateway.read_only) "read_only=$($gateway.read_only)"
    Add-Check 'Gateway has no workspace or secret mounts' ((Get-PropertyCount $gateway 'volumes') -eq 0) 'volumes=0'
    $gatewayConfig = Get-Content -LiteralPath (Join-Path $workspace 'gateway/nginx.conf') -Raw
    $dockerDnsResolver = ([regex]::Matches($gatewayConfig, '(?m)^\s*resolver\s+127\.0\.0\.11\s+valid=10s\s+ipv6=off;\s*$')).Count
    $fixedPortalDestination = ([regex]::Matches($gatewayConfig, '(?m)^\s*set\s+\$portal_upstream\s+portal:3000;\s*$')).Count
    $fixedApiDestination = ([regex]::Matches($gatewayConfig, '(?m)^\s*set\s+\$api_upstream\s+api:8080;\s*$')).Count
    $portalRoute = ([regex]::Matches($gatewayConfig, '(?m)^\s*proxy_pass\s+http://\$portal_upstream;\s*$')).Count
    $apiRoute = ([regex]::Matches($gatewayConfig, '(?m)^\s*proxy_pass\s+http://\$api_upstream;\s*$')).Count
    $allProxyRoutes = ([regex]::Matches($gatewayConfig, '(?m)^\s*proxy_pass\s+[^;]+;\s*$')).Count
    $userControlledUpstream = ([regex]::Matches($gatewayConfig, '(?m)^\s*proxy_pass\s+[^;]*\$(?:http_host|host|request_uri|arg_[A-Za-z0-9_]*|cookie_[A-Za-z0-9_]*|http_[A-Za-z0-9_]*|request_body)')).Count
    Add-Check 'Gateway refreshes fixed routes through Docker DNS' ($dockerDnsResolver -eq 1 -and $fixedPortalDestination -eq 1 -and $fixedApiDestination -eq 1 -and $portalRoute -eq 1 -and $apiRoute -eq 1) 'resolver=127.0.0.11; portal:3000 and api:8080 are fixed variables'
    Add-Check 'Gateway upstream selection is not user-controlled' ($allProxyRoutes -eq 2 -and $userControlledUpstream -eq 0) "proxy routes=$allProxyRoutes; user-controlled expressions=$userControlledUpstream"

    if ($Runtime) {
        $ids = @{}
        foreach ($serviceName in @('postgres', 'api', 'portal', 'gateway')) {
            $id = ((Invoke-Compose @('ps', '--quiet', $serviceName)) -join '').Trim()
            Add-Check "$serviceName container is running" (![string]::IsNullOrWhiteSpace($id)) "container=$id"
            $ids[$serviceName] = $id
        }

        $actualNetworkName = [string]$network.Value.name
        $actualInternal = (& docker network inspect --format '{{.Internal}}' $actualNetworkName 2>$null) -join ''
        Add-Check 'Runtime local_only network is internal' ($LASTEXITCODE -eq 0 -and $actualInternal.Trim() -eq 'true') "network=$actualNetworkName; internal=$($actualInternal.Trim())"

        foreach ($entry in @(
            @{ name = 'PostgreSQL'; id = $ids.postgres },
            @{ name = 'API'; id = $ids.api },
            @{ name = 'Portal'; id = $ids.portal }
        )) {
            $bindingsJson = (& docker inspect --format '{{json .HostConfig.PortBindings}}' $entry.id) -join ''
            Add-Check "Runtime $($entry.name) has no direct host binding" ($LASTEXITCODE -eq 0 -and $bindingsJson.Trim() -eq '{}') "bindings=$($bindingsJson.Trim())"
        }

        $gatewayBindingsJson = (& docker inspect --format '{{json .HostConfig.PortBindings}}' $ids.gateway) -join ''
        $gatewayBindings = ConvertFrom-Json -InputObject $gatewayBindingsJson
        $apiBinding = @($gatewayBindings.'8080/tcp')
        $portalBinding = @($gatewayBindings.'3000/tcp')
        $gatewayLoopbackOnly = $apiBinding.Count -eq 1 -and $portalBinding.Count -eq 1 -and $apiBinding[0].HostIp -eq '127.0.0.1' -and $portalBinding[0].HostIp -eq '127.0.0.1'
        Add-Check 'Runtime gateway bindings are loopback-only' $gatewayLoopbackOnly "bindings=$gatewayBindingsJson"

        $apiHealth = Invoke-RestMethod -Uri "http://127.0.0.1:$($apiBinding[0].HostPort)/actuator/health" -TimeoutSec 10
        Add-Check 'API remains healthy through loopback' ($apiHealth.status -eq 'UP') "status=$($apiHealth.status)"
        $portalResponse = Invoke-WebRequest -Uri "http://127.0.0.1:$($portalBinding[0].HostPort)" -UseBasicParsing -TimeoutSec 10
        Add-Check 'Portal remains healthy through loopback' ($portalResponse.StatusCode -eq 200) "status=$($portalResponse.StatusCode)"

        & docker exec $ids.api getent hosts example.com *> $null
        Add-Check 'API external DNS lookup is blocked' ($LASTEXITCODE -ne 0) 'getent hosts example.com failed as expected'
        & docker exec $ids.api curl --fail --silent --show-error --connect-timeout 5 https://example.com *> $null
        Add-Check 'API outbound HTTPS is blocked' ($LASTEXITCODE -ne 0) 'curl https://example.com failed as expected'

        $dnsProbe = "require('node:dns').promises.lookup('example.com').then(()=>process.exit(0)).catch(()=>process.exit(23))"
        & docker exec $ids.portal node -e $dnsProbe *> $null
        Add-Check 'Portal external DNS lookup is blocked' ($LASTEXITCODE -ne 0) 'dns.lookup(example.com) failed as expected'
        $httpsProbe = "fetch('https://example.com',{signal:AbortSignal.timeout(5000)}).then(()=>process.exit(0)).catch(()=>process.exit(23))"
        & docker exec $ids.portal node -e $httpsProbe *> $null
        Add-Check 'Portal outbound HTTPS is blocked' ($LASTEXITCODE -ne 0) 'fetch(https://example.com) failed as expected'
    }

    $folder = Join-Path $workspace ('tmp/local-only-acceptance-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $folder | Out-Null
    $report = [ordered]@{
        checkedAt = [DateTime]::UtcNow.ToString('o')
        project = $ProjectName
        scope = $(if ($Runtime) { 'compose-and-runtime' } else { 'compose' })
        passed = $true
        checks = $checks
    }
    [IO.File]::WriteAllText((Join-Path $folder 'local-only-acceptance.json'), ($report | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    Write-Output "Local-only acceptance passed: $folder"
} finally {
    Pop-Location
    if ($temporaryEnvFile -and (Test-Path -LiteralPath $temporaryEnvFile -PathType Leaf)) {
        Remove-Item -LiteralPath $temporaryEnvFile -Force
    }
}
