[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$workspace = Split-Path $PSScriptRoot -Parent
$releaseRoot = Join-Path $workspace '.github/release/standalone'
$composeTemplate = Get-Content -LiteralPath (Join-Path $workspace '.github/release/compose.release.yaml.tmpl') -Raw

function Assert-True([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw "Standalone release check failed: $Message" }
}

foreach ($file in @('setup.ps1','setup.sh','jscc.ps1','jscc.sh')) {
    Assert-True (Test-Path -LiteralPath (Join-Path $releaseRoot $file) -PathType Leaf) "missing $file"
}

$setupPowerShell = Get-Content -LiteralPath (Join-Path $releaseRoot 'setup.ps1') -Raw
$controlPowerShell = Get-Content -LiteralPath (Join-Path $releaseRoot 'jscc.ps1') -Raw
$setupShell = Get-Content -LiteralPath (Join-Path $releaseRoot 'setup.sh') -Raw
$controlShell = Get-Content -LiteralPath (Join-Path $releaseRoot 'jscc.sh') -Raw
[void][scriptblock]::Create($setupPowerShell)
[void][scriptblock]::Create($controlPowerShell)

Assert-True ($composeTemplate -notmatch '(?m)^\s+build:') 'release Compose contains a source build context'
Assert-True ($composeTemplate -match 'APP_DISTRIBUTION_CHANNEL:\s*\$\{APP_DISTRIBUTION_CHANNEL:-standalone\}') 'standalone distribution channel is not the default'
Assert-True ($composeTemplate -match 'APP_CODEX_GUIDANCE_ENABLED:\s*\$\{APP_CODEX_GUIDANCE_ENABLED:-false\}') 'Codex guidance is not disabled by default'
foreach ($content in @($setupPowerShell,$controlPowerShell,$setupShell,$controlShell)) {
    Assert-True ($content -notmatch '(?m)(?:--build\b(?!.*--no-build)|docker\s+build)') 'a standalone helper can build source'
}
Assert-True ($controlPowerShell -match '--pull never --no-build --wait') 'PowerShell start is not a pull-only launch'
Assert-True ($controlShell -match '--pull never --no-build --wait') 'POSIX start is not a pull-only launch'
Assert-True ($setupPowerShell -match "APP_CODEX_GUIDANCE_ENABLED=.*'false'") 'PowerShell setup does not select standalone UI behavior'
Assert-True ($setupShell -match 'APP_CODEX_GUIDANCE_ENABLED=false') 'POSIX setup does not select standalone UI behavior'
Assert-True ($setupPowerShell -match "ValidateSet\('standalone','codex'\)") 'PowerShell setup cannot select the Codex-assisted distribution channel'
Assert-True ($setupShell -match '--distribution-channel') 'POSIX setup cannot select the Codex-assisted distribution channel'
Assert-True ($setupPowerShell -match "DistributionChannel -eq 'codex'") 'PowerShell setup does not enable Codex guidance for the Codex channel'
Assert-True ($setupShell -match 'DISTRIBUTION_CHANNEL.*codex') 'POSIX setup does not enable Codex guidance for the Codex channel'
Assert-True ($setupShell -match 'PORTAL_PORT.*-le 65535') 'POSIX setup does not constrain the portal port range'
Assert-True ($setupShell -match 'API_PORT.*-le 65535') 'POSIX setup does not constrain the API port range'

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("jscc-standalone-test-" + [Guid]::NewGuid().ToString('N'))
$bundle = Join-Path $tempRoot 'bundle with spaces'
$userWorkspace = Join-Path $tempRoot 'workspace with spaces'
$bin = Join-Path $tempRoot 'bin'
try {
    New-Item -ItemType Directory -Path (Join-Path $bundle 'gateway'),$bin -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $releaseRoot 'setup.ps1') -Destination $bundle
    Copy-Item -LiteralPath (Join-Path $releaseRoot 'setup.sh') -Destination $bundle
    Copy-Item -LiteralPath (Join-Path $releaseRoot 'jscc.ps1') -Destination $bundle
    Copy-Item -LiteralPath (Join-Path $releaseRoot 'jscc.sh') -Destination $bundle
    Set-Content -LiteralPath (Join-Path $bundle 'compose.yaml') -Value 'services: {}' -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $bundle 'compose.connected.yaml') -Value 'services: {}' -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $bundle 'gateway/nginx.conf') -Value 'events {}' -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $bundle 'release-manifest.json') -Value '{}' -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $bundle 'VERSION') -Value 'v1.0.0' -Encoding utf8NoBOM
    Set-Content -LiteralPath (Join-Path $bundle 'SHA256SUMS') -Value '' -Encoding utf8NoBOM
    foreach ($file in @('LICENSE','NOTICE','TRADEMARKS.md','COMMERCIAL-LICENSE.md','README.md')) {
        Set-Content -LiteralPath (Join-Path $bundle $file) -Value $file -Encoding utf8NoBOM
    }
    $dockerLog = Join-Path $tempRoot 'docker.log'
    @(
        '@echo off'
        "echo %*>>`"$dockerLog`""
        'exit /b 0'
    ) | Set-Content -LiteralPath (Join-Path $bin 'docker.cmd') -Encoding ascii
    $originalPath = $env:PATH
    $env:PATH = "$bin;$originalPath"
    try {
        & (Join-Path $bundle 'setup.ps1') -WorkspaceFolder $userWorkspace -Empty
        Assert-True ($LASTEXITCODE -eq 0) 'PowerShell installer failed against the Docker stub'
    } finally {
        $env:PATH = $originalPath
    }

    foreach ($folder in @('.jscc','postgres-data','application-resumes','notes','daily-high-fit-job-roles','linkedin-data-import','preparation-workspace','company-targets','backups')) {
        Assert-True (Test-Path -LiteralPath (Join-Path $userWorkspace $folder)) "installer did not create $folder"
    }
    $envText = Get-Content -LiteralPath (Join-Path $userWorkspace '.env') -Raw
    Assert-True ($envText -match '(?m)^APP_DEMO_MODE=false\r?$') 'empty-mode selection was not persisted'
    Assert-True ($envText -match '(?m)^APP_DISTRIBUTION_CHANNEL=standalone\r?$') 'standalone channel was not persisted'
    Assert-True ($envText -match '(?m)^COMPOSE_PROJECT_NAME=job-search-command-center-[0-9a-f]{8}\r?$') 'workspace-specific Compose project name was not generated'
    $commands = Get-Content -LiteralPath $dockerLog -Raw
    Assert-True ($commands -match '(?m)^compose .* pull\r?$') 'installer did not pull release images'
    Assert-True ($commands -match 'up --detach --pull never --no-build --wait') 'installer did not use the no-build launch contract'
    Assert-True ($commands -notmatch '(?m)(^|\s)--build(\s|$)') 'installer passed a source-build flag'
} finally {
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}

Write-Output 'Standalone release checks passed.'
