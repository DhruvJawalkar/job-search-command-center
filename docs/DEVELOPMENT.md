# Development and release validation

The Docker-first installer is the supported user path. Host development remains available for contributors.

## Host prerequisites

- JDK 21
- Maven 3.9+
- Node.js 22+
- pnpm 11+
- Docker with Compose v2 for PostgreSQL and packaged-install testing

## Backend

```bash
cd backend
mvn test
```

The application uses Flyway. Never edit an applied migration; add the next numbered migration. The current Privacy and Trust build applies migrations V1 through V34.

## Frontend

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm run lint
pnpm run test
```

The frontend test command performs a production build and validates the rendered application shell.

## Docker package

Use a new disposable local workspace for each install test:

```powershell
./setup.ps1 -ProjectFolder C:\path\to\disposable-folder -Demo
```

```bash
./setup.sh --folder /path/to/disposable-folder --demo
```

Verify:

- PostgreSQL, API, portal, and gateway are healthy;
- the API health endpoint and installation status respond;
- all 34 migrations are applied;
- the profile can be saved and read back;
- demo mode has a visible banner and fictional records;
- empty mode has no synthetic records and shows the page-aware Codex workflow hint;
- the gateway is the only service with host-published ports and binds them only to `127.0.0.1`;
- PostgreSQL, API, and portal have no direct host-published ports;
- the data-processing services use only the internal `local_only` network and API/portal DNS and HTTPS egress probes fail;
- the gateway exposes only fixed routes to the named portal and API services, with no secrets or workspace mounts; and
- stopping and restarting preserves the selected local data.

Validate the rendered Compose contract without changing a running installation:

```powershell
./scripts/Test-LocalOnlyRuntime.ps1
```

After starting the exact stack under test, add `-Runtime` to inspect its actual port bindings and networks; verify the gateway's loopback-only fixed-route boundary; verify application health through the gateway; and verify failed API/portal outbound DNS and HTTPS probes. The runtime check does not start, stop, recreate, or remove containers.

## Release gate

Before tagging a release:

1. Run backend tests and frontend lint/test.
2. Validate Compose configuration.
3. Complete clean demo and empty Docker installs in separate folders.
4. Scan tracked files for credentials, personal paths, resumes, account exports, database files, logs, and private planning artifacts.
5. Run `./scripts/Test-ContainerReleaseWorkflow.ps1` and review [Container release and verification](CONTAINER_RELEASE.md).
6. Run `git diff --check` and confirm the worktree is clean after the release commit.
7. Tag the complete packaged commit—not the preparatory reconstruction commit.

The PowerShell scripts under `scripts/` preserve deeper Windows-based backup, recovery, accessibility, performance, and fresh-install checks from V1 closure. Docker package validation is the cross-platform public release gate.
