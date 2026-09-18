# V1 release evidence

This record captures the local release-candidate gates initially completed on 2026-09-08 and the follow-up checks noted below. The final `v1.0.0` tag remains pending manual acceptance. This record contains no private workspace path, generated credential, resume, account export, or personal job-search data.

## Automated gates

- Backend unit and integration suite: `mvn -q test` — passed.
- Committed sample-bundle import: ten openings and three opportunity-linked actions through the production importer — passed.
- Frontend lint: `pnpm run lint` — passed.
- Frontend production build and rendered-shell test: `pnpm run test` — passed.
- Docker Compose configuration: `docker compose --env-file .env.example --file compose.yaml config --quiet` — passed.
- Windows setup script parsing — passed.
- macOS/Linux setup script syntax (`sh -n setup.sh` in Alpine Linux) — passed.
- Patch whitespace validation: `git diff --check` — passed.

## Guided-workflow follow-up — 2026-09-15

- Thirty-two workflow files were validated for required metadata, unique IDs, and working local Markdown links.
- The static release gate validated a page index and 3–5 workflows for every application page — passed.
- Frontend lint, production build, and rendered-shell test — passed.
- Browser checks confirmed the workflow hint on all seven routes with no horizontal overflow.
- Source and browser checks confirmed that the Profile modal and first-opening redirect no longer force a journey.

## Summary-personalization follow-up — 2026-09-15

- The full backend suite passed with persistence coverage for Summary preferences and rejection coverage for overlapping schedule blocks.
- Frontend lint, production build, and rendered-shell checks passed after the configurable Summary was added.
- The static release gate and Docker Compose configuration check passed.
- Desktop and narrow browser checks confirmed the configuration dialog, priority sources, rotating or fixed specialization, and responsive schedule editors render without horizontal overflow.
- A clean browser load with an existing scratchpad note produced no hydration errors after browser-storage initialization was corrected.
- The initial Privacy and Trust foundation acceptance run described below applied all 30 migrations in both empty and demo modes. A later Privacy and Trust acceptance rerun covered V31–V34 and applied all 34 migrations in both modes.

## Privacy and Trust follow-up — 2026-09-18

- Backend unit and integration suite: 89 tests, 0 failures, 0 errors, and 2 intentional skips — passed.
- Frontend lint, production build, and three rendered-shell/privacy workflow tests — passed.
- Privacy policy, server-owned notice-version acceptance, stricter-policy preview, manual cleanup confirmation, payload-free receipts, and repeated-save scheduling behavior — passed.
- Read-only data inventory covered all 46 application tables and six configured filesystem scopes; path-safety tests included outside-workspace and unsafe-link rejection — passed.
- Docker Desktop for Windows local-only acceptance: 35 of 35 checks passed. PostgreSQL, API, and portal had no direct host publications; API and portal DNS/HTTPS egress probes failed as required; the fixed-route gateway remained reachable on loopback.
- Egress-broker test suite: 4 tests passed.
- Connected-runtime structural acceptance: 14 of 14 checks passed without making an external request.
- Local-only/static contract checks and the static release gate passed.
- Independent empty-mode and demo-mode fresh setup applied all 34 migrations. Empty mode stayed empty; demo mode produced the expected application and three opportunities — passed.
- The tag-driven container-release workflow passed structural checks and local Compose rendering. Live registry publication, remote CVE/secret/config scans, signatures, SBOM/provenance verification, and public image-digest evidence remain pending.
- macOS and Linux full-host installation/locality evidence remains pending and must be recorded as a limitation if it is not completed before the release tag.

## Clean-install gates

Two independent local data folders were used so modes could not share a database.

### Empty mode

- PostgreSQL, API, and portal became healthy.
- Installation API reported personal mode and version `1.0.0`.
- Thirty-four Flyway migrations were applied.
- Profile began incomplete and the opportunity list began empty.
- The page-aware Codex workflow hint was visible without forcing a dialog or redirect.
- A profile update survived an API-container restart.
- An unprivileged API container successfully wrote a note into the bind-mounted local folder.

### Synthetic demo mode

- PostgreSQL, API, and portal became healthy on loopback-only port bindings.
- Thirty-four Flyway migrations were applied.
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
