# Reviewed connected runtime

The default `compose.yaml` remains local-only: the API, portal, and PostgreSQL use only the internal network and cannot make ordinary outbound connections. Connected V1 is a separate, explicit override for two user-confirmed operations:

1. schema-constrained OpenAI Responses requests for inbox structuring or weekly-reflection drafts; and
2. fetching a user-selected public HTTPS job page for local evidence extraction.

Do not add an outbound network to the API. `compose.connected.yaml` adds a small egress broker as the only service attached to both `local_only` and `connected_egress`. The API authenticates to that broker with a local shared token. The broker has no host port, workspace mount, database access, or dynamic forwarding endpoint.

## Start only when needed

Add these values to the selected workspace `.env`; never commit them:

```dotenv
CONNECTED_BROKER_TOKEN=<a fresh random value of at least 32 characters>
APP_OPENAI_MODEL=<optional model used for OpenAI-assisted workflows>
OPENAI_API_KEY=<optional provider key; broker only>
```

The provider key is optional when only live job-page evidence is needed. Start the opt-in profile from the repository folder:

```powershell
docker compose --env-file workspace/.env -f compose.yaml -f compose.connected.yaml up --build -d
```

Using a custom workspace means replacing `workspace/.env` with that workspace's `.env` path.

Before any request, the portal must display the destination, purpose, minimized field names, and outbound content. Confirmation produces a server-issued token that expires after ten minutes, is bound to the payload hash, operation, and destination, and can be consumed once. The database stores no token or outbound payload: it keeps only token/payload hashes and payload-free transmission metadata. Application-owned assistant results still follow the selected Stateless, Session-only, or Time-bound policy.

## Enforced destinations

- OpenAI assistance has one fixed route: `https://api.openai.com/v1/responses`. The broker replaces the internal authorization token with the provider key; the API never receives the provider key.
- Job-page fetch accepts public HTTPS port 443 only. It resolves every initial destination and redirect, rejects private, loopback, link-local, multicast, documentation, and other non-public address ranges, pins the validated address for the TLS request, revalidates every redirect, accepts only HTML, allows at most five redirects, times out, and reads at most 4 MiB.

## Return immediately to local-only

Stop the connected stack and restart the default topology:

```powershell
docker compose --env-file workspace/.env -f compose.yaml -f compose.connected.yaml down
docker compose --env-file workspace/.env -f compose.yaml up -d
```

Removing `CONNECTED_BROKER_TOKEN`, `OPENAI_API_KEY`, and `APP_OPENAI_MODEL` from the workspace `.env` after stopping reduces accidental reuse. The PostgreSQL and workspace bind mounts remain local; `down` without `--volumes` does not remove them.

## Verification

Run the structural boundary check without credentials or external traffic:

```powershell
./scripts/Test-ConnectedRuntime.ps1
```

Run broker tests locally; they use only loopback and do not contact provider or job sites:

```powershell
node --test --test-isolation=none egress-broker/test/*.test.mjs
```

## Residual risks

Connected mode is deliberately weaker than local-only mode. A compromised egress broker can use its outbound network; DNS and public CDN/provider infrastructure remain trusted after address validation; a permitted public service can log requests; provider-side retention is outside this application's control; and the host browser, Codex, Docker, operating system, extensions, clipboard, screenshots, and other local processes remain separate boundaries. A signature, SBOM, scan, allowlist, or payload-free receipt is evidence of a tested control—not proof that exfiltration is impossible.
