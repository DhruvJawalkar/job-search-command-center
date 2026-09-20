[CmdletBinding()]
param(
    [ValidateSet('start','stop','restart','status','logs','pull')]
    [string]$Command = 'status'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$controlRoot = Split-Path $MyInvocation.MyCommand.Path -Parent
$workspaceRoot = Split-Path $controlRoot -Parent
$composeFile = Join-Path $controlRoot 'compose.yaml'
$envFile = Join-Path $workspaceRoot '.env'

if (!(Test-Path -LiteralPath $composeFile)) { throw "Runtime definition not found: $composeFile" }
if (!(Test-Path -LiteralPath $envFile)) { throw "Workspace configuration not found: $envFile" }
if (!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker was not found.' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker is installed but is not running.' }
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose v2 is required.' }

$projectNameLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^COMPOSE_PROJECT_NAME=' } | Select-Object -First 1
if (!$projectNameLine) { throw 'COMPOSE_PROJECT_NAME is missing from the workspace .env file.' }
$projectName = ($projectNameLine -split '=', 2)[1]
if ($projectName -notmatch '^[a-z0-9][a-z0-9_.-]*$') { throw 'The stored Compose project name is invalid.' }

$env:APP_DATA_ROOT = [IO.Path]::GetFullPath($workspaceRoot).Replace('\','/')
$arguments = @('compose','--project-name',$projectName,'--env-file',$envFile,'--file',$composeFile)

switch ($Command) {
    'start' {
        & docker @arguments pull
        if ($LASTEXITCODE -ne 0) { throw 'Unable to pull the release images.' }
        & docker @arguments up --detach --pull never --no-build --wait
        if ($LASTEXITCODE -ne 0) { throw 'The application did not become healthy.' }
    }
    'stop' { & docker @arguments stop }
    'restart' {
        & docker @arguments stop
        if ($LASTEXITCODE -eq 0) { & docker @arguments up --detach --pull never --no-build --wait }
    }
    'status' { & docker @arguments ps }
    'logs' { & docker @arguments logs --tail 200 }
    'pull' { & docker @arguments pull }
}

if ($LASTEXITCODE -ne 0) { throw "The '$Command' command failed." }
