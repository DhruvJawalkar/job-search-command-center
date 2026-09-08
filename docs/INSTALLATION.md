# Installation

## Requirements

- Docker Desktop on Windows or macOS, or Docker Engine on Linux.
- Docker Compose v2.
- About 3 GB of free disk space for the first build and local containers.

No system JDK, Maven, Node.js, pnpm, or PostgreSQL installation is required for the Docker-first path.

## Guided setup

Windows PowerShell:

```powershell
./setup.ps1
```

macOS or Linux:

```bash
chmod +x setup.sh
./setup.sh
```

The script previews its actions, proposes the checked-out repository folder as the local workspace, and lets you start empty or with synthetic demo content. Press Enter to accept that folder or enter another path. Generated configuration and data folders in the repository are Git-ignored. The first build normally takes 5–10 minutes depending on network and machine speed.

On Linux, the script records your numeric user and group IDs so the API can write only to the selected bind-mounted workspace without running as root. The Windows helper records a Docker Desktop-compatible unprivileged identity and performs the same setup contract as the shell script.

Non-interactive examples:

```powershell
./setup.ps1 -ProjectFolder "$HOME/JobSearchCommandCenter" -Empty
./setup.ps1 -ProjectFolder "$HOME/JobSearchCommandCenterDemo" -Demo
```

```bash
./setup.sh --folder "$HOME/JobSearchCommandCenter" --empty
./setup.sh --folder "$HOME/JobSearchCommandCenterDemo" --demo
```

Open `http://127.0.0.1:3000` after the containers report healthy.

Use a separate folder for demo and personal installations. Rerunning setup against an existing folder preserves its database and `.env`; it does not erase or convert that workspace.

## Start and stop later

From the source repository, replace the example path with the selected local workspace:

```bash
docker compose --project-name job-search-command-center --env-file /path/to/local/workspace/.env --file compose.yaml stop
docker compose --project-name job-search-command-center --env-file /path/to/local/workspace/.env --file compose.yaml up --detach --wait
```

Run the setup script again after updating the source checkout; it rebuilds the images and preserves the selected bind-mounted data.

## Privacy boundary

The app is unauthenticated and intended for one trusted user on one local machine. Docker publishes the portal, API, and database only on `127.0.0.1`. Do not change those bindings unless authentication and authorization are added and independently reviewed.

## Troubleshooting

Check status:

```bash
docker compose --project-name job-search-command-center --env-file /path/to/local/workspace/.env --file compose.yaml ps
```

View logs:

```bash
docker compose --project-name job-search-command-center --env-file /path/to/local/workspace/.env --file compose.yaml logs --tail 200
```

Common first-run problems are Docker not running, ports 3000/8080/5432 already being used, or Docker Desktop lacking access to the selected local folder.
