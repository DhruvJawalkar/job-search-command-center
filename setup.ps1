[CmdletBinding()]
param(
    [string]$WorkspaceFolder,
    [switch]$Demo,
    [switch]$Empty,
    [ValidateRange(1,65535)][int]$PortalPort = 3000,
    [ValidateRange(1,65535)][int]$ApiPort = 8080,
    [ValidatePattern('^v[0-9]+\.[0-9]+\.[0-9]+$')][string]$ReleaseTag = 'v1.0.0'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Demo -and $Empty) { throw 'Choose either -Demo or -Empty, not both.' }
if ($PortalPort -eq $ApiPort) { throw 'Portal and API ports must be different.' }

$projectRoot = Split-Path $MyInvocation.MyCommand.Path -Parent
$defaultWorkspace = Join-Path $projectRoot 'workspace'
if ([string]::IsNullOrWhiteSpace($WorkspaceFolder)) {
    $answer = Read-Host "Workspace folder [$defaultWorkspace]"
    $WorkspaceFolder = if ([string]::IsNullOrWhiteSpace($answer)) { $defaultWorkspace } else { $answer }
}
$WorkspaceFolder = [IO.Path]::GetFullPath($WorkspaceFolder)

$repository = 'DhruvJawalkar/job-search-command-center'
$asset = "job-search-command-center-$ReleaseTag-standalone.zip"
$checksums = "job-search-command-center-$ReleaseTag-standalone.SHA256SUMS"
$releaseBase = "https://github.com/$repository/releases/download/$ReleaseTag"
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("jscc-bootstrap-" + [Guid]::NewGuid().ToString('N'))

Write-Host ''
Write-Host 'Job Search Command Center — Codex-assisted runtime setup' -ForegroundColor Cyan
Write-Host "Accepted release: $ReleaseTag"
Write-Host "Local workspace: $WorkspaceFolder"
Write-Host 'The bootstrap will download the accepted source-free bundle, verify its checksum, pull immutable release images, and start the local app.'
Write-Host 'Expected time: 3–8 minutes on the first run.'
Write-Host ''

New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
try {
    $archivePath = Join-Path $temporaryRoot $asset
    $checksumPath = Join-Path $temporaryRoot $checksums
    try {
        Invoke-WebRequest -Uri "$releaseBase/$asset" -OutFile $archivePath -UseBasicParsing
        Invoke-WebRequest -Uri "$releaseBase/$checksums" -OutFile $checksumPath -UseBasicParsing
    } catch {
        throw "The accepted $ReleaseTag release bundle is not available from GitHub. No application was installed. Confirm the release exists and try again. $($_.Exception.Message)"
    }

    $pattern = '(?im)^([0-9a-f]{64})\s+\*?' + [Regex]::Escape($asset) + '\s*$'
    $match = [Regex]::Match((Get-Content -LiteralPath $checksumPath -Raw), $pattern)
    if (!$match.Success) { throw "The published checksum file does not contain $asset." }
    $expectedHash = $match.Groups[1].Value.ToLowerInvariant()
    $actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) { throw 'Release archive checksum mismatch. The archive was not executed.' }

    $bundleRoot = Join-Path $temporaryRoot 'bundle'
    Expand-Archive -LiteralPath $archivePath -DestinationPath $bundleRoot -Force
    $installer = Join-Path $bundleRoot 'setup.ps1'
    if (!(Test-Path -LiteralPath $installer)) { throw 'The verified release archive does not contain setup.ps1.' }

    $arguments = @{
        WorkspaceFolder = $WorkspaceFolder
        PortalPort = $PortalPort
        ApiPort = $ApiPort
        DistributionChannel = 'codex'
    }
    if ($Demo) { $arguments.Demo = $true }
    if ($Empty) { $arguments.Empty = $true }
    & $installer @arguments
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) { Remove-Item -LiteralPath $temporaryRoot -Recurse -Force }
}
