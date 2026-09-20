# Job Search Command Center

A local-first, single-user command center for discovering roles, preserving job
evidence, tracking applications and referral paths, planning preparation, and
reviewing progress.

## Release status

`v1.0.0` is the planned first public container release. Component tags,
immutable digests, the standalone archives, checksums, signatures, SBOMs,
provenance, and scan evidence become supported release assets only after final
acceptance. The planned standalone assets are:

- `job-search-command-center-v1.0.0-standalone.zip` for Windows;
- `job-search-command-center-v1.0.0-standalone.tar.gz` for macOS and Linux; and
- `job-search-command-center-v1.0.0-standalone.SHA256SUMS` for verification.

If the
[`v1.0.0` GitHub release](https://github.com/DhruvJawalkar/job-search-command-center/releases/tag/v1.0.0)
does not show these accepted assets and the verification evidence, do not
treat a visible Docker tag as an accepted installation.

## Choose one package route

### 1. Docker-only standalone — recommended

Download the archive for your operating system and the `SHA256SUMS` file from
the accepted GitHub release, verify the archive, and follow the
[standalone Docker guide](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/docs/STANDALONE_DOCKER.md).

This route requires Docker with Compose v2 only. It does not clone the
repository and does not require Java, Maven, Node.js, pnpm, or PostgreSQL on the
host. On Windows, expand the verified ZIP and run `./setup.ps1`. On macOS or
Linux, extract the verified tar archive and run `./setup.sh`. The installer
prompts for a private workspace and runtime mode, generates local database
credentials, installs the controls under `<workspace>/.jscc`, pulls the
accepted images by immutable digest, and starts the stack. The downloaded
archive and extraction folder can then be removed; the workspace is the
persistent installation.

### 2. Codex-assisted Docker runtime

Clone the default `main` branch, add that folder as the primary folder of a
local Codex project, and use its guided setup and page-aware workflow catalog:

```bash
git clone https://github.com/DhruvJawalkar/job-search-command-center.git
cd job-search-command-center
```

Use this route when you want the same published Docker runtime together with
`AGENTS.md`, the checked-in `guided-workflows/` catalog, samples, and Codex
onboarding material. Codex assistance is optional and does not turn V1 into an
autonomous application-submission or outreach agent.

See
[Codex-assisted use](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/docs/CODEX_ONBOARDING.md)
and
[installation](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/docs/INSTALLATION.md).

### 3. Full source build

Developers who want to inspect, test, modify, and build every component should
use the `source` branch:

```bash
git clone --branch source https://github.com/DhruvJawalkar/job-search-command-center.git
cd job-search-command-center
```

The source branch contains the Spring Boot API, React/TypeScript portal,
connected-mode egress broker, Dockerfiles, tests, and release tooling. Review
the source branch's README and development guide before building. The license
remains non-commercial; source availability is not permission for commercial
use.

## This is a multi-container application

The tags in this Docker Hub repository are independently addressable release
components:

```text
dhruvjawalkar/job-search-command-center:api-v1.0.0
dhruvjawalkar/job-search-command-center:portal-v1.0.0
dhruvjawalkar/job-search-command-center:broker-v1.0.0
```

These names are shown for the planned release and become supported only after
acceptance. A `docker pull` downloads one component; it does not install the
application. Do not run the API, portal, or broker alone and do not assemble a
stack from mutable tags. The supported installation uses the accepted Compose
bundle, which also pins compatible PostgreSQL and Nginx images and supplies
the required network, mount, health-check, and gateway configuration.

The project images support:

- `linux/amd64`
- `linux/arm64`

## Local workspace and persistence

Application data is not stored in the replaceable release bundle. Setup asks
for a private local workspace, installs versioned control files under
`<workspace>/.jscc`, and persists:

- PostgreSQL data;
- resumes and preserved job descriptions;
- notes;
- daily high-fit workbooks and action files;
- LinkedIn connection imports;
- preparation and company-target files;
- local configuration and generated secrets; and
- private backups.

Use `<workspace>/.jscc/jscc.ps1` on Windows or
`<workspace>/.jscc/jscc.sh` on macOS and Linux for the supported lifecycle
commands: `start`, `stop`, `restart`, `status`, `logs`, and `pull`. The `pull`
command retrieves the exact digests configured for the installed release; it
does not upgrade the installation to a different release.

Keep that workspace out of Git and unencrypted cloud sharing. Stop/start,
container recreation, and application-image updates preserve the bind-mounted
workspace when the same path is used. Back up the complete stopped workspace
before an upgrade. Database migrations can make an older application image
unsafe against a newer live database, so retain a pre-upgrade backup rather
than assuming image rollback alone is sufficient.

## Local-only security boundary

V1 is an unauthenticated application for one trusted user on one local
workstation. The accepted Compose topology publishes only a hardened,
fixed-route gateway on `127.0.0.1`; the portal, API, and PostgreSQL have no
direct host ports and use an internal network with no ordinary outbound route.

Do not expose V1 through a LAN bind, tunnel, reverse proxy, port-forward,
shared host, or public deployment. CORS is not authentication. The host
browser, Codex, Docker Desktop or Engine, the operating system, and other local
processes remain separate trust boundaries.

Optional application-owned OpenAI assistance and live public-page retrieval
use a separate reviewed connected overlay with a narrow authenticated broker,
policy opt-in, and one-time confirmation of each destination, purpose, and
payload preview. Codex use does not require that overlay.

Read the full
[security policy](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/SECURITY.md),
[privacy notice](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/PRIVACY.md),
and
[threat model](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/docs/THREAT_MODEL.md)
before adding real personal or third-party data.

## Release verification and evidence

An accepted release publishes exact image-index and platform digests, keyless
signatures, GitHub/BuildKit provenance, SPDX SBOM attestations, vulnerability
scan reports, a digest-pinned Compose bundle, and checksums. The release
manifest binds those artifacts to the exact source commit and source tag.

Evidence becomes authoritative only when the accepted GitHub release exposes
the manifest and verification material. Source workflow files, a successful
local build, or a Docker Hub tag by itself is not release evidence. Follow
[container release verification](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/docs/CONTAINER_RELEASE.md)
for the exact Cosign, GitHub attestation, digest, SBOM, and checksum checks.
Signatures and scans establish specific properties of exact artifacts; they do
not prove that software is vulnerability-free or that data can never leave a
computer.

## Licensing and project identity

Job Search Command Center is source-available for non-commercial use under the
[PolyForm Noncommercial License 1.0.0](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/LICENSE).
Copyright © 2026 Dhruv Jawalkar.

Pulling an image does not grant commercial-use rights. Redistributors and
modifiers must preserve the license, required creator notice, and attribution;
modified distributions must not imply that they are official or endorsed.
Commercial use requires a separate written agreement. See the
[required notice](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/NOTICE),
[branding terms](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/TRADEMARKS.md),
and
[commercial licensing](https://github.com/DhruvJawalkar/job-search-command-center/blob/main/COMMERCIAL-LICENSE.md).

## About Docker Hub pull counts

Docker Hub pull totals are useful as a directional repository-usage signal,
not as a count of people or successful installations. Pulls may include CI,
release verification, vulnerability scanning, updates, repeated downloads, and
multi-platform manifest or image retrieval. The repository total also combines
the API, portal, and broker tags. It cannot reliably distinguish unique users,
package routes, component usage, or completed local setups.

Use pull totals for broad trend tracking and pair them with release-download,
support, and feedback signals when evaluating adoption.
