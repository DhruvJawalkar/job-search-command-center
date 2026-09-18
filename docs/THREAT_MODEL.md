# V1 threat model

**Status:** Pre-release implementation snapshot
**Last updated:** 18 September 2026

This threat model covers the supported single-user Docker Compose deployment of Job Search Command Center. It records current controls and accepted risks; it is not a certification or a guarantee that the application has no vulnerabilities.

## Scope and security objectives

The supported deployment is one trusted user on one local workstation. Its security objectives are to:

1. keep portal and API access behind a fixed-route gateway published only on host loopback;
2. keep PostgreSQL off the host network;
3. deny Internet egress from the portal, API, and PostgreSQL data-processing services in the default configuration;
4. keep personal runtime data out of the public repository and release images;
5. separate deliberately saved records from application-owned assistant-derived context;
6. require an explicit policy opt-in before provider-backed assistance; and
7. describe boundaries the application cannot enforce, including Codex, the browser, the host, external providers, and copied data.

The model does not cover a public, multi-user, LAN, tunneled, or reverse-proxied deployment. Those uses require authentication, authorization, transport security, tenant isolation, rate limiting, and an independently reviewed deployment design.

## Assets

| Asset | Examples | Primary concern |
|---|---|---|
| Workspace secrets | `.env`, database password, future provider credentials | Disclosure or accidental publication |
| Structured records | profile, openings, applications, contacts, outreach, reviews, calendar, privacy policy | Confidentiality, integrity, availability |
| Sensitive files | resumes, job descriptions, notes, LinkedIn exports, preparation artifacts | Confidentiality and controlled retention |
| Third-party personal data | contact details, profile links, relationship notes, outreach history | Confidentiality and responsible handling |
| Assistant-derived context | prompts assembled from records, structured results, decisions, provider metadata | Confidentiality and retention minimization |
| Workflow evidence | application events, imports, artifacts, cleanup receipts | Integrity and correct interpretation |
| Release artifacts | source, images, dependency graph, future attestations | Supply-chain integrity |

## Actors and assumptions

| Actor | Assumption |
|---|---|
| Local user | Trusted to choose what to save, transmit, export, and publish |
| Other local process or local administrator | Potentially able to read loopback services, browser storage, files, container data, or process memory |
| Imported file or external page | Untrusted input that may be malformed, oversized, misleading, or crafted to exploit a parser |
| External website or AI provider | Outside the application trust boundary and governed by separate policies |
| Dependency, base image, or build service | Potential supply-chain source of vulnerable or malicious code |
| Remote network attacker | Should not reach the supported stack directly unless the host or user broadens exposure |

The host operating system, Docker installation, browser, and user account are assumed to be maintained and not already fully compromised. Full host compromise defeats the application's local isolation.

## Trust boundaries and data paths

```text
                          outside application control
                  ┌──────────────────────────────────────┐
                  │ Codex task · external sites/providers│
                  └───────────────▲──────────────────────┘
                                  │ user action or confirmed connected request
Host workstation                  │
┌─────────────────────────────────┼───────────────────────────────┐
│ Host browser ──127.0.0.1──▶ fixed-route gateway                 │
│      │                         │ internal `local_only`            │
│      └── browser local storage├──▶ portal                        │
│                                └──▶ API ──▶ PostgreSQL            │
│                                     │ └──▶ selected workspace     │
│           connected override only:  └──▶ authenticated broker ────┼──▶ reviewed destination
└──────────────────────────────────────────────────────────────────┘
```

Browser traffic crosses a loopback-published gateway port and a fixed proxy route before reaching the portal or API. The portal, API, and PostgreSQL attach only to the internal Docker network and have no ordinary outbound route. The gateway is also attached to an ordinary ingress bridge so Docker Desktop can preserve host access; that gives the gateway outbound reachability. It has no workspace or secret mounts and is hardened, but it is a separately disclosed boundary. The explicit connected override adds an authenticated broker as the only service on both the internal and egress networks; the API can reach only its internal endpoint, not arbitrary Internet destinations. The network also does not constrain the browser, Codex, Docker, the operating system, or other host processes. See [Data flow and inventory](DATA_FLOW.md) for table and folder details.

## Threats, controls, and residual risk

