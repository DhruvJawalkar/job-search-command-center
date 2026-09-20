param(
    [switch]$SkipRuntime
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$results = [System.Collections.Generic.List[object]]::new()

function Add-Check([string]$Name, [bool]$Passed, [string]$Evidence) {
    $results.Add([ordered]@{ name = $Name; passed = $Passed; evidence = $Evidence })
    if (!$Passed) { throw "Release gate failed: $Name. $Evidence" }
}

function Assert-Contains([string]$Path, [string]$Pattern, [string]$Name) {
    $content = Get-Content -LiteralPath (Join-Path $workspace $Path) -Raw
    Add-Check $Name ($content -match $Pattern) $Path
}

Push-Location $workspace
try {
    Assert-Contains 'backend/src/main/resources/application.yml' 'address:\s*\$\{SERVER_ADDRESS:127\.0\.0\.1\}' 'API defaults to loopback'
    Assert-Contains 'compose.yaml' 'local_only:\s*\r?\n\s+internal:\s*true' 'Compose default network is externally isolated'
    Assert-Contains 'compose.yaml' '127\.0\.0\.1:\$\{APP_API_HOST_PORT:-8080\}:8080' 'Gateway publishes API route on loopback'
    Assert-Contains 'compose.yaml' '127\.0\.0\.1:\$\{APP_PORTAL_HOST_PORT:-3000\}:3000' 'Gateway publishes portal route on loopback'
    Assert-Contains 'backend/pom.xml' '<jackson-bom\.version>3\.1\.5</jackson-bom\.version>' 'API uses the Docker Scout remediation release of Jackson Databind'
    Assert-Contains 'backend/pom.xml' '<log4j2\.version>2\.25\.5</log4j2\.version>' 'API uses the Docker Scout remediation release of Log4j'
    Assert-Contains 'backend/Dockerfile' 'apk del --no-network coreutils' 'API runtime removes unnecessary vulnerable coreutils'
    Assert-Contains 'backend/Dockerfile' 'ENV APP_VERSION=\$\{APP_VERSION\}' 'API runtime does not expose its immutable build version'
    Assert-Contains 'compose.yaml' 'test:\s*\["CMD", "wget", "--quiet", "--output-document=/dev/null", "http://127\.0\.0\.1:8080/actuator/health"\]' 'API health check does not require curl or nghttp2'
    Assert-Contains 'compose.yaml' 'APP_DISTRIBUTION_CHANNEL:\s*\$\{APP_DISTRIBUTION_CHANNEL:-source\}' 'source runtime does not identify its distribution channel'
    Assert-Contains 'compose.yaml' 'APP_CODEX_GUIDANCE_ENABLED:\s*\$\{APP_CODEX_GUIDANCE_ENABLED:-true\}' 'source runtime does not explicitly retain Codex guidance'
    Assert-Contains 'frontend/package.json' 'vinext start --hostname 127\.0\.0\.1' 'Production portal defaults to loopback'
    Assert-Contains 'frontend/package.json' 'vinext dev --hostname 127\.0\.0\.1' 'Development portal defaults to loopback'
    Assert-Contains 'AGENTS.md' 'guided-workflows/README\.md' 'Codex repository router is present'
    Assert-Contains 'guided-workflows/README.md' 'Help me on this page\.' 'Guided workflow entry prompt is documented'

    $workflowPages = @('getting-started', 'summary', 'opportunities', 'network', 'market-and-skills', 'preparation', 'weekly-review', 'profile')
    foreach ($page in $workflowPages) {
        $index = Join-Path $workspace "guided-workflows/$page/README.md"
        Add-Check "Guided workflow index: $page" (Test-Path -LiteralPath $index -PathType Leaf) $index
        $workflows = @(Get-ChildItem -LiteralPath (Split-Path $index -Parent) -Filter '*.md' -File | Where-Object Name -ne 'README.md')
        Add-Check "Guided workflow count: $page" ($workflows.Count -ge 3 -and $workflows.Count -le 5) "count=$($workflows.Count)"
    }

    $privateProbes = @(
        'application-resumes/example.pdf',
        'notes/example.txt',
        'backups/example/database.dump',
        'daily-high-fit-job-roles/example.xlsx',
        'linkedin-data-import/Connections.csv',
        'preparation-workspace/example.pdf',
        'agent-logs/scout/example.json',
        '.env',
        'Complete_LinkedInDataExport_example.zip',
        'LinkedIn connection request templates.txt',
        'role-outreach-targets-notes.txt',
        'AI_Technical_Skill_Gap_Backlog.md'
    )
    foreach ($path in $privateProbes) {
        & git check-ignore -q -- $path
        Add-Check "Private path ignored: $path" ($LASTEXITCODE -eq 0) '.gitignore'
    }

    $trackedIgnored = @(& git ls-files -ci --exclude-standard)
    Add-Check 'No ignored file remains tracked' ($trackedIgnored.Count -eq 0) $(if ($trackedIgnored.Count) { $trackedIgnored -join ', ' } else { 'none' })

    $secretPatterns = @(
        '-----BEGIN [A-Z ]*PRIVATE KEY-----',
        'sk-[A-Za-z0-9_-]{20,}',
        'gh[pousr]_[A-Za-z0-9]{20,}',
        'AIza[0-9A-Za-z_-]{20,}',
        'xox[baprs]-[A-Za-z0-9-]{10,}',
        'AKIA[0-9A-Z]{16}'
    )
    $secretHits = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($pattern in $secretPatterns) {
        foreach ($hit in @(& git grep -Il -E -- $pattern 2>$null)) { [void]$secretHits.Add($hit) }
        if ($LASTEXITCODE -notin @(0, 1)) { throw "Secret scan failed for a configured signature." }
    }
    Add-Check 'No high-confidence credential material is tracked' ($secretHits.Count -eq 0) $(if ($secretHits.Count) { ($secretHits | Sort-Object) -join ', ' } else { 'none' })

    & git diff --check | Out-Null
    Add-Check 'Git whitespace check' ($LASTEXITCODE -eq 0) 'git diff --check'

    & (Join-Path $workspace 'scripts/Test-ContainerReleaseWorkflow.ps1')
    Add-Check 'Container release workflow structure' $true 'scripts/Test-ContainerReleaseWorkflow.ps1'

    $localOnlyArguments = if ($SkipRuntime) { @() } else { @('-Runtime') }
    & (Join-Path $workspace 'scripts/Test-LocalOnlyRuntime.ps1') @localOnlyArguments
    Add-Check 'Local-only Compose acceptance' $true $(if ($SkipRuntime) { 'compose configuration' } else { 'compose configuration and live runtime' })

    if (!$SkipRuntime) {
        $listeners = @(netstat -ano)
        foreach ($port in @(3000, 8080)) {
            $loopback = @($listeners | Where-Object { $_ -match "^\s*TCP\s+127\.0\.0\.1:$port\s+.*LISTENING" })
            $broad = @($listeners | Where-Object { $_ -match "^\s*TCP\s+(0\.0\.0\.0|\[::\]):$port\s+.*LISTENING" })
            Add-Check "Runtime port $port is loopback-only" ($loopback.Count -gt 0 -and $broad.Count -eq 0) "loopback=$($loopback.Count); broad=$($broad.Count)"
        }
        $apiHealth = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 10
        Add-Check 'API health' ($apiHealth.status -eq 'UP') "status=$($apiHealth.status)"
        $portal = Invoke-WebRequest -Uri 'http://127.0.0.1:3000/#overview' -UseBasicParsing -TimeoutSec 10
        Add-Check 'Portal health' ($portal.StatusCode -eq 200) "status=$($portal.StatusCode)"
    }

    $folder = Join-Path $workspace ('tmp/c6-release-gate-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $folder | Out-Null
    $report = [ordered]@{
        checkedAt = [DateTime]::UtcNow.ToString('o')
        scope = $(if ($SkipRuntime) { 'static' } else { 'static-and-live-runtime' })
        passed = $true
        checks = $results
    }
    [IO.File]::WriteAllText((Join-Path $folder 'release-gate.json'), ($report | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
    Write-Output "C6 release gate passed: $folder"
} finally {
    Pop-Location
}
