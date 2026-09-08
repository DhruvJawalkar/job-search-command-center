param(
    [int]$DatabasePort = 55436,
    [int]$ApiPort = 8083,
    [string]$Java,
    [switch]$KeepEnvironment
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Recovery.Common.ps1"
$workspace = Split-Path $PSScriptRoot -Parent

if ($DatabasePort -in @(5432, 55432, 55435) -or $ApiPort -in @(8080, 8081) -or $DatabasePort -eq $ApiPort) {
    throw 'Fresh-setup ports must be isolated from live and retained acceptance environments.'
}
foreach ($port in @($DatabasePort, $ApiPort)) {
    if (Test-RecoveryPort $port) { throw "Isolated port $port is already in use." }
}

function Get-JsonArrayCount([string]$Uri) {
    $content = (Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10).Content
    $parsed = ConvertFrom-Json -InputObject $content
    if ($null -eq $parsed) { return 0 }
    if ($parsed -is [Array]) { return $parsed.Count }
    return 1
}

$run = Join-Path $workspace ('tmp/c6-fresh-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $run | Out-Null
foreach ($folder in @('application-resumes', 'notes', 'daily-high-fit-job-roles', 'linkedin-data-import', 'company-targets')) {
    New-Item -ItemType Directory -Path (Join-Path $run $folder) | Out-Null
}

$container = 'job-search-c6-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$password = [Guid]::NewGuid().ToString('N')
$api = $null
$createdContainer = $false
$previous = @{}

try {
    Push-Location (Join-Path $workspace 'backend')
    try {
        & mvn -q '-DskipTests' '-Dapp.build.name=c6-fresh' package
        if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' }
    } finally { Pop-Location }

    Invoke-DockerChecked @(
        'run', '-d', '--name', $container,
        '--label', 'job-search.release-gate=c6-fresh',
        '-p', "127.0.0.1:${DatabasePort}:5432",
        '-e', 'POSTGRES_DB=c6_fresh',
        '-e', 'POSTGRES_USER=c6_fresh',
        '-e', "POSTGRES_PASSWORD=$password",
        'postgres:17-alpine'
    ) | Out-Null
    $createdContainer = $true

    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $state = & docker exec $container pg_isready -U c6_fresh -d c6_fresh 2>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw 'Fresh PostgreSQL instance did not become ready.' }

    $settings = @{
        DB_URL = "jdbc:postgresql://127.0.0.1:$DatabasePort/c6_fresh"
        DB_USERNAME = 'c6_fresh'
        DB_PASSWORD = $password
        SERVER_PORT = "$ApiPort"
        APP_SEED_DEMO = 'false'
        APP_DAILY_HIGH_FIT_IMPORT_ENABLED = 'false'
        APP_APPLICATION_RESUMES_FOLDER = (Join-Path $run 'application-resumes')
        APP_NOTES_FOLDER = (Join-Path $run 'notes')
        APP_DAILY_HIGH_FIT_FOLDER = (Join-Path $run 'daily-high-fit-job-roles')
        APP_LINKEDIN_CONNECTIONS_FILE = (Join-Path $run 'linkedin-data-import/Connections.csv')
        APP_COMPANY_TARGETS_FOLDER = (Join-Path $run 'company-targets')
        OPENAI_API_KEY = ''
        APP_OPENAI_MODEL = ''
    }
    foreach ($entry in Get-ChildItem Env: | Where-Object { $_.Name -match '^(APP_|DB_|SERVER_|OPENAI_)' }) {
        $previous[$entry.Name] = $entry.Value
        [Environment]::SetEnvironmentVariable($entry.Name, $null, 'Process')
    }
    foreach ($key in $settings.Keys) {
        if (!$previous.ContainsKey($key)) { $previous[$key] = $null }
        [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process')
    }

    if ([string]::IsNullOrWhiteSpace($Java)) {
        $javaHomeCandidate = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { $null }
        $Java = if ($javaHomeCandidate -and (Test-Path -LiteralPath $javaHomeCandidate -PathType Leaf)) {
            $javaHomeCandidate
        } else {
            (Get-Command java -ErrorAction Stop).Source
        }
    }
    $versionText = (& $Java -version 2>&1) -join ' '
    if ($versionText -notmatch 'version\s+"(?<major>\d+)') { throw "Could not determine Java version from $Java." }
    $major = [int]$Matches.major
    if ($major -lt 21) { throw "JDK 21 or newer is required; $Java reports major version $major." }
    $jar = Join-Path $workspace 'backend/target/c6-fresh.jar'
    $api = Start-Process -FilePath $Java -ArgumentList @(
        '-Duser.timezone=Asia/Kolkata',
        '-jar', ('"' + $jar + '"'),
        '--app.seed-demo=false',
        '--app.imports.daily-high-fit.enabled=false'
    ) -WorkingDirectory (Join-Path $workspace 'backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'api.log') -RedirectStandardError (Join-Path $run 'api-error.log')

    $healthy = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($api.HasExited) { throw 'Fresh API exited before becoming healthy.' }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$ApiPort/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (!$healthy) { throw 'Fresh API did not become healthy.' }

    $applicationCount = Get-JsonArrayCount "http://127.0.0.1:$ApiPort/api/v1/applications"
    $opportunityCount = Get-JsonArrayCount "http://127.0.0.1:$ApiPort/api/v1/opportunities"
    if ($applicationCount -ne 0 -or $opportunityCount -ne 0) {
        throw "Fresh database unexpectedly contains records: applications=$applicationCount, opportunities=$opportunityCount."
    }

    $migrations = [int](Invoke-DockerChecked @('exec', $container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'c6_fresh', '-d', 'c6_fresh', '-c', 'SELECT count(*) FROM flyway_schema_history WHERE success;')).Trim()
    if ($migrations -ne 27) { throw "Expected 27 successful migrations, found $migrations." }

    $report = [ordered]@{
        checkedAt = [DateTime]::UtcNow.ToString('o')
        passed = $true
        database = 'fresh disposable PostgreSQL 17-alpine'
        databasePort = $DatabasePort
        apiPort = $ApiPort
        loopbackOnly = $true
        migrations = $migrations
        applications = $applicationCount
        opportunities = $opportunityCount
        demoSeeded = $false
        automaticImports = $false
    }
    [IO.File]::WriteAllText((Join-Path $run 'fresh-setup.json'), ($report | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
    Write-Output "C6 fresh setup passed: $run"
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    if ($api -and !$api.HasExited) {
        Stop-Process -Id $api.Id -ErrorAction SilentlyContinue
        $api.WaitForExit()
    }
    if ($createdContainer -and !$KeepEnvironment) {
        $label = (& docker inspect --format '{{ index .Config.Labels "job-search.release-gate" }}' $container 2>$null)
        if ($LASTEXITCODE -eq 0 -and $label -eq 'c6-fresh') { & docker rm -f $container | Out-Null }
    }
}