| Threat | Current control | Status and residual risk |
|---|---|---|
| Accidental network exposure | Only the gateway publishes ports, both on `127.0.0.1`; its routes are fixed to portal/API; other services publish no host ports | **Implemented.** Loopback is not authentication; other local processes can still call the API. User-modified Compose can broaden exposure. |
| Data-processing-service exfiltration | Portal, API, and PostgreSQL use only `internal: true`; runtime acceptance verifies failed API/portal DNS and HTTPS probes | **Implemented in the tested Compose design.** The gateway has ordinary outbound reachability through its ingress bridge, and the control does not cover the browser, Codex, clipboard, screenshots, or a compromised Docker/host layer. Cross-platform runtime evidence remains a release task. |
| Unauthorized local access | Single-user deployment guidance and host filesystem protections | **Accepted risk.** V1 has no application login, authorization, or authenticated actor audit. |
| Container privilege escalation | Gateway/API/portal drop all Linux capabilities; all services use `no-new-privileges`; API and gateway run as unprivileged users; gateway rootfs is read-only with bounded tmpfs | **Partially mitigated.** PostgreSQL keeps its image-default capabilities, API/portal/PostgreSQL root filesystems are writable, and container isolation is not a sandbox against every kernel/runtime defect. |
| Compromised ingress gateway | Fixed upstream routes, access logging off, no data/secret mounts, reviewed config mounted read-only, unprivileged user, read-only rootfs, dropped capabilities | **Residual risk.** It handles proxied request/response data in transit and has ordinary outbound reachability. These controls reduce opportunity; they do not prove it could never exfiltrate data if compromised. |
| Secrets committed or copied | Workspace and `.env` are ignored; release gate scans sensitive paths; docs prohibit publication | **Partially mitigated.** A user can still paste a secret into a task, issue, log, backup, or tracked file. No secret manager is built in. |
| Sensitive logs or error output | Logging guidance forbids bodies, credentials, and personal payloads | **Review required.** No claim is made that every dependency error path has been proven payload-free. Logs must be inspected before sharing. |
| Retention choice not honored | Stateless results are response-only with signed deliberate-save artifacts; Session runs are bounded in memory; Time-bound rows use scheduled cleanup; receipts contain metadata rather than payloads; explicit lifecycle previews support category export/deletion and delete all | **Implemented and covered by integrated backend acceptance.** The full backend suite passed retention, policy-transition, lifecycle, path-safety, partial-failure, and replay coverage. OS copies, browser data, Codex/provider data, and backups remain outside secure-erasure claims. |
| Unintended provider transmission | Accepted notice and policy opt-in, explicit connected profile, destination/purpose/field/content preview, and a payload-bound one-time token are all required | **Implemented and fail closed.** The API has no direct provider route or provider key. A compromised broker, host, or permitted provider remains a residual disclosure risk. |
| Provider retains data | OpenAI requests set `store: false`; privacy notice separates provider controls | **External residual risk.** `store: false` is not a promise of zero provider retention. Provider and account policies apply. |
| Codex history confused with app retention | UI/docs state that app policy cannot control Codex tasks, memories, or account retention | **Disclosure control only.** Users must configure Codex separately and minimize sensitive task content. |
| Browser or external-site disclosure | External actions occur in the host browser and remain user-controlled | **Outside container boundary.** Browser history, extensions, logged-in services, uploads, and submitted messages follow browser/site controls. |
| Malicious URL / server-side request forgery | Broker accepts HTTPS:443 only, resolves/pins public addresses, revalidates every redirect, blocks non-public ranges, and bounds redirects/time/type/size | **Defense in depth.** DNS and Internet infrastructure remain external dependencies; a compromised broker or host is outside this boundary. Default Compose has no broker. |
| Malformed or hostile import | Server-side validation, constrained schemas, transaction boundaries, and duplicate review | **Partially mitigated.** Spreadsheet, CSV, file-upload, and text parsers remain attack surfaces; import only files from sources you trust. |
| Data corruption or destructive action | Database transactions, append-only evidence in selected flows, artifact hashes, and backup/recovery scripts | **Partially mitigated.** The local administrator can edit data directly; backup scripts create unencrypted local copies and are not automatic off-device protection. |
| Incomplete deletion | Exact preview digest, typed phrase, FK-safe transaction, canonical file revalidation, payload-free journal, idempotent completed replay, and fresh authorization after partial file failure | **Residual risk.** Integrated tests cover canonical path validation, partial filesystem failure, replay, and fresh authorization after partial failure. Files follow the database transaction and can still partially fail. Deletion cannot recall copied/transmitted data and is not secure media erasure; backups, snapshots, browser storage, Codex, and provider data remain separate. |
| Vulnerable dependency or image | Reproducible source builds and documented scan/sign/SBOM release goals | **Not yet completed.** Published signed images, SBOM/provenance verification, and final vulnerability gates remain pre-release work. Scanning cannot prove absence of unknown flaws. |
| Sensitive content in release image | First-party build contexts are limited to `backend/` and `frontend/`; the gateway uses an official Nginx image with a reviewed static config; runtime workspace is mounted separately | **Structurally limited.** Release inspection and secret scanning are still required for the exact image digests and configuration. |

