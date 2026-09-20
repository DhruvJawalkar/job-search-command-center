# Reviewed connected runtime

The default installed runtime is local-only: the API, portal, and PostgreSQL have no ordinary outbound route. Connected V1 is a separate, explicit overlay for two user-confirmed operations:

1. schema-constrained OpenAI Responses requests for assisted drafting; and
2. fetching a user-selected public HTTPS job page for local evidence extraction.

The overlay adds a narrow authenticated egress broker. The API never receives the provider key, and the broker has no host port, workspace mount, database access, or dynamic forwarding endpoint.

## Prepare the private workspace

Add the values you need to the selected workspace `.env`; never add them to this repository:

```dotenv
CONNECTED_BROKER_TOKEN=<a fresh random value of at least 32 characters>
APP_OPENAI_MODEL=<optional provider model>
OPENAI_API_KEY=<optional provider key; broker only>
```

The provider key is optional when only live public-page evidence is needed. Before every request, the portal displays the destination, purpose, minimized field names, and outbound content. Confirmation creates a server-issued, single-use token bound to the payload, operation, and destination and expiring after ten minutes.

## Start the installed overlay

The runtime files live under `<workspace>/.jscc`. Replace the paths below with the actual workspace. Do not copy Compose files out of that folder or replace their digest-pinned images.

Windows PowerShell:

```powershell
$Workspace = (Resolve-Path './workspace').Path
$Control = Join-Path $Workspace '.jscc'
$env:APP_DATA_ROOT = $Workspace.Replace('\','/')
$Project = (Get-Content (Join-Path $Workspace '.env') | Where-Object { $_ -match '^COMPOSE_PROJECT_NAME=' } | Select-Object -First 1) -replace '^COMPOSE_PROJECT_NAME=', ''
docker compose --project-name $Project --env-file (Join-Path $Workspace '.env') --file (Join-Path $Control 'compose.yaml') --file (Join-Path $Control 'compose.connected.yaml') up --detach --pull never --no-build --wait
```

macOS/Linux:

```bash
workspace=$(CDPATH= cd -- ./workspace && pwd)
control="$workspace/.jscc"
project=$(sed -n 's/^COMPOSE_PROJECT_NAME=//p' "$workspace/.env" | sed -n '1p')
APP_DATA_ROOT="$workspace" docker compose --project-name "$project" --env-file "$workspace/.env" --file "$control/compose.yaml" --file "$control/compose.connected.yaml" up --detach --pull never --no-build --wait
```

## Return to local-only

Stop the combined topology, then use the installed lifecycle helper to restart only the local stack:

```powershell
docker compose --project-name $Project --env-file (Join-Path $Workspace '.env') --file (Join-Path $Control 'compose.yaml') --file (Join-Path $Control 'compose.connected.yaml') down
& (Join-Path $Control 'jscc.ps1') start
```

```bash
APP_DATA_ROOT="$workspace" docker compose --project-name "$project" --env-file "$workspace/.env" --file "$control/compose.yaml" --file "$control/compose.connected.yaml" down
"$control/jscc.sh" start
```

Removing the broker token, provider key, and optional model after stopping reduces accidental reuse. `down` without `--volumes` preserves the database and workspace bind mounts.

## Enforced destinations and residual risk

OpenAI assistance has one fixed route: `https://api.openai.com/v1/responses`. Public-page retrieval accepts public HTTPS port 443 only, validates initial and redirected addresses, rejects non-public ranges, pins the validated address for the TLS request, limits redirects, content type, size, and duration, and never exposes a generic forwarding endpoint.

Connected mode is intentionally weaker than local-only mode. The destination service, DNS/CDN/provider infrastructure, and the outbound broker become additional trust boundaries. Provider-side retention is outside the application’s control. Signatures, allowlists, scans, and payload-free receipts are evidence of tested controls, not proof that data can never leave the machine.
