# V1 distribution packaging plan

## Objective

Ship one accepted V1 application through three explicit journeys without weakening the signed-image, local-data, privacy, or license boundaries:

1. **Docker only** — download a small release bundle, choose a local workspace, and pull the published images. No Git clone or Codex project is required.
2. **Codex assisted** — shallow-clone the default `main` branch, which contains only the runtime bundle, guided workflows, samples, and user documentation. It pulls the same published images and enables page-aware Codex guidance.
3. **Full source** — switch to the public `source` branch to inspect, build, test, or modify the API, portal, broker, and release pipeline locally.

All three routes use the same V1 product behavior and local workspace contract. Their difference is the surrounding distribution material and whether repository-native Codex guidance is enabled.

## Non-goals

- A single-container `docker run` command. The supported application is a four-service local stack: gateway, portal, API, and PostgreSQL, with an optional connected egress broker.
- Hosted accounts, telemetry, application authentication, or a cloud database.
- Weakening digest pinning, signature verification, attestations, or release gates to improve download attribution.
- Treating Docker Hub pulls as an exact count of people or successful installations.

## Shared release boundary

- The `v1.0.0` source tag points to the fully reviewed commit on the `source` branch.
- The protected workflow builds one API, portal, and broker image for `linux/amd64` and `linux/arm64`.
- Project images must have zero known vulnerabilities at every reported severity in both Trivy and Docker Scout.
- The published Compose bundle pins every project and upstream image by digest.
- Signatures, provenance, SBOM attestations, the release manifest, checksums, and runtime acceptance refer to the same tag and image digests.
- The accepted bundle is attached to a durable GitHub Release, not retained only as an expiring Actions artifact.

## Package 1 — Docker only

### User journey

1. Open the Docker Hub repository or GitHub Release.
2. Download the Windows ZIP or macOS/Linux tarball and its SHA-256 checksum.
3. Extract and run `setup.ps1` or `setup.sh`.
4. Choose a local workspace folder and empty or synthetic-demo mode.
5. The installer validates Docker Compose, creates the workspace contract and secret, pulls the digest-pinned images, waits for health, and prints the local URL and lifecycle commands.

The installer must not require Java, Maven, Node.js, pnpm, PostgreSQL, a Git clone, or Codex. It must never build an image.

### Workspace contract

Runtime control files live under `<workspace>/.jscc/`; user and database data live in sibling folders. This lets a user remove the downloaded archive after setup while preserving a complete, versioned local runtime definition.

```text
<workspace>/
  .env
  README.md
  .jscc/
    compose.yaml
    compose.connected.yaml
    gateway/nginx.conf
    release-manifest.json
    SHA256SUMS
    VERSION
    jscc.ps1
    jscc.sh
  postgres-data/
  application-resumes/
  notes/
  daily-high-fit-job-roles/
  linkedin-data-import/
  preparation-workspace/
  company-targets/
  backups/
```

The default workspace is platform-native:

- Windows: `%LOCALAPPDATA%\JobSearchCommandCenter`
- macOS: `~/Library/Application Support/JobSearchCommandCenter`
- Linux: `${XDG_DATA_HOME:-$HOME/.local/share}/job-search-command-center`

Rerunning setup is idempotent. It preserves the database password and data. It must reject an attempted empty/demo conversion in an existing workspace and direct the user to a separate folder.

## Package 2 — Codex-assisted default branch

The default `main` branch is a small, source-free project context. It contains:

- `setup.ps1`, `setup.sh`, source-free Compose/runtime files, and lifecycle helpers;
- `guided-workflows/`, `AGENTS.md`, Codex onboarding, privacy/security/user documentation, and sample daily files;
- license, required notice, trademark, commercial-license, release manifest, checksums, and verification instructions; and
- no backend, frontend, broker, build context, package manifest, migration source, or developer build toolchain.

Setup proposes `<clone>/workspace`, pulls the accepted images, and enables the runtime `codex` distribution channel and page-aware guidance. The README begins with the three-package selector and explains that the default branch is a runtime/assistant package rather than the source branch.

Recommended clone command:

```bash
git clone --depth 1 --single-branch --branch main https://github.com/DhruvJawalkar/job-search-command-center.git
```

## Package 3 — full source branch

The `source` branch preserves the complete application, tests, Dockerfiles, release templates, and workflows. Its setup continues to build locally. Source-build documentation uses:

```bash
git clone --branch source https://github.com/DhruvJawalkar/job-search-command-center.git
```

The branch remains source-available for non-commercial use under the repository license. Public tags identify reviewed source commits on this branch. Development for later versions occurs here or on branches based on it, not on the runtime-only `main` branch.

## Safe branch migration

1. Finish application/runtime changes and all source-branch tests on current `main`.
2. Create and push `source` at that exact commit.
3. Build and validate a candidate minimal tree in a temporary local branch.
4. Fresh-clone both candidates and test Docker-only, Codex-assisted, and source-build journeys.
5. Replace `main` with an orphan, runtime-only history only after the `source` branch is visible and verified remotely.
6. Keep release workflows on default `main` only where GitHub requires them for dispatch; ensure the selected tag/ref checks out and validates the `source` commit.
7. Create `v1.0.0` only after manual acceptance, then publish images and durable release assets through the protected workflow.
8. Enable immutable GitHub and Docker Hub version-tag rules only after exact-digest acceptance succeeds.

This sequence preserves rollback: before changing `main`, the full source is independently reachable at `source` by commit ID.

## Docker Hub attribution

Docker Hub pull totals are adoption signals, not install counters. One local-only installation pulls at least the API and portal images; connected mode also pulls the broker. CI, scanners, verification, cached layers, repeat pulls, architecture selection, and version checks affect the totals.

Keep digest pinning. Map each release's API, portal, and broker digests from `release-manifest.json` to Docker Hub's repository/tag/digest reports. Record a post-release-validation baseline, then report trends in data downloads and unique clients where the account's analytics permit it. Do not claim that repository pull count equals unique users or completed installations.

The Docker Hub Overview is maintained from `docs/DOCKER_HUB_OVERVIEW.md` so the external copy does not drift from the release.

## Acceptance gates

- Docker-only installers pass Windows, macOS, and Linux syntax/prerequisite tests, including paths with spaces.
- A real Linux Docker run proves no build context, correct empty/demo state, 34 migrations, persistence, loopback-only exposure, and blocked API/portal egress.
- The portal shows no Codex workflow prompts when `app.codex-guidance-enabled=false` and retains them when true.
- The minimal `main` branch contains no application source or build context and all local links resolve.
- A fresh `main` clone pulls accepted images and starts without source tooling.
- A fresh `source` clone builds and passes the existing source release gate.
- Release ZIP/tar assets, scripts, manifests, licenses, and Compose files are checksummed and attached only after protected runtime acceptance.
- Docker Hub Overview instructions reproduce the accepted no-clone journey.

## Decision log

| Date | Decision | Reason |
|---|---|---|
| 20 Sep 2026 | Offer Docker-only, Codex-assisted, and full-source packages. | Different users need a minimal runtime, conversational guidance, or complete inspectable source. |
| 20 Sep 2026 | Use a source-free Compose bundle rather than a single `docker run`. | The supported product depends on gateway, portal, API, and PostgreSQL boundaries. |
| 20 Sep 2026 | Keep exact digest pinning even though pull metrics are not exact install counts. | Artifact integrity and reproducibility are more important than tag-attribution convenience. |
| 20 Sep 2026 | Make `main` the runtime/Codex package and preserve full source on `source`. | The default clone becomes the simplest assisted user journey while source remains explicitly available. |