## Privacy-policy abuse cases

The following cases must remain fail-closed:

- A client cannot transmit merely by enabling connected assistance; the connected profile, current notice consent, preview, and a valid one-time token are independently required.
- A client cannot select an arbitrary Time-bound period; V1 accepts only 7, 30, or 90 days.
- A client cannot record notice acceptance without a notice version, or enable connected assistance without accepting the notice.
- Manual derived-context cleanup does not cascade into openings, applications, contacts, outreach, profile, preparation, or other deliberately saved records.
- A cleanup receipt must not contain prompt text, result payloads, personal records, or secrets.

Stateless governs assistant-derived run context, not records the user deliberately saves. It may retain payload-free policy/audit metadata such as a transmission hash and destination receipt; UI and public copy must preserve that distinction.

## Operational checks

Before release or after changing Compose/security behavior:

1. Render and inspect `docker compose config`.
2. Run `scripts/Test-LocalOnlyRuntime.ps1` for the static contract.
3. Start an isolated disposable stack and run the script with `-Runtime` to inspect actual bindings, health, Docker-network `Internal` state, gateway properties, and failed API/portal outbound DNS/HTTPS probes.
4. Confirm only the gateway has loopback bindings, other services have no host ports, fixed proxy routes work, and required API/database traffic remains healthy.
5. Run backend and frontend tests, the release gate, secret/content review, and `git diff --check`.
6. Record the exact source revision, Compose configuration, host platform, timestamp, and image digests for evidence.

Passing these checks establishes evidence for the tested configuration only. It does not prove universal non-exfiltration or validate a different host, Docker version, Compose override, or manually edited network.

## Incident response

If data exposure or credential leakage is suspected:

1. Stop the exact Compose project and remove any tunnel, broad listener, or override.
2. Preserve only the logs and metadata needed to investigate, and redact personal data before sharing.
3. Rotate affected database, provider, external-account, and repository credentials.
4. Inspect the selected workspace, browser downloads/storage, Codex task, backups, synchronized folders, and external service activity according to the suspected path.
5. Restore only from a verified private snapshot, and keep the prior state until recovery is accepted.
6. Report product vulnerabilities through the private channel described in [Security](../SECURITY.md).

## Accepted risks and non-goals

- No protection from a fully compromised host or trusted local administrator.
- No authentication, authorization, tenant separation, or remote-deployment support.
- No guarantee of physical media erasure.
- No recall of data copied to backups, screenshots, exports, Codex, browsers, or external providers.
- No guarantee that scanning detects every vulnerability or that an internal Docker network defeats every runtime exploit.
- The ingress gateway has ordinary outbound reachability and sees proxied traffic in transit; its fixed routes and hardening are risk reduction, not universal non-exfiltration proof.
- Stateless results are response-only and Session-only results are memory-bound; neither is a promise about Codex tasks, browser state, explicit saved records, audit metadata, provider processing, or copied data.
- Application-level export and deletion cannot control browser storage, unmounted backups, copied exports, Codex/provider data, or physical remnants.
- The connected broker is the sole supported egress path, but compromise of that broker, Docker, or the host can bypass application-layer intent. Signed/scanned public-image evidence remains a separate release gate.

Review this model whenever a migration, mount, external destination, container capability, published port, connected feature, or retention behavior changes.
