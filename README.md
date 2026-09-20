# Job Search Command Center

A local-first command center for discovering high-fit roles, preserving job evidence, tracking applications and referral paths, planning preparation, and reviewing progress.

## This branch is the Codex-assisted runtime package

The default `main` branch is intentionally source-free. Add this folder to a local Codex project to give Codex the product documentation, page-aware guided workflows, safe operating boundaries, examples, and lightweight setup/lifecycle context it needs to help you use the published application.

It does **not** contain the API, portal, broker, database migrations, Dockerfiles, package manifests, or build toolchain. The complete inspectable implementation lives on the [`source` branch](https://github.com/DhruvJawalkar/job-search-command-center/tree/source).

## Start in about 3–8 minutes

You need Docker Desktop on Windows or macOS, or Docker Engine with Compose v2 on Linux. Java, Maven, Node.js, pnpm, and PostgreSQL are not required.

```bash
git clone --depth 1 --single-branch --branch main https://github.com/DhruvJawalkar/job-search-command-center.git
cd job-search-command-center
```

In the Codex desktop app:

1. Create a local project named **Job Search Command Center**.
2. Add this cloned folder and make it the primary folder.
3. Start a project chat, then run the setup command below.

Windows PowerShell:

```powershell
./setup.ps1
```

macOS or Linux:

```bash
./setup.sh
```

Setup proposes the Git-ignored `workspace` folder in this clone, asks whether to start empty or with synthetic demo data, downloads the accepted `v1.0.0` release archive from GitHub, verifies its SHA-256 checksum, installs its digest-pinned runtime controls into the workspace, pulls the published images, and waits for health. If the accepted release has not been published yet, setup stops without installing an unaccepted image.

When setup completes, open a Browser tab beside the Codex project chat, enter `http://127.0.0.1:3000`, and ask:

> Help me on this page.

Codex uses [`AGENTS.md`](AGENTS.md) and the [`guided-workflows`](guided-workflows/README.md) catalog to offer up to three relevant, user-controlled workflows for the visible page.

## Inputs setup requests

- A private local workspace folder; `<clone>/workspace` is the default.
- Empty mode or clearly synthetic demo mode.
- Optional non-default portal/API ports.
- Docker running with Compose v2.

The application’s first screen then asks how application-owned assistant context should be retained. Profile details, role targets, saved openings, contacts, preparation, and reviews are entered only when you choose to add them.

## What remains local

The selected workspace contains the generated secret, PostgreSQL data, resumes, preserved job descriptions, notes, daily workbooks, contact imports, preparation files, and backups. It is ignored by Git, but you should also keep it out of unencrypted cloud synchronization and shared folders.

Runtime definitions and lifecycle helpers are installed under `<workspace>/.jscc`. For example:

```powershell
./workspace/.jscc/jscc.ps1 status
./workspace/.jscc/jscc.ps1 logs
./workspace/.jscc/jscc.ps1 stop
./workspace/.jscc/jscc.ps1 start
```

On macOS/Linux use the equivalent `./workspace/.jscc/jscc.sh` commands. These helpers pull only the immutable digests configured for the installed release; they do not build source or silently upgrade the application.

## Choose the distribution that fits

1. **Codex-assisted runtime (`main`)** — this branch; recommended when you want conversational, page-aware help with the published Docker application.
2. **Docker-only archive** — download the verified ZIP or tar archive from the accepted [GitHub release](https://github.com/DhruvJawalkar/job-search-command-center/releases/tag/v1.0.0); no Git clone or Codex project is required.
3. **Full source (`source`)** — inspect, build, test, or modify every component locally.

The application is a multi-container stack. Pulling or running a single Docker Hub tag is not a supported installation because compatible gateway, API, portal, PostgreSQL, network, mount, and health-check settings must stay together.

## Privacy and security boundary

V1 is an unauthenticated application for one trusted user on one local workstation. Only a hardened fixed-route gateway binds to `127.0.0.1`; the portal, API, and PostgreSQL have no direct host ports and use an internal network with no ordinary outbound route. Do not expose it through a LAN bind, tunnel, reverse proxy, port-forward, shared host, or public deployment.

Optional application-owned OpenAI assistance and live public-page retrieval require the separate reviewed connected overlay, explicit policy opt-in, and confirmation of the destination, purpose, and outbound data preview. Codex task history, the host browser, Docker, and the operating system remain separate trust boundaries.

Read [Installation](docs/INSTALLATION.md), [Codex-assisted use](docs/CODEX_ONBOARDING.md), [Privacy](PRIVACY.md), [Security](SECURITY.md), and the [Threat model](docs/THREAT_MODEL.md) before adding real personal or third-party data. Verification of signatures, provenance, SBOM attestations, scan evidence, and image digests is documented in [Container release verification](docs/CONTAINER_RELEASE.md).

## License and identity

Job Search Command Center is **source-available for non-commercial use** under the [PolyForm Noncommercial License 1.0.0](LICENSE). Copyright © 2026 Dhruv Jawalkar. Attribution and the required notice must remain; modified distributions must not claim to be official or endorsed. Commercial use requires a separate written agreement. See [NOTICE](NOTICE), [branding terms](TRADEMARKS.md), and [commercial licensing](COMMERCIAL-LICENSE.md).
