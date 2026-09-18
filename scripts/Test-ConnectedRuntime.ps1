[CmdletBinding()]
param([string]$EnvFile)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$temporaryEnvFile = $null
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $temporaryEnvFile = Join-Path ([IO.Path]::GetTempPath()) ('jscc-connected-' + [Guid]::NewGuid().ToString('N') + '.env')
    $dataRoot = (Join-Path $workspace 'tmp/connected-config-validation').Replace('\', '/')
    [IO.File]::WriteAllLines($temporaryEnvFile, @(
        "APP_DATA_ROOT=$dataRoot",
        'POSTGRES_PASSWORD=connected-config-validation',
        'CONNECTED_BROKER_TOKEN=connected-structural-check-token-32-characters'
    ), [Text.UTF8Encoding]::new($false))
    $EnvFile = $temporaryEnvFile
}

function Assert-Check([string]$Name, [bool]$Passed) {
    if (!$Passed) { throw "Connected-runtime check failed: $Name" }
    Write-Output "PASS: $Name"
}

try {
    $base = (& docker compose --env-file $EnvFile -f (Join-Path $workspace 'compose.yaml') config --format json) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Default Compose configuration could not be rendered.' }
    $connected = (& docker compose --env-file $EnvFile -f (Join-Path $workspace 'compose.yaml') -f (Join-Path $workspace 'compose.connected.yaml') config --format json) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Connected Compose configuration could not be rendered.' }
    $baseConfig = ConvertFrom-Json $base
    $config = ConvertFrom-Json $connected

    Assert-Check 'Default local-only topology has no egress broker' ($null -eq $baseConfig.services.PSObject.Properties['egress-broker'])
    $apiNetworks = @($config.services.api.networks.PSObject.Properties.Name)
    Assert-Check 'API remains internal-only in connected mode' ($apiNetworks.Count -eq 1 -and $apiNetworks[0] -eq 'local_only')
    foreach ($name in @('portal', 'postgres')) {
        $networks = @($config.services.$name.networks.PSObject.Properties.Name)
        Assert-Check "$name remains internal-only" ($networks.Count -eq 1 -and $networks[0] -eq 'local_only')
    }
    $broker = $config.services.'egress-broker'
    $brokerNetworks = @($broker.networks.PSObject.Properties.Name | Sort-Object)
    Assert-Check 'Broker is the only connected egress bridge' ($brokerNetworks.Count -eq 2 -and $brokerNetworks[0] -eq 'connected_egress' -and $brokerNetworks[1] -eq 'local_only')
    Assert-Check 'Broker publishes no host port' ($null -eq $broker.PSObject.Properties['ports'])
    Assert-Check 'Broker mounts no workspace or host path' ($null -eq $broker.PSObject.Properties['volumes'])
    Assert-Check 'Broker root filesystem is read-only' ([bool]$broker.read_only)
    Assert-Check 'Broker drops all capabilities' (@($broker.cap_drop) -contains 'ALL')
    Assert-Check 'Broker enables no-new-privileges' (@($broker.security_opt) -contains 'no-new-privileges:true')

    $source = Get-Content -LiteralPath (Join-Path $workspace 'egress-broker/server.mjs') -Raw
    Assert-Check 'OpenAI route is fixed' ($source -match 'https://api\.openai\.com/v1/responses')
    Assert-Check 'Arbitrary broker routes fail closed' ($source -match 'This outbound route is not allowlisted')
    Assert-Check 'Job redirects are revalidated' ($source -match 'target = validateJobUrl\(new URL\(response\.headers\.location')
    Assert-Check 'Job response size is bounded' ($source -match 'MAX_JOB_PAGE_BYTES')
    Write-Output 'Connected-runtime structural acceptance passed. No external request was made.'
} finally {
    if ($temporaryEnvFile -and (Test-Path -LiteralPath $temporaryEnvFile)) {
        Remove-Item -LiteralPath $temporaryEnvFile -Force
    }
}
