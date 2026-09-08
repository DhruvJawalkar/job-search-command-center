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
    Assert-Contains 'compose.yaml' '127\.0\.0\.1:5432:5432' 'PostgreSQL publishes on loopback'
    Assert-Contains 'frontend/package.json' 'vinext start --hostname 127\.0\.0\.1' 'Production portal defaults to loopback'
    Assert-Contains 'frontend/package.json' 'vinext dev --hostname 127\.0\.0\.1' 'Development portal defaults to loopback'

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

    if (!$SkipRuntime) {
        $listeners = @(netstat -ano)
        foreach ($port in @(3000, 8080, 5432)) {
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
