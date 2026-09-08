[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BackupFolder,
    [Parameter(Mandatory)][string]$JavaExecutable,
    [int]$DatabasePort = 55432,
    [int]$ApiPort = 8081,
    [switch]$VerifyOnly
)
. "$PSScriptRoot/Recovery.Common.ps1"
$folder = (Resolve-Path -LiteralPath $BackupFolder).Path
$manifest = Assert-BackupIntegrity $folder
if ($VerifyOnly) { Write-Output "Backup integrity verified: $($manifest.files.Count) files."; return }
if ($DatabasePort -eq 5432 -or $ApiPort -eq 8080 -or $DatabasePort -eq $ApiPort) { throw 'Rehearsal ports must not be production ports or equal.' }
if ((Test-RecoveryPort $DatabasePort) -or (Test-RecoveryPort $ApiPort)) { throw 'Rehearsal ports are in use.' }
$java = (Resolve-Path -LiteralPath $JavaExecutable).Path
$project = Split-Path $PSScriptRoot -Parent
$id = 'restore-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
$container = "job-search-$id"
$run = Resolve-ChildPath $project "backups/rehearsals/$id"
if (Test-Path -LiteralPath $run) { throw 'Rehearsal target already exists.' }
New-Item -ItemType Directory -Path $run | Out-Null
$state = Join-Path $run 'restored'
New-Item -ItemType Directory -Path $state | Out-Null
foreach ($file in $manifest.files) {
    $source = Resolve-ChildPath $folder $file.path
    $target = Resolve-ChildPath $state $file.path
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $file.sha256) { throw 'Restored file checksum mismatch.' }
}
$workspace = Join-Path $state 'workspace'
foreach ($name in @('application-resumes','notes')) {
    New-Item -ItemType Directory -Path (Join-Path $workspace $name) -Force | Out-Null
}
$baseline = @(Get-Content -LiteralPath (Join-Path $state 'database-baseline.json') -Raw | ConvertFrom-Json)
$artifacts = @(Get-Content -LiteralPath (Join-Path $state 'application-artifacts.json') -Raw | ConvertFrom-Json)
Assert-ApplicationArtifacts (Join-Path $workspace 'application-resumes') $artifacts
$password = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$created = $false
$process = $null
$results = [ordered]@{snapshotId=$manifest.snapshotId; startedAtUtc=[DateTime]::UtcNow.ToString('o'); status='RUNNING'; container=$container; databasePort=$DatabasePort; apiPort=$ApiPort; filesVerified=$manifest.files.Count; artifactsVerified=$artifacts.Count; logicalTablesVerified=$baseline.Count; startupChecks=@()}

function Start-RehearsalApi($Attempt) {
    # Environment values are scoped to the child; credentials never enter the log/report or Java command line.
    $settings = @{
        DB_URL="jdbc:postgresql://127.0.0.1:$DatabasePort/restore_verify"; DB_USERNAME='restore_verify'; DB_PASSWORD=$password
        SERVER_PORT="$ApiPort"; SERVER_ADDRESS='127.0.0.1'; APP_SEED_DEMO='false'; APP_DAILY_HIGH_FIT_IMPORT_ENABLED='false'
        APP_APPLICATION_RESUMES_FOLDER=(Join-Path $workspace 'application-resumes'); APP_NOTES_FOLDER=(Join-Path $workspace 'notes')
        APP_DAILY_HIGH_FIT_FOLDER=(Join-Path $workspace 'daily-high-fit-job-roles')
        APP_LINKEDIN_CONNECTIONS_FILE=(Join-Path $workspace 'linkedin-data-import/Connections.csv')
        APP_COMPANY_TARGETS_FOLDER=$workspace; OPENAI_API_KEY=''; APP_OPENAI_MODEL=''
        SPRING_FLYWAY_ENABLED='true'; SPRING_JPA_HIBERNATE_DDL_AUTO='validate'; SPRING_CONFIG_LOCATION='classpath:/application.yml'
    }
    $previous = @{}
    try {
        # Do not inherit production Spring overrides, profiles or injected JVM flags.
        # In particular SPRING_DATASOURCE_URL / SPRING_APPLICATION_JSON could otherwise
        # override DB_URL and accidentally reconnect the rehearsal to the live database.
        foreach ($entry in Get-ChildItem Env: | Where-Object { $_.Name -match '^(SPRING_|APP_|DB_|SERVER_|OPENAI_)|^(JAVA_TOOL_OPTIONS|_JAVA_OPTIONS|JDK_JAVA_OPTIONS)$' }) {
            $previous[$entry.Name]=$entry.Value
            [Environment]::SetEnvironmentVariable($entry.Name,$null,'Process')
        }
        foreach ($key in $settings.Keys) {
            if (!$previous.ContainsKey($key)) { $previous[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }
            [Environment]::SetEnvironmentVariable($key,$settings[$key],'Process')
        }
        $jar = Join-Path $state 'application.jar'
        # CreateProcess on Windows rejects long current-directory paths even when
        # file APIs accept them. All data roots above are absolute and isolated.
        return Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run "api-$Attempt.log") -RedirectStandardError (Join-Path $run "api-$Attempt-error.log")
    } finally {
        foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') }
    }
}

