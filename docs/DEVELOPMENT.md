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

The application uses Flyway. Never edit an applied migration; add the next numbered migration. A clean V1 release applies migrations V1 through V27.

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

- all three services are healthy;
- the API health endpoint and installation status respond;
- all 27 migrations are applied;
- the profile can be saved and read back;
- demo mode has a visible banner and fictional records;
- empty mode has no synthetic records and shows the first-use card;
- service ports are bound only to `127.0.0.1`; and
- stopping and restarting preserves the selected local data.

## Release gate

Before tagging a release:

1. Run backend tests and frontend lint/test.
2. Validate Compose configuration.
3. Complete clean demo and empty Docker installs in separate folders.
4. Scan tracked files for credentials, personal paths, resumes, account exports, database files, logs, and private planning artifacts.
5. Run `git diff --check` and confirm the worktree is clean after the release commit.
6. Tag the complete packaged commit—not the preparatory reconstruction commit.

The PowerShell scripts under `scripts/` preserve deeper Windows-based backup, recovery, accessibility, performance, and fresh-install checks from V1 closure. Docker package validation is the cross-platform public release gate.
