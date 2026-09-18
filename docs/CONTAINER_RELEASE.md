# Container release and verification

The container workflow prepares three project images in the confirmed Docker Hub repository `dhruvjawalkar/job-search-command-center`, plus two upstream runtime dependencies:

- `dhruvjawalkar/job-search-command-center:api-v1.0.0` for `linux/amd64` and `linux/arm64`;
- `dhruvjawalkar/job-search-command-center:portal-v1.0.0` for `linux/amd64` and `linux/arm64`;
- `dhruvjawalkar/job-search-command-center:broker-v1.0.0` for `linux/amd64` and `linux/arm64`; and
- the Docker Official `nginx:1.29-alpine` and `postgres:17-alpine` images, each resolved, scanned, and recorded by immutable multi-architecture digest.

The project does not rebuild, rebrand, or sign Nginx or PostgreSQL. The workflow scans both platforms of each dependency and records their upstream digests. Signatures and project attestations apply only to the API, portal, and connected-mode egress-broker images built by this repository.

## Current status

The workflow and local structural check are release preparation. They have not, by their presence in the repository, built, scanned, signed, or published an image. A successful workflow run against the exact release tag and retained evidence are required before making any such claim.

## Source-available license

The project images contain software offered under the PolyForm Noncommercial License 1.0.0, not an OSI-approved open-source license. Pulling an image does not grant commercial-use rights. Redistributors and modifiers must preserve `LICENSE`, `NOTICE`, creator attribution, and the restrictions in `TRADEMARKS.md`; modified distributions must not present themselves as official. Commercial use requires a separate written license from Dhruv Jawalkar. Third-party packages and the upstream Nginx and PostgreSQL images retain their own licenses.

The OCI license, license-URL, and required-notice labels make the terms and creator notice discoverable on each project image. The final Compose bundle also includes `LICENSE`, `NOTICE`, `TRADEMARKS.md`, and `COMMERCIAL-LICENSE.md`. Labels do not replace those license texts.

## Repository configuration before the first publication

Create a protected GitHub environment named `container-release` and configure required reviewers. Prevent self-review when the repository plan supports it. The reviewer should compare the tag, commit, scan results, resolved base digests, Nginx and PostgreSQL digests, and license notice before approving the publish job.

Configure:

| Setting | Kind | Purpose |
|---|---|---|
| `DOCKERHUB_NAMESPACE` | Repository variable, optional | Lowercase Docker Hub user or organization. If omitted, the workflow lowercases the GitHub repository owner. Set it explicitly when those namespaces differ. |
| `DOCKERHUB_USERNAME` | `container-release` environment secret | Least-privilege Docker Hub service user used only by the protected publish job. |
| `DOCKERHUB_TOKEN` | `container-release` environment secret | Revocable Docker Hub access token with push access only to `dhruvjawalkar/job-search-command-center`. |

Do not add registry credentials as Docker build arguments, image labels, Compose values, provenance parameters, or repository variables. The workflow's validation jobs do not receive the Docker Hub secrets.

Protect release tags and require the source/test, CodeQL, image, runtime-dependency, and locality checks on the release commit. Keep the `v1.0.0` tag unmoved. The workflow also refuses a manual release request unless the selected commit already carries the exact `vMAJOR.MINOR.PATCH` tag.

## Safe workflow operation

`Container release candidate` supports:

1. A manual dispatch with `publish` left at its default `false`. This validates an existing tag but cannot enter the registry-login or publish job.
2. A manual dispatch with `publish: true` selected from the exact version tag ref, or an exact version-tag push. A branch-dispatched publication is rejected, even when the commit also has the tag, so the keyless signature identity remains tag-bound. All validation jobs still run first. The `container-release` environment then pauses for manual approval before registry credentials are exposed.

Before approval, the workflow:

