[CmdletBinding()]
param(
    [string]$WorkspaceFolder,
    [switch]$Demo,
    [switch]$Empty,
    [ValidateRange(1,65535)][int]$PortalPort = 3000,
    [ValidateRange(1,65535)][int]$ApiPort = 8080
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Demo -and $Empty) { throw 'Choose either -Demo or -Empty, not both.' }
if ($PortalPort -eq $ApiPort) { throw 'Portal and API ports must be different.' }

$bundleRoot = Split-Path $MyInvocation.MyCommand.Path -Parent
$defaultBase = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } elseif ($HOME) { $HOME } else { (Get-Location).Path }
$defaultFolder = Join-Path $defaultBase 'JobSearchCommandCenter'

Write-Host ''
Write-Host 'Job Search Command Center — standalone Docker setup' -ForegroundColor Cyan
Write-Host 'This source-free installer will:'
Write-Host '  1. Create a private local workspace and generated database password.'
Write-Host '  2. Pull the signed release images; it will not build application source.'
Write-Host '  3. Start the app on the loopback-only local URL.'
Write-Host 'Expected time: 3–8 minutes on the first run.'
Write-Host ''

if ([string]::IsNullOrWhiteSpace($WorkspaceFolder)) {
    $answer = Read-Host "Workspace folder [$defaultFolder]"
    $WorkspaceFolder = if ([string]::IsNullOrWhiteSpace($answer)) { $defaultFolder } else { $answer }
}
$WorkspaceFolder = [IO.Path]::GetFullPath($WorkspaceFolder)
if ($WorkspaceFolder -match '[\r\n#]') { throw 'The workspace folder cannot contain a line break or # character.' }

if (!$Demo -and !$Empty) {
    Write-Host 'Choose the first-run experience:'
    Write-Host '  1. Start empty (recommended for personal use)'
    Write-Host '  2. Load synthetic demo content'
    $choice = Read-Host 'Selection [1]'
    $Demo = $choice -eq '2'
}
$requestedDemo = [bool]$Demo

foreach ($required in @('compose.yaml','gateway/nginx.conf','release-manifest.json','VERSION','jscc.ps1','jscc.sh')) {
    if (!(Test-Path -LiteralPath (Join-Path $bundleRoot $required))) { throw "Release bundle is incomplete: $required is missing." }
}
if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker was not found. Install and start Docker Desktop, then run this script again.' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker is installed but is not running. Start Docker Desktop, then try again.' }
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 is required.' }

$folders = @('postgres-data','application-resumes','notes','daily-high-fit-job-roles','linkedin-data-import','preparation-workspace','company-targets','backups')
New-Item -ItemType Directory -Path $WorkspaceFolder -Force | Out-Null
foreach ($folder in $folders) { New-Item -ItemType Directory -Path (Join-Path $WorkspaceFolder $folder) -Force | Out-Null }

$envFile = Join-Path $WorkspaceFolder '.env'
if (Test-Path -LiteralPath $envFile) {
    $storedMode = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^APP_DEMO_MODE=' } | Select-Object -First 1
    if (!$storedMode) { throw 'The existing workspace .env has no APP_DEMO_MODE value.' }
    $storedDemo = (($storedMode -split '=', 2)[1]).Trim().ToLowerInvariant() -eq 'true'
    if (($Demo -or $Empty) -and $storedDemo -ne $requestedDemo) { throw 'This workspace was initialized in a different data mode. Choose another workspace folder.' }
    Write-Host "Reusing existing local configuration: $envFile" -ForegroundColor Yellow
} else {
    $normalized = $WorkspaceFolder.ToLowerInvariant()
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = [Convert]::ToHexString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($normalized))).Substring(0,8).ToLowerInvariant() } finally { $sha.Dispose() }
    $version = (Get-Content -LiteralPath (Join-Path $bundleRoot 'VERSION') -Raw).Trim().TrimStart('v')
    $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N').Substring(0,16)
    $demoValue = if ($requestedDemo) { 'true' } else { 'false' }
    @(
        "COMPOSE_PROJECT_NAME=job-search-command-center-$hash"
        'APP_UID=1000'
        'APP_GID=1000'
        'POSTGRES_DB=job_search'
        'POSTGRES_USER=job_search'
        "POSTGRES_PASSWORD=$password"
        "APP_SEED_DEMO=$demoValue"
        "APP_DEMO_MODE=$demoValue"
        "APP_API_HOST_PORT=$ApiPort"
        "APP_PORTAL_HOST_PORT=$PortalPort"
        "APP_VERSION=$version"
        'APP_DISTRIBUTION_CHANNEL=standalone'
        'APP_CODEX_GUIDANCE_ENABLED=false'
    ) | Set-Content -LiteralPath $envFile -Encoding utf8NoBOM
}

$controlRoot = Join-Path $WorkspaceFolder '.jscc'
New-Item -ItemType Directory -Path (Join-Path $controlRoot 'gateway') -Force | Out-Null
foreach ($file in @('compose.yaml','compose.connected.yaml','release-manifest.json','SHA256SUMS','VERSION','LICENSE','NOTICE','TRADEMARKS.md','COMMERCIAL-LICENSE.md','jscc.ps1','jscc.sh')) {
    $source = Join-Path $bundleRoot $file
    if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination (Join-Path $controlRoot $file) -Force }
}
Copy-Item -LiteralPath (Join-Path $bundleRoot 'gateway/nginx.conf') -Destination (Join-Path $controlRoot 'gateway/nginx.conf') -Force
if (Test-Path -LiteralPath (Join-Path $bundleRoot 'README.md')) { Copy-Item -LiteralPath (Join-Path $bundleRoot 'README.md') -Destination (Join-Path $WorkspaceFolder 'README.md') -Force }

Write-Host ''
Write-Host 'Pulling and starting the accepted release…' -ForegroundColor Cyan
& (Join-Path $controlRoot 'jscc.ps1') start

Write-Host ''
Write-Host 'Setup complete.' -ForegroundColor Green
Write-Host "Workspace folder: $WorkspaceFolder"
Write-Host "App: http://127.0.0.1:$PortalPort"
Write-Host "Status: & '$controlRoot\jscc.ps1' status"
Write-Host "Stop:   & '$controlRoot\jscc.ps1' stop"
Write-Host "Logs:   & '$controlRoot\jscc.ps1' logs"
