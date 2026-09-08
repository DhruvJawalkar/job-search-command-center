[CmdletBinding()]
param(
    [string]$ProjectFolder,
    [switch]$Demo,
    [switch]$Empty
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Demo -and $Empty) { throw 'Choose either -Demo or -Empty, not both.' }

$sourceRoot = Split-Path $MyInvocation.MyCommand.Path -Parent
$composeFile = Join-Path $sourceRoot 'compose.yaml'
$defaultFolder = Join-Path (Get-Location) 'job-search-command-center-local'

Write-Host ''
Write-Host 'Job Search Command Center — local setup' -ForegroundColor Cyan
Write-Host 'This will:'
Write-Host '  1. Create a private local workspace and configuration.'
Write-Host '  2. Build PostgreSQL, API, and portal containers.'
Write-Host '  3. Start the app on http://127.0.0.1:3000.'
Write-Host 'Expected time: 5–10 minutes on the first run; later starts are faster.'
Write-Host 'Needed: Docker Desktop with Docker Compose and about 3 GB of free disk space.'
Write-Host ''

if ([string]::IsNullOrWhiteSpace($ProjectFolder)) {
    $answer = Read-Host "Local workspace folder [$defaultFolder]"
    $ProjectFolder = if ([string]::IsNullOrWhiteSpace($answer)) { $defaultFolder } else { $answer }
}
$ProjectFolder = [IO.Path]::GetFullPath($ProjectFolder)
if ($ProjectFolder -match '[\r\n#]') { throw 'The workspace folder cannot contain a line break or # character.' }

if (!$Demo -and !$Empty) {
    Write-Host 'Choose the first-run experience:'
    Write-Host '  1. Start empty (recommended for personal use)'
    Write-Host '  2. Load synthetic demo content'
    $choice = Read-Host 'Selection [1]'
    $Demo = $choice -eq '2'
}

if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker was not found. Install and start Docker Desktop, then run this script again.' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker is installed but is not running. Start Docker Desktop, then try again.' }
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 is required.' }

$folders = @('postgres-data','application-resumes','notes','daily-high-fit-job-roles','linkedin-data-import','preparation-workspace','company-targets','backups')
New-Item -ItemType Directory -Path $ProjectFolder -Force | Out-Null
foreach ($folder in $folders) { New-Item -ItemType Directory -Path (Join-Path $ProjectFolder $folder) -Force | Out-Null }

$envFile = Join-Path $ProjectFolder '.env'
if (Test-Path -LiteralPath $envFile) {
    Write-Host "Reusing existing local configuration: $envFile" -ForegroundColor Yellow
} else {
    $dockerPath = $ProjectFolder.Replace('\','/')
    $demoValue = if ($Demo) { 'true' } else { 'false' }
    $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N').Substring(0,16)
    @(
        "APP_DATA_ROOT=$dockerPath"
        'APP_UID=1000'
        'APP_GID=1000'
        'POSTGRES_DB=job_search'
        'POSTGRES_USER=job_search'
        "POSTGRES_PASSWORD=$password"
        "APP_SEED_DEMO=$demoValue"
        "APP_DEMO_MODE=$demoValue"
    ) | Set-Content -LiteralPath $envFile -Encoding utf8NoBOM
}

Copy-Item -LiteralPath (Join-Path $sourceRoot 'docs/LOCAL_WORKSPACE_README.md') -Destination (Join-Path $ProjectFolder 'README.md') -Force

Write-Host ''
Write-Host 'Building and starting the local app…' -ForegroundColor Cyan
& docker compose --project-name job-search-command-center --env-file $envFile --file $composeFile up --detach --build --wait
if ($LASTEXITCODE -ne 0) {
    & docker compose --project-name job-search-command-center --env-file $envFile --file $composeFile ps
    throw 'The containers did not become healthy. Review the status above and docs/INSTALLATION.md.'
}

Write-Host ''
Write-Host 'Setup complete.' -ForegroundColor Green
Write-Host "Local workspace: $ProjectFolder"
Write-Host 'App: http://127.0.0.1:3000'
Write-Host 'API health: http://127.0.0.1:8080/actuator/health'
Write-Host 'In Codex, ask: “Open my local Job Search Command Center and guide me through the first useful outcome.”'