- resolves Maven, Temurin, Node, Nginx, and PostgreSQL tags to immutable index digests;
- runs Maven and portal tests, pnpm's production advisory gate, Trivy dependency/secret/configuration scans, and CodeQL;
- builds API, portal, and egress-broker images independently for amd64 and arm64 without publishing;
- records every known high/critical finding and fails on known fixable high/critical vulnerabilities;
- creates SPDX JSON SBOMs for the scanned project images; and
- scans both architectures of the resolved official Nginx and PostgreSQL runtime images.

After explicit approval, the workflow:

- rebuilds each project image from the same digest-pinned base inputs;
- publishes a multi-architecture manifest with BuildKit SPDX SBOM and `mode=max` provenance attestations;
- re-scans each exact published platform digest with Trivy and runs an independent Docker Scout fixable high/critical gate;
- signs each project image index with Sigstore keyless signing tied to the GitHub workflow identity;
- creates GitHub-signed build provenance and platform-specific SPDX SBOM attestations;
- emits JSON evidence with the tag, source commit, index digest, platform digests, and upstream runtime digests;
- assembles a separately downloadable base Compose file and optional connected-mode override whose API, portal, broker, Nginx, and PostgreSQL references are all immutable digests and which have no source build contexts; and
- validates the rendered Compose configuration before preserving the bundle and its checksums.

Any known fixable critical or high vulnerability blocks the workflow. Unfixed findings remain in the report and require release-owner review. An exception must document applicability and remediation; do not add an ignore rule or VEX statement solely to make a gate pass. CodeQL findings also require review because successful analysis execution is not, by itself, proof that the result set is empty.

The exact-digest Trivy and Docker Scout gates necessarily run after the candidate manifest is pushed. If either post-publication gate fails, the run is failed: do not announce, tag as `latest`, or consume that candidate, and remove the failed tag from Docker Hub before retrying from a corrected new tag. A pushed candidate is not an accepted release until signing, attestation, verification, evidence review, and the remaining V1 gates all pass.

## Local structural check

This offline-safe check validates the workflow's security invariants without contacting GitHub or Docker Hub:

```powershell
./scripts/Test-ContainerReleaseWorkflow.ps1
```

It checks triggers, protected publication, lowercase namespace handling, exact action-SHA pins, platforms, attestations, signature verification, scanners, gates, required secret names, and upstream runtime boundaries. It does not execute the Actions workflow or validate credentials.

## Verification after a successful protected run

Obtain the immutable API, portal, and broker digests from the workflow's `published-*-evidence` artifacts, not from a mutable tag. Substitute the recorded namespace, repository, and digest below.

```bash
cosign verify \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  --certificate-identity 'https://github.com/DhruvJawalkar/job-search-command-center/.github/workflows/container-release.yml@refs/tags/v1.0.0' \
  docker.io/dhruvjawalkar/job-search-command-center@sha256:<api-digest>

gh attestation verify \
  oci://docker.io/dhruvjawalkar/job-search-command-center@sha256:<api-digest> \
  --repo DhruvJawalkar/job-search-command-center

docker buildx imagetools inspect \
  docker.io/dhruvjawalkar/job-search-command-center@sha256:<api-digest>
```

Repeat these commands for the portal and broker. Use `gh attestation verify` on the recorded platform digests to verify the signed SPDX SBOM attestations. Inspect the downloaded `sbom-*.spdx.json`, scan JSON, and `resolved-images.json` evidence alongside the attestations.

Download `compose-release-<tag>` and verify `SHA256SUMS`, including the bundled license and notice files. Every `image:` entry in `compose.yaml` and `compose.connected.yaml` must end in an `@sha256:...` digest and neither file may contain a `build:` key. Compare its Nginx and PostgreSQL values with `resolved-images.json`, and review both architecture scan artifacts for each. Do not use this project's Cosign identity to verify either upstream dependency.

Signatures establish artifact identity and integrity for the verified digest. SBOMs, provenance, dependency scans, static analysis, secret scans, and vulnerability scans are useful evidence, but they do not prove that software is vulnerability-free or that data can never leave a computer.
