# Standalone Docker installation

This route runs Job Search Command Center from an accepted, digest-pinned
release bundle without cloning the source repository. It requires Docker with
Compose v2, but not Java, Maven, Node.js, pnpm, PostgreSQL, Git, or Codex.

> **Release status:** `v1.0.0` is the planned first release. The archives,
> checksums, component tags, immutable digests, signatures, attestations,
> SBOMs, and scan reports become supported release assets only after final
> acceptance. If the GitHub release does not contain the files named below and
> identify them as accepted, this page is a preview rather than an active
> installation path.

## Requirements

- Docker Desktop on Windows or macOS, or Docker Engine on Linux.
- Docker Compose v2.
- Approximately 3 GB of free disk space for the first pull and local data.
- A private local folder for the workspace selected during setup.

The project images support `linux/amd64` and `linux/arm64`. Docker Desktop can
run these Linux containers on supported Windows and macOS hosts.

## Download the accepted release

After acceptance, open the
[`v1.0.0` GitHub release](https://github.com/DhruvJawalkar/job-search-command-center/releases/tag/v1.0.0).
It will provide:

```text
job-search-command-center-v1.0.0-standalone.zip
job-search-command-center-v1.0.0-standalone.tar.gz
job-search-command-center-v1.0.0-standalone.SHA256SUMS
```

Windows users need the ZIP and checksum file. macOS and Linux users need the
tar archive and checksum file. If the matching archive or checksum file is
absent, the standalone release is not ready. Do not reconstruct the stack from
individual Docker Hub tags.

## Windows setup

Place the ZIP and checksum file in the same directory, then use PowerShell:

```powershell
$ArchiveName = 'job-search-command-center-v1.0.0-standalone.zip'
$ChecksumName = 'job-search-command-center-v1.0.0-standalone.SHA256SUMS'
$ChecksumLine = Get-Content -LiteralPath $ChecksumName |
  Where-Object { $_.TrimEnd().EndsWith($ArchiveName) } |
  Select-Object -First 1
if (!$ChecksumLine) { throw "No checksum was published for $ArchiveName." }

$Expected = ($ChecksumLine -split '\s+', 2)[0].ToLowerInvariant()
$Actual = (Get-FileHash -LiteralPath $ArchiveName -Algorithm SHA256).Hash.ToLowerInvariant()
if ($Actual -ne $Expected) { throw 'Standalone ZIP checksum mismatch.' }

$Destination = Join-Path (Get-Location) 'job-search-command-center-v1.0.0-standalone'
Expand-Archive -LiteralPath $ArchiveName -DestinationPath $Destination -Force
Set-Location $Destination
./setup.ps1
```

The installer explains its actions, proposes
`%LOCALAPPDATA%\JobSearchCommandCenter`, asks whether to start empty or with
synthetic demo data, creates the workspace and a generated database password,
installs the runtime control files, pulls the accepted images, and waits for a
healthy application.

For non-interactive setup or custom ports:

```powershell
./setup.ps1 -WorkspaceFolder 'D:\Private\JobSearchCommandCenter' -Empty
./setup.ps1 -WorkspaceFolder 'D:\Private\JobSearchCommandCenterDemo' -Demo -PortalPort 3300 -ApiPort 8800
```

## macOS setup

Place the tar archive and checksum file in the same directory, then use a
terminal:

```bash
archive=job-search-command-center-v1.0.0-standalone.tar.gz
checksums=job-search-command-center-v1.0.0-standalone.SHA256SUMS
grep "  $archive\$" "$checksums" | shasum -a 256 --check
mkdir -p job-search-command-center-v1.0.0-standalone
tar -xzf "$archive" -C job-search-command-center-v1.0.0-standalone
cd job-search-command-center-v1.0.0-standalone
chmod +x setup.sh
./setup.sh
```

The installer proposes
`~/Library/Application Support/JobSearchCommandCenter`, asks for empty or
synthetic-demo mode, creates the workspace and secret, installs its control
files, pulls the accepted images, and waits for health.

For non-interactive setup or custom ports:

```bash
./setup.sh --folder "$HOME/JobSearchCommandCenter" --empty
./setup.sh --folder "$HOME/JobSearchCommandCenterDemo" --demo --portal-port 3300 --api-port 8800
```

## Linux setup

Place the tar archive and checksum file in the same directory, then run:

```bash
archive=job-search-command-center-v1.0.0-standalone.tar.gz
checksums=job-search-command-center-v1.0.0-standalone.SHA256SUMS
grep "  $archive\$" "$checksums" | sha256sum --check
mkdir -p job-search-command-center-v1.0.0-standalone
tar -xzf "$archive" -C job-search-command-center-v1.0.0-standalone
cd job-search-command-center-v1.0.0-standalone
chmod +x setup.sh
./setup.sh
```

The default workspace is
`${XDG_DATA_HOME:-$HOME/.local/share}/job-search-command-center`. The same
non-interactive `--folder`, `--empty`, `--demo`, `--portal-port`, and
`--api-port` options shown for macOS are supported.

## What setup installs

The extracted download can be removed after setup. Durable runtime controls
and user data live in the selected workspace:

```text
<workspace>/
  .env                         generated secret and local settings
  README.md                    this standalone guide
  .jscc/
    compose.yaml               digest-pinned local-only runtime
    compose.connected.yaml     optional reviewed connected overlay
    gateway/nginx.conf         fixed gateway routes
    release-manifest.json      source and image-digest identity
    SHA256SUMS                 release-bundle file checksums
    VERSION                    installed release version
    jscc.ps1                   Windows lifecycle helper
    jscc.sh                    macOS/Linux lifecycle helper
    LICENSE                    non-commercial source license
    NOTICE                     required creator notice
    TRADEMARKS.md              project-identity terms
    COMMERCIAL-LICENSE.md      commercial-license guidance
  postgres-data/               PostgreSQL database files
  application-resumes/         resumes and preserved job descriptions
  notes/                       saved note snapshots
  daily-high-fit-job-roles/    workbook and action-file imports
  linkedin-data-import/        optional Connections.csv import
  preparation-workspace/       private preparation material
  company-targets/             optional company-segment workbook
  backups/                     private local backup copies
```

Rerunning setup against the same workspace preserves its database password and
data. It also refuses to convert an existing empty workspace into demo mode,
or a demo workspace into personal mode. Use separate workspace folders for
those experiences.

Keep the workspace out of Git, shared directories, and unencrypted cloud
sync. Its `.env`, database, resumes, exports, notes, and backups may contain
credentials or sensitive personal and third-party data.

## Open the application

Setup prints the selected URLs. With the default ports:

- Portal: `http://127.0.0.1:3000`
- API health: `http://127.0.0.1:8080/actuator/health`

On first use, review the application privacy choice before entering real
personal or third-party data.

## Lifecycle commands

The helpers always use the installed, digest-pinned Compose definition under
`<workspace>/.jscc`. They never build application source.

Windows PowerShell, using the default workspace:

```powershell
$Control = Join-Path $env:LOCALAPPDATA 'JobSearchCommandCenter\.jscc\jscc.ps1'
& $Control status
& $Control logs
& $Control stop
& $Control start
& $Control restart
& $Control pull
```

macOS, using the default workspace:

```bash
control="$HOME/Library/Application Support/JobSearchCommandCenter/.jscc/jscc.sh"
"$control" status
"$control" logs
"$control" stop
"$control" start
"$control" restart
"$control" pull
```

Linux, using the default workspace:

```bash
control="${XDG_DATA_HOME:-$HOME/.local/share}/job-search-command-center/.jscc/jscc.sh"
"$control" status
"$control" logs
"$control" stop
"$control" start
"$control" restart
"$control" pull
```

`start` pulls the exact configured digests and waits for health. `pull`
re-downloads those same configured digests; it does not silently select a
newer release. Use the lifecycle helper matching the host rather than manually
changing the installed Compose or `.env` files.

## Backup, update, and rollback

Before an upgrade or important local change:

1. Stop the application with the installed lifecycle helper.
2. Confirm its `status` shows the services stopped.
3. Make a private, access-controlled copy of the complete workspace, including
   `.env`, `.jscc/`, and `postgres-data/`.
4. Restart only after that copy finishes.

A stopped, complete workspace copy preserves database and file artifacts
together. It is unencrypted unless the destination provides encryption. Test
important restores in a separate workspace; never restore over the live
workspace merely to diagnose a problem.

To install a later accepted release, verify and extract its new archive, then
run its setup installer against the existing workspace and the same data mode.
The installer replaces the versioned control files and starts the newly pinned
images without deleting user data. Keep the previous archive and pre-upgrade
backup until the update has been reviewed.

Application-image rollback is safe only when the older application supports
the database schema already present. Flyway migrations can make an in-place
downgrade unsafe. When compatibility is not explicitly documented, restore
the pre-upgrade backup into a separate workspace instead.

## Advanced runtime details

Job Search Command Center is a multi-container application: PostgreSQL, API,
portal, and a fixed-route Nginx gateway, with an optional connected-mode egress
broker. Pulling or running one Docker Hub component alone is not a supported
installation. The accepted bundle preserves compatible image digests,
networks, mounts, gateway routes, and health checks together.

The default stack gives the API and portal no ordinary outbound route. Codex
use beside the local app does not require the connected overlay. Optional
application-owned OpenAI assistance and live public-page retrieval require the
reviewed connected runtime, a fresh broker token, application policy opt-in,
and confirmation of each previewed transmission. Follow the
[connected-runtime guide](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/docs/CONNECTED_RUNTIME.md)
without weakening the default network boundary.

For exact digest, signature, provenance, SBOM, scan-report, and release-manifest
checks, see
[container release and verification](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/docs/CONTAINER_RELEASE.md).
Checksums detect changed downloads; the signed provenance and attestations bind
the accepted artifacts to their source and release identity. None of this is a
claim that software is vulnerability-free.

## Security, privacy, and licensing

V1 is an unauthenticated, trusted-single-user application. Only the fixed-route
gateway is published, on loopback. Do not expose it through a LAN bind, tunnel,
reverse proxy, port-forward, shared host, or public deployment. Read the
[security policy](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/SECURITY.md),
[privacy notice](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/PRIVACY.md),
and
[threat model](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/docs/THREAT_MODEL.md)
before adding real data.

Job Search Command Center is source-available for non-commercial use under the
[PolyForm Noncommercial License 1.0.0](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/LICENSE).
Pulling an image does not grant commercial-use rights. Preserve the
[required notice](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/NOTICE),
follow the
[branding terms](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/TRADEMARKS.md),
and review
[commercial-license guidance](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/COMMERCIAL-LICENSE.md)
before redistribution or commercial use.
