# V1 release evidence

This record captures the local release-candidate gates completed on 2026-09-08. The final `v1.0.0` tag remains pending manual acceptance. This record contains no private workspace path, generated credential, resume, account export, or personal job-search data.

## Automated gates

- Backend unit and integration suite: `mvn -q test` — passed.
- Committed sample-bundle import: ten openings and three opportunity-linked actions through the production importer — passed.
- Frontend lint: `pnpm run lint` — passed.
- Frontend production build and rendered-shell test: `pnpm run test` — passed.
- Docker Compose configuration: `docker compose --env-file .env.example --file compose.yaml config --quiet` — passed.
- Windows setup script parsing — passed.
- macOS/Linux setup script syntax (`sh -n setup.sh` in Alpine Linux) — passed.
- Patch whitespace validation: `git diff --check` — passed.

## Clean-install gates

Two independent local data folders were used so modes could not share a database.

### Empty mode

- PostgreSQL, API, and portal became healthy.
- Installation API reported personal mode and version `1.0.0`.
- Twenty-seven Flyway migrations were applied.
- Profile began incomplete and the opportunity list began empty.
- The onboarding dialog and first-use outcome card were visible.
- A profile update survived an API-container restart.
- An unprivileged API container successfully wrote a note into the bind-mounted local folder.

### Synthetic demo mode

- PostgreSQL, API, and portal became healthy on loopback-only port bindings.
- Installation API reported demo mode and version `1.0.0`.
- The fictional Alex profile loaded with onboarding complete.
- Three ranked openings and three daily actions loaded.
- One fictional contact and one unsent outreach plan loaded.
- One preparation track, active sprint task, and daily commitment loaded.
- The persistent synthetic-data disclosure appeared on Summary, Openings, Network, and Prep.
- Openings, outreach, and preparation records were visibly rendered in the containerized portal.

## Release-boundary checks

- Generated `.env` files and local data folders remained ignored.
- No Git remote was configured and no repository was published during preparation.
- The preparatory reconstruction remains a separate parent commit.
- The `v1.0.0` tag is reserved for the final, manually accepted Docker-packaged public release commit.
