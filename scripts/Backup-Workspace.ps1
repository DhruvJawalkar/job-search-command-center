[CmdletBinding()]
param(
    [string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent),
    [string]$Container = 'job-search-postgres',
    [string]$Database = 'job_search',
    [string]$DatabaseUser = 'job_search',
    [string]$SourceRevision = 'unrecorded (source and runtime hashes preserved)',
    [int]$ApiPort = 8080,
    [Parameter(Mandatory)][switch]$MaintenanceConfirmed
)
. "$PSScriptRoot/Recovery.Common.ps1"
if (!$MaintenanceConfirmed) { throw 'Stop the API and pause edits before confirming maintenance.' }
if (Test-RecoveryPort $ApiPort) { throw "API port $ApiPort is still listening. Stop the API before backing up." }
$root = (Resolve-Path -LiteralPath $ProjectRoot).Path
$id = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
$backup = Resolve-ChildPath $root "backups/$id"
if (Test-Path -LiteralPath $backup) { throw 'Backup target already exists.' }
$clientsSql = 'select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid();'
$clients = Invoke-DockerChecked -Arguments @('exec',$Container,'psql','-X','-qAt','-U',$DatabaseUser,'-d',$Database,'-c',$clientsSql)
if ([int]$clients -ne 0) { throw 'Database clients remain connected. Stop writers before backing up.' }
New-Item -ItemType Directory -Path $backup | Out-Null
$workspace = Join-Path $backup 'workspace'
New-Item -ItemType Directory -Path $workspace | Out-Null

# Explicit excludes prevent copying caches, past backups and secret configuration.
# Private user artifacts ARE included; this backup must remain private.
$skipDirectories = @('.git','.codex','.codex-tmp','.pnpm-store','node_modules','target','dist','.next','.vinext','.wrangler','backups','data','output','tmp','.idea','.vscode','.auth')
function Get-SourceFiles($Directory) {
    foreach ($item in Get-ChildItem -LiteralPath $Directory -Force) {
        if ($item.PSIsContainer -and $item.Name -in $skipDirectories) { continue }
        if ($item.Name -like '.env*' -and $item.Name -ne '.env.example') { continue }
        if ($item.Name -match '(?i)(credentials|client_secret|cookies|storage.?state|^id_rsa|^id_ed25519|^\.pgpass$|^pgpass\.conf$)' -or
            $item.Extension -in @('.pem','.p12','.pfx','.jks','.keystore','.log')) { continue }
        if ($item.LinkType) { throw "Linked paths require explicit review before backup: $($item.Name)" }
        if ($item.PSIsContainer) { Get-SourceFiles $item.FullName }
        else { $item }
    }
}

$baseline = @(Get-DatabaseFingerprint $Container $DatabaseUser $Database)
Write-RecoveryJson (Join-Path $backup 'database-baseline.json') $baseline
$artifactSql = "select coalesce(json_agg(x),'[]') from (select stored_relative_path as path, content_hash as sha256, size_bytes as bytes from application_artifact order by stored_relative_path) x;"
$artifacts = @( (Invoke-DockerChecked -Arguments @('exec',$Container,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U',$DatabaseUser,'-d',$Database,'-c',$artifactSql)) | ConvertFrom-Json )
Write-RecoveryJson (Join-Path $backup 'application-artifacts.json') $artifacts
Assert-ApplicationArtifacts (Join-Path $root 'application-resumes') $artifacts
$image = Invoke-DockerChecked -Arguments @('inspect','--format','{{.Image}}',$Container)
$dumpPath = "/tmp/job-search-$id.dump"
Invoke-DockerChecked -Arguments @('exec',$Container,'pg_dump','-U',$DatabaseUser,'-d',$Database,'--format=custom','--no-owner','--no-privileges',"--file=$dumpPath") | Out-Null
Invoke-DockerChecked -Arguments @('cp',"${Container}:$dumpPath",(Join-Path $backup 'database.dump')) | Out-Null

$sourceFiles = @(Get-SourceFiles $root | Sort-Object FullName)
$sourceHashes = @{}
foreach ($file in $sourceFiles) {
    $relative = $file.FullName.Substring($root.Length + 1)
    $destination = Resolve-ChildPath $workspace $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    $sourceHashes[$relative] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    Copy-Item -LiteralPath $file.FullName -Destination $destination -ErrorAction Stop
    if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $sourceHashes[$relative]) { throw 'File changed during snapshot.' }
}
foreach ($directory in @('application-resumes','notes')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $workspace $directory) | Out-Null
}
$jar = @(Get-ChildItem -LiteralPath (Join-Path $root 'backend/target') -Filter '*.jar')
if ($jar.Count -ne 1) { throw 'Build exactly one backend executable jar before taking a recovery snapshot.' }
Copy-Item -LiteralPath $jar[0].FullName -Destination (Join-Path $backup 'application.jar')
if ((Get-FileHash -LiteralPath $jar[0].FullName).Hash -ne (Get-FileHash -LiteralPath (Join-Path $backup 'application.jar')).Hash) { throw 'Runtime copy mismatch.' }

Assert-FingerprintEqual $baseline @(Get-DatabaseFingerprint $Container $DatabaseUser $Database)
$afterFiles = @(Get-SourceFiles $root | Sort-Object FullName)
if (($sourceFiles.FullName -join "`n") -cne ($afterFiles.FullName -join "`n")) { throw 'Source file inventory changed during snapshot.' }
foreach ($file in $afterFiles) {
    $relative = $file.FullName.Substring($root.Length + 1)
    if ((Get-FileHash -LiteralPath $file.FullName).Hash -ne $sourceHashes[$relative]) { throw 'Source changed during backup. Take a new snapshot.' }
}
Assert-ApplicationArtifacts (Join-Path $workspace 'application-resumes') $artifacts
$files = @(Get-ChildItem -LiteralPath $backup -File -Recurse | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($backup.Length+1).Replace('\','/'); bytes=$_.Length; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$manifest = [ordered]@{
    formatVersion=1; status='COMPLETE'; snapshotId=$id; createdAtUtc=[DateTime]::UtcNow.ToString('o')
    database=$Database; postgresImage=$image; sourceRevision=$SourceRevision; schemaMigrations=($baseline | Where-Object name -eq 'flyway_schema_history').rows
    applicationArtifactCount=$artifacts.Count; sourceFileCount=$sourceFiles.Count
    consistency='API stopped; no other initial DB clients; database fingerprints and source hashes equal before/after snapshot'
    storageRoots=@('application-resumes','notes','docs','daily-high-fit-job-roles','linkedin-data-import','preparation-workspace')
    exclusions=@('Browser localStorage and external accounts','Credentials and environment secrets','Build caches, prior backups, Git history, generated output and logs','Custom external storage roots: require a separately configured backup')
    files=$files
}
Write-RecoveryJson (Join-Path $backup 'manifest.json') $manifest
[IO.File]::WriteAllText((Join-Path $backup 'manifest.sha256'), (Get-FileHash -LiteralPath (Join-Path $backup 'manifest.json')).Hash)
Assert-BackupIntegrity $backup | Out-Null
Write-Output "Backup complete: $backup"
Write-Output "Source files: $($sourceFiles.Count); application artifacts: $($artifacts.Count); logical tables/sequences: $($baseline.Count)"
Write-Output "Private, unencrypted snapshot. Container staging dump retained at $dumpPath; no live data removed."