function Wait-RehearsalHealth($Process) {
    for ($try = 0; $try -lt 90; $try++) {
        if ($Process.HasExited) { throw 'Restored API exited before health became ready; inspect private rehearsal logs.' }
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$ApiPort/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw 'Restored API did not become healthy before the readiness timeout.'
}

try {
    # An isolated container with a new volume and a loopback-only port. No live DB connection is used.
    Invoke-DockerChecked -Arguments @('run','-d','--name',$container,'--label','job-search.recovery=rehearsal','-p',"127.0.0.1:${DatabasePort}:5432",'-e','POSTGRES_DB=restore_verify','-e','POSTGRES_USER=restore_verify','-e',"POSTGRES_PASSWORD=$password",$manifest.postgresImage) | Out-Null
    $created = $true
    $ready = $false
    for ($try=0; $try -lt 60; $try++) {
        & docker exec $container pg_isready -U restore_verify -d restore_verify *> $null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw 'Isolated PostgreSQL did not become ready.' }
    Invoke-DockerChecked -Arguments @('cp',(Join-Path $state 'database.dump'),"${container}:/tmp/restore.dump") | Out-Null
    Invoke-DockerChecked -Arguments @('exec',$container,'pg_restore','-U','restore_verify','-d','restore_verify','--no-owner','--no-privileges','--exit-on-error','--single-transaction','/tmp/restore.dump') | Out-Null
    Assert-FingerprintEqual $baseline @(Get-DatabaseFingerprint $container 'restore_verify' 'restore_verify')
    for ($attempt=1; $attempt -le 2; $attempt++) {
        Write-Output "Verifying restored API, startup $attempt of 2..."
        $process = Start-RehearsalApi $attempt
        Wait-RehearsalHealth $process
        $counts = [ordered]@{}
        foreach ($endpoint in @('applications','opportunities','contacts','outreach','preparation/overview','skills/overview','calendar/events','reviews/weekly')) {
            $payload = Invoke-RestMethod -Uri "http://127.0.0.1:$ApiPort/api/v1/$endpoint" -TimeoutSec 20
            $counts[$endpoint] = @($payload).Count
        }
        # Compare every table again: includes sprint memberships/status, sessions, events, reviews and FK rows.
        Assert-FingerprintEqual $baseline @(Get-DatabaseFingerprint $container 'restore_verify' 'restore_verify')
        Assert-ApplicationArtifacts (Join-Path $workspace 'application-resumes') $artifacts
        $results.startupChecks += [ordered]@{attempt=$attempt; health='UP'; endpointResponseItemsOrDocuments=$counts; schemaValidation='Flyway enabled; Hibernate validate; startup succeeded'; databaseEquality='PASS'; artifactEquality='PASS'}
        # Only the process created by this script is stopped. Never target a broad Java process list.
        Stop-Process -Id $process.Id -ErrorAction Stop
        $process.WaitForExit(); $process=$null
        if ($attempt -eq 1) {
            Invoke-DockerChecked -Arguments @('restart',$container) | Out-Null
            $ready=$false
            for ($try=0; $try -lt 60; $try++) {
                & docker exec $container pg_isready -U restore_verify -d restore_verify *> $null
                if ($LASTEXITCODE -eq 0) { $ready=$true; break }
                Start-Sleep -Milliseconds 500
            }
            if (!$ready) { throw 'Restored PostgreSQL failed restart readiness.' }
        }
    }
    $results.status='PASS'
} catch {
    $results.status='FAILED'
    $results.error=$_.Exception.Message
    throw
} finally {
    if ($null -ne $process -and !$process.HasExited) { Stop-Process -Id $process.Id; $process.WaitForExit() }
    if ($created) { Invoke-DockerChecked -Arguments @('stop',$container) | Out-Null }
    $results.finishedAtUtc=[DateTime]::UtcNow.ToString('o')
    $results.cleanup='Only rehearsal API/container stopped; restored data and backup retained. No live data overwritten or deleted.'
    Write-RecoveryJson (Join-Path $run 'verification.json') $results
    Write-Output "Rehearsal report: $(Join-Path $run 'verification.json')"
}
Write-Output 'Restore verified across two API starts and a PostgreSQL restart. Rehearsal container is stopped.'
