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
- The tag-driven container-release workflow passed structural checks and local Compose rendering. The later pre-publication run below completed the remote source, dependency, secret, configuration, CodeQL, multi-architecture image, upstream-runtime, and SBOM gates. Registry publication, exact published-digest rescans, signatures, provenance attestations, and public image-digest evidence remain pending.
- Native-Linux and macOS full-host installation/locality evidence remains pending and must be recorded as a limitation if it is not completed before the release tag.

## WSL2 Linux-host follow-up — 2026-09-19

- Ubuntu 24.04.3 LTS under WSL2 ran the POSIX host harness from a fresh clone in the native Linux filesystem, using the normal user identity and Docker Desktop's Linux/amd64 engine.
- Independent empty and demo workspaces passed non-interactive `setup.sh`, four-service health, 34 migrations, expected data-mode contents, gateway-only isolated loopback ports, and failed API/portal DNS and HTTPS egress probes.
- The generated `.env` was mode `0600`; the API ran as the invoking Linux UID/GID; and an API-written bind-mount artifact retained the invoking user's ownership.
- Database and workspace markers survived stop/start, and the unchanged gateway recovered both fixed routes after independent API and portal recreation.
- A final empty-mode run confirmed that a fresh policy has no accepted privacy notice, connected assistance is disabled, and notice version `v1` is reported. Consent selection remains a manual browser checkpoint.
- The first run exposed an uppercase Compose-project-name defect in the new harness; the generated name was corrected to lowercase and all subsequent runs passed. Evidence collection was also corrected to retain unique filenames for multiple modes.
- This is valid WSL2/Linux-userland and Docker Desktop integration evidence. It does not establish standalone Linux Engine behavior, rootless Docker, SELinux/AppArmor distributions, or macOS Docker Desktop behavior.

## macOS hosted preflight follow-up — 2026-09-19

- Commit `b3051780d77380394faab580f8db2abae9af8a04` passed the path-filtered [macOS installer preflight](https://github.com/DhruvJawalkar/job-search-command-center/actions/runs/35432511892).
- The Apple Silicon lane ran on `macos-15` and verified `arm64`; the Intel lane ran on `macos-15-intel` and verified `x86_64` — both passed.
- Each real macOS VM preserved the Git executable bits, parsed both POSIX scripts, found the required BSD/macOS utilities, cloned the repository into a path containing spaces, and verified clear Docker-not-running and Compose-v2-missing prerequisite messages.
- Both jobs published architecture-specific JSON evidence artifacts with 30-day retention.
- This preflight does not establish Docker Desktop runtime, bind-mount, loopback, persistence, or container-egress behavior on macOS. That exact limitation remains a release decision unless a suitable Mac host becomes available.

## Container release-candidate validation — 2026-09-19

- Commit `82537ab9faa29cae8bb651100ca079e4cfdf160c` passed the non-publishing [container release candidate run #4](https://github.com/DhruvJawalkar/job-search-command-center/actions/runs/35435739704).
- Source tests and production dependency audit, secret/configuration/dependency scans, and both Java/Kotlin and JavaScript/TypeScript CodeQL analyses passed.
- API, portal, and connected-mode broker images were independently built, scanned, and supplied with SPDX JSON SBOM evidence for both `linux/amd64` and `linux/arm64`; all six fixable high/critical project-image gates passed.
- The exact Docker Official `nginx:1.30.5-alpine3.24-slim` dependency passed its full-report and fixable operating-system-package gates on both architectures.
- The exact Docker Official `postgres:17.11-alpine3.24` dependency passed its fixable operating-system-package gates on both architectures. Its full reports retain the reviewed `gosu` Go-standard-library findings, and the strict target, package, Go version, 22-CVE ID set, and count assertion passed on each architecture. The applicability and remediation trigger are documented in `docs/CONTAINER_RELEASE.md`.
- Twelve retained artifacts cover the resolved immutable inputs, source scan evidence, six project-image scan/SBOM bundles, and four upstream runtime-dependency reports.
- This run intentionally left the release tag blank and publication disabled. The protected registry login, multi-architecture publication, exact published-digest rescans, signing, BuildKit/GitHub provenance attestations, digest-pinned Compose runtime acceptance, and final bundle attestation were therefore skipped rather than claimed as complete.

## Protected release controls — 2026-09-19

- The `container-release` GitHub environment requires approval by `DhruvJawalkar`, disallows administrator bypass, and accepts deployments only from tags matching `v*.*.*`.
- Self-review is allowed because the personal repository currently has one release owner; the gate records explicit owner approval but does not claim independent separation of duties.
- The active `Immutable release tags` ruleset has no bypass actor and prevents updates, deletion, and force pushes for matching `v*.*.*` source tags while still allowing initial creation.
- `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` are present as environment secrets. Their values are not stored in the repository or exposed to validation-only jobs.
- Docker Hub immutable image-tag rules remain a post-acceptance action. They will be enabled only after the published digests pass the post-push security, signature, attestation, and runtime gates so a failed candidate can still be removed.

## Rejected publication candidate and API remediation — 2026-09-20

- Protected run `35441939677` published and signed three `v1.0.0` candidate image indexes, but stopped before final acceptance when GitHub provenance verification lacked `GH_TOKEN`. The authentication defect was corrected without weakening the release boundary.
- A subsequent Docker Scout review of the API candidate found six known findings: two Java dependency findings, three Alpine `coreutils` findings, and one Alpine `nghttp2` finding. The candidate and its source tag were rejected; none of its signatures or attestations are presented as V1 release evidence.
- The API now selects Jackson Databind `3.1.5` and Log4j API `2.25.5`. Its runtime image no longer installs `curl`, removes the unnecessary `coreutils` package, and uses BusyBox `wget` for the container healthcheck and local egress probe.
- The locally rebuilt `linux/amd64` API image passed the full backend suite, a disposable 34-migration Compose runtime acceptance, package-absence assertions for `coreutils` and `nghttp2`, and a Docker Scout scan reporting zero known critical, high, medium, low, or unspecified vulnerabilities.
- The primary publication workflow now requires Trivy and Docker Scout to report zero known project-image vulnerabilities at every severity, including unfixed and unspecified findings. The incident-specific recovery workflow was retired after its rejected images and source tag were removed, eliminating a stale alternate publication path. The separate, explicitly reviewed upstream Nginx/PostgreSQL policy is unchanged.
- Non-publishing [container validation run #7](https://github.com/DhruvJawalkar/job-search-command-center/actions/runs/35489614712) completed successfully: all 14 applicable jobs passed, including the remediated API image and the portal and broker images on both `linux/amd64` and `linux/arm64`. The protected publication-only jobs were correctly skipped.
- The rejected `api-v1.0.0`, `portal-v1.0.0`, and `broker-v1.0.0` Docker Hub tags and the rejected GitHub `v1.0.0` source tag were removed with owner approval. The immutable GitHub tag ruleset was restored immediately afterward and applies to zero tags until the accepted tag is created.

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
