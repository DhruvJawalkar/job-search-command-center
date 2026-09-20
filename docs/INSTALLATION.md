# Install the Codex-assisted runtime

This guide applies to the source-free default `main` branch. It downloads the accepted release bundle and runs published, digest-pinned containers. For a local source build, switch to the [`source` branch](https://github.com/DhruvJawalkar/job-search-command-center/tree/source) and follow its README.

## Requirements

- Windows 10/11 with Docker Desktop; macOS with Docker Desktop; or Linux with Docker Engine.
- Docker Compose v2 (`docker compose version`).
- Git for cloning this Codex context repository.
- Approximately 3 GB of free disk space for images and initial local data.

Java, Maven, Node.js, pnpm, and a host PostgreSQL installation are not required.

## Clone and connect Codex

```bash
git clone --depth 1 --single-branch --branch main https://github.com/DhruvJawalkar/job-search-command-center.git
cd job-search-command-center
```

Create a local Codex project named **Job Search Command Center**, add this repository folder as its primary folder, and start a chat inside that project. The app can run without Codex, but the project gives Codex access to the checked-in workflow catalog and operating guidance.

## Guided setup

Windows PowerShell:

```powershell
./setup.ps1
```

macOS or Linux:

```bash
./setup.sh
```

The bootstrap:

1. proposes `<clone>/workspace`, while allowing another private folder;
2. asks for empty or synthetic-demo mode;
3. downloads the accepted `v1.0.0` archive and checksum from the GitHub release;
4. refuses to execute the archive if its SHA-256 does not match;
5. invokes the verified published installer in the `codex` distribution mode;
6. creates a generated database password and local folder contract;
7. installs digest-pinned runtime controls under `<workspace>/.jscc`;
8. pulls the accepted images without any source build; and
9. waits for the loopback-only application to become healthy.

Until the accepted release exists, the bootstrap exits with a release-not-available message and does not fall back to mutable tags.

Non-interactive examples:

```powershell
./setup.ps1 -WorkspaceFolder 'D:\Private\JobSearchCommandCenter' -Empty
./setup.ps1 -WorkspaceFolder 'D:\Private\JobSearchCommandCenterDemo' -Demo -PortalPort 3300 -ApiPort 8800
```

```bash
./setup.sh --folder "$HOME/JobSearchCommandCenter" --empty
./setup.sh --folder "$HOME/JobSearchCommandCenterDemo" --demo --portal-port 3300 --api-port 8800
```

Use separate workspaces for demo and personal data. Rerunning setup preserves the existing database and generated secret and refuses to convert a workspace between empty and demo modes.

## First use

Open `http://127.0.0.1:3000`. A new personal installation first presents **Privacy & intelligence**, where you choose Stateless, Session-only, or Time-bound application-owned assistance context. This is separate from records you deliberately save and from Codex task/account retention.

Return to the Codex project chat, open a Browser panel beside it, enter the same local URL, and ask **“Help me on this page.”**

## Workspace and lifecycle

The clone contains guidance; the selected workspace contains the runtime and private data. Its `.jscc` folder includes the digest-pinned Compose definition, gateway routes, release manifest, checksums, licenses, and lifecycle helper.

Windows:

```powershell
./workspace/.jscc/jscc.ps1 status
./workspace/.jscc/jscc.ps1 logs
./workspace/.jscc/jscc.ps1 stop
./workspace/.jscc/jscc.ps1 start
./workspace/.jscc/jscc.ps1 restart
./workspace/.jscc/jscc.ps1 pull
```

macOS/Linux:

```bash
./workspace/.jscc/jscc.sh status
./workspace/.jscc/jscc.sh logs
./workspace/.jscc/jscc.sh stop
./workspace/.jscc/jscc.sh start
./workspace/.jscc/jscc.sh restart
./workspace/.jscc/jscc.sh pull
```

`pull` retrieves the same configured digests; it is not an upgrade. Before installing a later release, stop the application and make a protected copy of the complete workspace, including `.env`, `.jscc`, and `postgres-data`.

## Troubleshooting

- Confirm Docker is running and `docker compose version` succeeds.
- Confirm ports 3000 and 8080 are free, or select alternate ports.
- On Linux, ensure the current user can access the Docker socket without switching the workspace owner to root.
- On Docker Desktop, ensure the selected workspace folder is available to Docker.
- If the release archive returns 404, the accepted release has not been published; do not assemble the stack from component tags.

Use the installed lifecycle helper’s `status` and `logs` commands for runtime diagnostics. The detailed no-clone path is documented in [Standalone Docker installation](STANDALONE_DOCKER.md).
