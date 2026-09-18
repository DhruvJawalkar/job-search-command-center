# Installation

## Requirements

- Docker Desktop on Windows or macOS, or Docker Engine on Linux.
- Docker Compose v2.
- About 3 GB of free disk space for the first build and local containers.

No system JDK, Maven, Node.js, pnpm, or PostgreSQL installation is required for the Docker-first path.

## Connect the clone to Codex

For the Codex-assisted experience:

1. Open **Projects** in the Codex desktop app.
2. Create a local project named **Job Search Command Center**.
3. Add the cloned `job-search-command-center` repository folder and make it the primary folder.
4. Start a new chat inside that project.
5. After installation, return to that chat and open a **Browser** tab in the right-side panel.
6. Enter `http://127.0.0.1:3000`, then ask **“Help me on this page.”** in the project chat.

The local project lets Codex read the repository documentation and assist with onboarding and later workflows. It is recommended, but the Docker application can run without it.

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

The script previews its actions, proposes the Git-ignored `workspace` folder inside the checked-out repository for user-specific data, and lets you start empty or with synthetic demo content. Press Enter to accept that folder or enter another path. The first build normally takes 5–10 minutes depending on network and machine speed.

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

## First-run privacy choice

On a new personal installation, the portal presents **Privacy & intelligence** before profile onboarding. Choose how the application should treat assistant-derived context:

- **Stateless** — intended for no durable context between assisted interactions;
- **Session only** — intended for temporary context during the active session; or
- **Time-bound** — cleanup eligibility after 7, 30, or 90 days.

These choices are separate from openings, applications, contacts, preparation work, and other records you deliberately save. Stateless keeps assistant output response-only until you deliberately save reviewed fields; Session only uses bounded backend memory; Time-bound persists derived context for 7, 30, or 90 days and runs cleanup at startup and when daily due. Connected features can be permitted in any mode, but notice acceptance, the policy opt-in, the separate connected runtime, and one-time confirmation of each transmission are all required.

Review or change the choice later in Profile → Data & privacy. That panel can preview and manually clean eligible assistance runs and decisions. It does not delete saved records, workspace files, host-browser storage, Codex tasks, or provider-side copies. See the [Privacy notice](../PRIVACY.md) before entering real personal or third-party data.

## Start and stop later

From the source repository, replace the example path with the selected local workspace:

```bash
docker compose --project-name job-search-command-center --env-file /path/to/local/workspace/.env --file compose.yaml stop
docker compose --project-name job-search-command-center --env-file /path/to/local/workspace/.env --file compose.yaml up --detach --wait
```

Run the setup script again after updating the source checkout; it rebuilds the images and preserves the selected bind-mounted data.

## Privacy boundary

The app is unauthenticated and intended for one trusted user on one local machine. Docker publishes only a fixed-route gateway, bound to `127.0.0.1:3000` and `127.0.0.1:8080`. The portal, API, and PostgreSQL have no direct host ports, use only the `local_only` internal network, and have no ordinary outbound route. The gateway proxies only to the portal and API and has no workspace or secret mounts.

The gateway also joins an ordinary ingress network so Docker Desktop can publish the loopback ports. That attachment has ordinary outbound reachability; gateway hardening and fixed proxy routes limit its role but do not prove universal non-exfiltration. The host browser, Codex, Docker Desktop or Engine, and operating-system services are separate boundaries as well.

This is the default local-only V1 runtime. Do not change the loopback bindings, internal network, or fixed gateway routes. Selecting **Allow connected assistance** records a policy preference but does not create an outbound route by itself. The separate reviewed profile and its exact start/stop commands are documented in [Connected runtime](CONNECTED_RUNTIME.md); editing Compose manually is not a supported connected mode.

To verify the rendered configuration without starting or changing a stack, run `./scripts/Test-LocalOnlyRuntime.ps1` in PowerShell. Runtime verification is an explicit separate step against an isolated running stack; see [Development](DEVELOPMENT.md). Evidence from one host or Docker configuration does not establish the same result on every platform.

## Troubleshooting

Check status:

```bash
docker compose --project-name job-search-command-center --env-file /path/to/local/workspace/.env --file compose.yaml ps
```

View logs:

```bash
docker compose --project-name job-search-command-center --env-file /path/to/local/workspace/.env --file compose.yaml logs --tail 200
```

Common first-run problems are Docker not running, ports 3000 or 8080 already being used, or Docker Desktop lacking access to the selected local folder.
