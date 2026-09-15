. "$PSScriptRoot/Recovery.Common.ps1"
$root = Join-Path (Split-Path $PSScriptRoot -Parent) ('tmp/recovery-safety-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
$checks = 0
function Assert-Rejected([scriptblock]$Action, [string]$ExpectedMessage) {
    $rejected = $false
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message -notlike "*$ExpectedMessage*") { throw }
        $rejected = $true
    }
    if (!$rejected) { throw "Safety check did not reject: $ExpectedMessage" }
    $script:checks++
}
Assert-Rejected { Resolve-ChildPath $root '../outside.txt' } 'escapes its root'
Assert-Rejected { Resolve-ChildPath $root $root } 'relative recovery path'
$payload = Join-Path $root 'payload.txt'
[IO.File]::WriteAllText($payload, 'synthetic recovery fixture')
$entry = [ordered]@{path='payload.txt'; bytes=(Get-Item $payload).Length; sha256=(Get-FileHash $payload).Hash}
$manifest = [ordered]@{formatVersion=1; status='COMPLETE'; files=@($entry)}
function Save-FixtureManifest {
    Write-RecoveryJson (Join-Path $root 'manifest.json') $manifest
    [IO.File]::WriteAllText((Join-Path $root 'manifest.sha256'),(Get-FileHash (Join-Path $root 'manifest.json')).Hash)
}
Save-FixtureManifest
Assert-BackupIntegrity $root | Out-Null
$checks++
[IO.File]::WriteAllText($payload, 'corrupted fixture')
Assert-Rejected { Assert-BackupIntegrity $root } 'checksum mismatch'
$manifest.status='INCOMPLETE'; Save-FixtureManifest
Assert-Rejected { Assert-BackupIntegrity $root } 'Incomplete or unsupported'
$manifest.status='COMPLETE'; $manifest.files[0].path='../escape.txt'; Save-FixtureManifest
Assert-Rejected { Assert-BackupIntegrity $root } 'escapes its root'
[IO.File]::AppendAllText((Join-Path $root 'manifest.json'), ' ')
Assert-Rejected { Assert-BackupIntegrity $root } 'manifest checksum mismatch'
Assert-Rejected { Assert-FingerprintEqual @([ordered]@{name='test';rows=1;digest='a'}) @([ordered]@{name='test';rows=2;digest='b'}) } 'differ from the snapshot'
Write-Output "$checks recovery safety checks passed. Synthetic fixtures retained under ignored tmp/."
