# Job Search Command Center

A local-first, single-user command center for turning job-search activity into an evidence-backed operating system: discover high-fit roles, preserve job evidence, manage applications and referral paths, plan preparation, and review progress.

The application runs on your own Windows, macOS, or Linux workstation. It starts empty by default and offers an explicit synthetic-demo option.

## Release status

**v1.0.0 is the accepted public release.** Its component tags, signatures, attestations, SBOMs, scan reports, and standalone archives were published from source commit `3a3a3d4` through the protected release workflow. The installer accepts only this immutable release and will not fall back to mutable images.

## What it helps you do

- import and rank daily high-fit openings;
- preserve exact job and application evidence;
- track application stages, interviews, and follow-ups;
- find referral paths and manage outreach;
- plan preparation tracks and skill-development sprints;
- personalize priorities, schedules, recommendations, and weekly goals; and
- choose how application-owned assistance context is retained.

## Three supported setup routes

### 1. Docker-only archive

Download the verified Windows ZIP or macOS/Linux tar archive and SHA-256 checksum from the accepted [GitHub release](https://github.com/DhruvJawalkar/job-search-command-center/releases/tag/v1.0.0). Extract it and run setup.ps1 on Windows or setup.sh on macOS/Linux.

This route needs Docker with Compose v2 only—no Git clone, Codex, Java, Maven, Node.js, pnpm, or host PostgreSQL installation.

### 2. Codex-assisted runtime

Clone the source-free default branch, add it as a local Codex project, and run its guided bootstrap:

    git clone --depth 1 --single-branch --branch main https://github.com/DhruvJawalkar/job-search-command-center.git

The branch contains product documentation, page-aware guided workflows, examples, trust boundaries, and lightweight setup context—not application source. After setup, open http://127.0.0.1:3000 beside the project chat and ask “Help me on this page.”

### 3. Full source

Developers and reviewers can inspect, build, test, or modify every component from the public source branch:

    git clone --branch source https://github.com/DhruvJawalkar/job-search-command-center.git

## Inputs setup requests

- a private local workspace folder;
- empty or clearly synthetic demo mode;
- optional non-default portal/API ports; and
- Docker running with Compose v2.

The workspace holds the generated local secret, PostgreSQL data, resumes, preserved job descriptions, notes, workbooks, contact imports, preparation files, and backups. Runtime controls are installed under workspace/.jscc and remain separate from the replaceable download or repository clone.

## Multi-container and multi-architecture images

This is not a supported single-container docker run application. The accepted, digest-pinned Compose bundle keeps the gateway, portal, API, PostgreSQL, optional connected broker, networks, mounts, and health checks compatible.

Project images are built for both **linux/amd64** (common Intel/AMD computers) and **linux/arm64** (including Apple Silicon and ARM Linux devices). Docker selects the compatible platform image automatically.

Published component tags are:

    dhruvjawalkar/job-search-command-center:api-v1.0.0
    dhruvjawalkar/job-search-command-center:portal-v1.0.0
    dhruvjawalkar/job-search-command-center:broker-v1.0.0

Pulling one component does not install the application. Use an accepted release bundle rather than assembling a stack from tags.

## Local security architecture

V1 is an unauthenticated application for one trusted user on one local workstation. Only a hardened, fixed-route gateway binds to 127.0.0.1. The portal, API, and PostgreSQL have no direct host ports and use an internal Docker network with no ordinary outbound route.

Optional application-owned OpenAI assistance and live public job-page retrieval use a separate authenticated egress broker. They require policy opt-in plus a one-time confirmation showing the destination, purpose, and outbound data preview. The broker has no host port, database access, or workspace mount.

Do not expose V1 through a LAN bind, tunnel, reverse proxy, port-forward, shared host, or public deployment.

## Verifiable release evidence

An accepted release binds exact source, image, and Compose identities through:

- immutable image and platform digests;
- Trivy and Docker Scout vulnerability reports;
- CodeQL and dependency/secret/configuration checks;
- keyless Cosign signatures;
- GitHub and BuildKit provenance attestations;
- SPDX SBOM attestations;
- digest-pinned Compose files and SHA-256 checksums; and
- empty/demo runtime acceptance across 34 database migrations.

These controls provide inspectable evidence for exact artifacts; they are not a claim that software is universally vulnerability-free or that data can never leave a computer.

## Configurable privacy and personalization

At first run, users choose Stateless, Session-only, or Time-bound application-owned assistance context. Time-bound retention can be configured for 7, 30, or 90 days. Saved records remain a separate, deliberate retention category. Profile and Summary controls configure role targets, exclusions, schedule, recommendations, priorities, and weekly goals locally.

Application privacy settings do not control Codex task history, the host browser, Docker, the operating system, or provider-side retention. Keep the workspace out of Git and unencrypted cloud sharing.

Read the project [Privacy notice](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/PRIVACY.md), [Security policy](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/SECURITY.md), and [Threat model](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/docs/THREAT_MODEL.md) before adding real personal or third-party data.

## License and project identity

Source-available for non-commercial use under the PolyForm Noncommercial License 1.0.0. Copyright © 2026 Dhruv Jawalkar. Attribution and the required creator notice must remain; modified distributions must not claim to be official or endorsed. Commercial use requires a separate written agreement.
