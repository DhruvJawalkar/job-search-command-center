Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-DockerChecked {
    param([string[]]$Arguments)
    $result = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Docker operation failed: $($result -join [Environment]::NewLine)" }
    return ($result -join "`n")
}

function Write-RecoveryJson($Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 40), [Text.UTF8Encoding]::new($false))
}

function Resolve-ChildPath($Root, $Relative) {
    if ([IO.Path]::IsPathRooted($Relative)) { throw 'Expected a relative recovery path.' }
    $base = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $path = [IO.Path]::GetFullPath((Join-Path $base $Relative))
    if (!$path.StartsWith($base + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Recovery path escapes its root.'
    }
    return $path
}

function Test-RecoveryPort($Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try { $client.Connect('127.0.0.1', $Port); return $true }
    catch { return $false }
    finally { $client.Dispose() }
}

function Get-DatabaseFingerprint($Container, $User, $Database) {
    # Exact logical row equality, including empty tables and sequence positions.
    # MD5 is used for comparison only; the backup files themselves use SHA-256.
    $sql = @'
SET timezone = 'UTC'; SET datestyle = 'ISO, YMD';
CREATE TEMP TABLE recovery_fingerprint (name text, rows bigint, digest text);
DO $$ DECLARE r record; n bigint; h text; BEGIN
 FOR r IN SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename LOOP
  EXECUTE format('SELECT count(*), md5(coalesce(string_agg(j, chr(10) ORDER BY j COLLATE "C"), '''')) FROM (SELECT row_to_json(t)::text j FROM public.%I t) x',r.tablename) INTO n,h;
  INSERT INTO recovery_fingerprint VALUES(r.tablename,n,h);
 END LOOP;
 FOR r IN SELECT sequencename FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename LOOP
  EXECUTE format('SELECT last_value, is_called::text FROM public.%I',r.sequencename) INTO n,h;
  INSERT INTO recovery_fingerprint VALUES('sequence:'||r.sequencename,n,h);
 END LOOP;
END $$;
SELECT coalesce(json_agg(x ORDER BY x.name),'[]') FROM recovery_fingerprint x;
'@
    $json = Invoke-DockerChecked -Arguments @('exec', $Container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', $User, '-d', $Database, '-c', $sql)
    return $json | ConvertFrom-Json
}

function Assert-FingerprintEqual($Expected, $Actual) {
    $a = ConvertTo-Json -InputObject @($Expected) -Compress -Depth 10
    $b = ConvertTo-Json -InputObject @($Actual) -Compress -Depth 10
    if ($a -cne $b) { throw 'Database row counts/content or sequence positions differ from the snapshot.' }
}

function Assert-BackupIntegrity($Folder) {
    $manifestPath = Join-Path $Folder 'manifest.json'
    $expected = ([IO.File]::ReadAllText((Join-Path $Folder 'manifest.sha256'))).Trim()
    if ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash -ne $expected) {
        throw 'Backup manifest checksum mismatch.'
    }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.formatVersion -ne 1 -or $manifest.status -ne 'COMPLETE') { throw 'Incomplete or unsupported backup.' }
    foreach ($entry in $manifest.files) {
        $path = Resolve-ChildPath $Folder $entry.path
        if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing backup file: $($entry.path)" }
        if ((Get-Item -LiteralPath $path).Length -ne $entry.bytes -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256) {
            throw "Backup checksum mismatch: $($entry.path)"
        }
    }
    return $manifest
}

function Assert-ApplicationArtifacts($Root, $Artifacts) {
    foreach ($artifact in $Artifacts) {
        $path = Resolve-ChildPath $Root $artifact.path
        if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw 'A database-linked application artifact is missing.' }
        if ((Get-Item -LiteralPath $path).Length -ne $artifact.bytes -or
            (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $artifact.sha256) {
            throw 'A database-linked application artifact has an incorrect size or SHA-256.'
        }
    }
}
