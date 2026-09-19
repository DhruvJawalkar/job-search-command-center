# V1 Privacy and Trust milestone

**Status:** Privacy/retention implementation and automated acceptance complete; lifecycle UI/API manual review, release-artifact security evidence, demos, and final release approval remain pending
**Owner:** Dhruv Jawalkar
**Target:** Complete before the public V1 launch and `v1.0.0` tag
**Created:** 18 September 2026
**Last updated:** 18 September 2026

This is the living implementation and evidence plan for the V1 Privacy and Trust milestone. Update the progress checklist, decision log, verification results, and known limitations as work proceeds. Do not mark a release gate complete without linked, reproducible evidence.

## Outcome

The public V1 should give a user meaningful control over locally stored personal data, explain the separate Codex and external-provider boundaries, and default to a runtime configuration that prevents the portal, API, and database data-processing services from making outbound connections.

The release should provide evidence—not only claims—for:

- intentional collection and retention of local data;
- deletion and expiry behavior;
- visible consent before connected assistance;
- loopback-only host exposure, default data-processing-service egress isolation, and the separately disclosed ingress-gateway boundary;
- image contents, provenance, integrity, and known vulnerabilities; and
- the limits of what the application can guarantee.

## Locked decisions

The following decisions are approved and should not be reopened without recording a superseding decision below.

1. **“Stateless” governs assistant-derived context.** It does not erase or prevent storage of an opening, application, referral, preparation item, review, or other record the user deliberately saves.
2. **Explicit user records and assistant memory are separate retention categories.** The UI and documentation must not combine them into one ambiguous retention switch.
3. **Local-only is the default application runtime.** Connected capabilities are disabled until a user explicitly enables them.
4. **The application controls only application-owned data.** It cannot silently configure, delete, or make guarantees about Codex task history, Codex memories, OpenAI service retention, host-browser history, connected applications, or third-party services.
5. **Connected assistance requires a clear transmission boundary.** Before sensitive data leaves the local application, the user must be shown what will be sent, why, and to which destination.
6. **Security and privacy claims must be narrowly worded and supported by reproducible evidence.** Vulnerability scans and SBOMs are supply-chain evidence; they do not prove non-exfiltration.
7. **The official container release is a Compose application.** Publish independently verifiable API and portal images plus the pinned Compose release rather than describing the system as one monolithic image.

## Scope boundaries

### Controlled by Job Search Command Center

- PostgreSQL records created by the application.
- Files inside the selected local workspace.
- Application-generated personalization and assistance records.
- Import staging data and cached external content.
- Application logs and locally generated audit receipts.
- Network access available to the API, portal, and database containers.
- Data included in an application-initiated external request.

### Controlled separately

- Codex task transcripts and task lifecycle.
- Whether a Codex task uses existing memories or contributes to future memories.
- OpenAI or another provider's service-side processing and retention.
- Host-browser requests, history, downloads, and external account activity.
- User-created copies, exports, screenshots, backups, and synchronized folders.
- Operating-system, Docker Desktop, registry, and connected-service telemetry.

The UI must describe these as separate processing boundaries. It must never imply that changing an application preference automatically changes Codex or account settings.

## Current baseline

The existing V1 provides several useful controls:

- A fixed-route gateway publishes loopback ports in `compose.yaml`; portal, API, and PostgreSQL have no direct host publication.
- API and portal runtime images use non-root users.
- Personal state is segregated into a Git-ignored local workspace.
- Synthetic demo data requires an explicit setup choice and remains visibly disclosed in the UI.
- The optional connected broker sets `store: false` for OpenAI Responses API requests and owns the provider credential.
- OpenAI assistance and live-page fetching distinguish preview, one-time confirmation, generation, and human-reviewed save.

The following baseline gaps motivated this milestone. The checked implementation sections below are now the authoritative progress record:

- The original Compose network permitted outbound connectivity.
- PostgreSQL was unnecessarily published to the host, even though it was loopback-bound.
- There was no unified data inventory, retention policy, expiry job, deletion preview, or delete-all workflow.
- Application records, files, imports, derived intelligence, and assistance outputs did not have explicit retention classifications.
- Codex and application retention boundaries were not explained during setup.
- The release did not publish signed images, SBOMs, provenance, scan reports, or network-isolation evidence.

## User-facing privacy model

Add a **Privacy & intelligence** step to first-run onboarding and mirror it in **Profile → Data & privacy**.

### Assistance-context modes

#### Stateless

- Do not persist assistant-generated personalization, prompt payloads, or assistant outputs in application-owned durable storage.
- Do not use previous application-derived assistant context to prepare a later workflow.
- Continue to store only records the user deliberately saves through an explicit action.
- Guide the user to review Codex's separate per-task memory controls.
- Explain that the Codex task transcript remains outside the application's control.

#### Session only

- Allow temporary assistant context for the current local session.
- Keep the context in a bounded, non-durable store.
- Clear it when the session expires or the relevant service restarts.
- Do not silently promote session context into durable personalization.

#### Time-bound personalization

- Retain application-owned derived context for a user-selected period: 7, 30, or 90 days.
- Display the trade-off: longer retention improves continuity and recommendations but retains more sensitive context.
- Apply the configured duration to newly created derived records and safely handle changes to a shorter period.

### Data-retention categories

| Category | Examples | Proposed default | Available controls |
|---|---|---|---|
| Explicit user records | Openings, applications, contacts, outreach, preparation items, weekly reviews | Until explicitly deleted | Keep, category expiry, export, delete |
| Sensitive files | Resumes, preserved job descriptions, LinkedIn exports, notes | Until explicitly deleted | Keep, 30/90 days, export, delete |
| Derived intelligence | Recommendations, personalization signals, structured assistance outputs | Stateless | Session, 7/30/90 days |
| Transient ingestion | Raw imports, parse staging, fetched page cache | 7 days | Session, 7/30 days |
| Minimal audit receipts | Consent version, cleanup result, hashes, timestamps, counts | 90 days without payloads | 30/90 days |
| Backups | User-created snapshots; an optional future one-shot PostgreSQL dump | No application-managed backup in V1 | Keep outside automatic retention. A later low-priority action may create `db-backups/<timestamp>.dump`; no scheduler, automatic rotation, or built-in restore verification is planned. |

Defaults remain proposals until confirmed through implementation review and acceptance testing. The locked separation between explicitly saved records and assistant-derived context is not provisional.

### Required controls

- Show the current mode and effective retention dates.
- Preview affected record and file counts before changing to a shorter policy.
- Provide **Run cleanup now**.
- Provide category-level export and deletion.
- Provide **Delete all local personal data** with an exact scope summary and deliberate confirmation.
- Preserve a payload-free cleanup receipt containing policy version, cutoff, category counts, outcome, and timestamp.
- Explain that SSD behavior, filesystem snapshots, copied backups, and external synchronization can prevent guarantees of physical secure erasure.
- Show whether connected assistance is disabled or enabled and identify its destination.

## Persistence design

### Policy record

Introduce a versioned local policy record with fields equivalent to:

- assistance-context mode;
- derived-context retention days;
- transient-ingestion retention days;
- sensitive-file policy;
- explicit-record policy;
- connected-assistance state;
- consent text version and acceptance timestamp;
- last successful cleanup time and next scheduled cleanup time.

Store no unnecessary account identifiers in this record.

### Classification and timestamps

- Inventory every database table and mounted workspace directory.
- Assign each to one retention category.
- Ensure expirable entities have an authoritative creation or expiry timestamp.
- Define cascade order for relational records and matching filesystem artifacts.
- Treat immutable workflow evidence as immutable only during its configured retention window.
- Avoid storing full prompts or raw sensitive payloads merely to support replay protection; prefer hashes and minimal metadata where possible.

### Cleanup behavior

- Run cleanup at startup and on a bounded daily schedule.
- Use a transaction for database deletion planning and execution where practical.
- Delete associated files only after the database plan is validated.
- Make retries idempotent and record partial failures without including personal payloads.
- Never follow symbolic links or delete outside the selected local workspace.
- Test clock boundaries, shortened policies, interrupted cleanup, cascades, replay, and repeated cleanup.

## Codex boundary

The setup and Profile UI should explain:

- Codex local memories are a Codex feature and are separate from this application's local database.
- Codex provides per-task choices for using existing memories and contributing to future memories.
- Disabling memories is not the same as deleting a Codex task transcript.
- Application retention settings cannot change ChatGPT or Codex account/workspace retention.
- A user should avoid placing secrets or unnecessary sensitive data in any assistant conversation.

Provide a concise setup checklist and link to the official Codex memory and permissions documentation. A generated privacy instruction for Codex may reinforce the selected preference, but instructions are not an enforcement boundary and must not be described as one.

Consider shipping a recommended Codex permission profile that:

- restricts writes to intended workspace roots;
- denies reads of `.env`, PostgreSQL storage, backups, and other credential-bearing paths unless a workflow explicitly needs them;
- keeps command network access off by default; and
- documents how the user can inspect and activate the profile.

The profile must be tested on Windows, macOS, Linux, and WSL before it becomes part of installation guidance.

## Runtime locality and egress design

### Local-only default

- Place the portal, API, and PostgreSQL services only on an internal Compose network.
- Remove their direct host-port publications.
- Publish a minimal fixed-route gateway only on `127.0.0.1:3000` and `127.0.0.1:8080` to preserve Docker Desktop host access.
- Give the gateway no workspace or secret mounts and harden it with an unprivileged user, dropped capabilities, `no-new-privileges`, a read-only root filesystem, and bounded temporary storage.
- Prevent API and portal containers from reaching public destinations, while disclosing that the dual-network gateway has ordinary outbound reachability through its ingress bridge.
- Disable external AI assistance, live job-page fetching, and any other feature that requires application-controlled egress.
- Preserve local browser access to the portal and API.
- Add visible local-only status and explain that it refers to the data-processing services, not the host or gateway boundary.

### Connected mode

- Require an explicit opt-in separate from the retention selection.
- Identify each enabled destination and use case.
- Present a transmission preview for sensitive requests.
- Minimize fields before transmission.
- Do not send resumes, contact exports, notes, or complete workspace snapshots when a smaller structured input is sufficient.
- Prefer destination allowlists and deny all unlisted application-controlled egress.
- Record a payload-free transmission receipt only when the selected retention mode permits it.
- Provide an immediate way to return to local-only mode.

### Evidence

- Verify host listeners on Windows, macOS, and Linux.
- Verify only the gateway has loopback host listeners; portal, API, and PostgreSQL have no direct host ports.
- Demonstrate failed DNS and HTTPS egress from API and portal containers in local-only mode.
- Demonstrate required API-to-database service communication still works.
- Verify fixed gateway routes, no workspace or secret mounts, the read-only reviewed configuration mount, and the declared least-privilege settings.
- Capture Compose configuration, network inspection, test commands, timestamps, and image digests.
- Document that the host browser and Codex are outside the container network boundary.

Use **data-processing services have no outbound route in local-only mode** in public wording. State that the hardened fixed-route gateway provides loopback ingress and has ordinary outbound reachability. Do not claim mathematical proof that no information can ever leave the user's computer.

## Container and supply-chain release

### Published artifacts

The confirmed Docker Hub namespace is `dhruvjawalkar`. Dhruv has created the repository `dhruvjawalkar/job-search-command-center`. Publish the API, portal, and connected-mode broker as independently addressable component-prefixed tags in that repository:

- `dhruvjawalkar/job-search-command-center:api-v1.0.0`
- `dhruvjawalkar/job-search-command-center:portal-v1.0.0`
- `dhruvjawalkar/job-search-command-center:broker-v1.0.0`
- the reviewed fixed-route gateway configuration paired with a digest-pinned official Nginx image;
- a digest-pinned official PostgreSQL image;
- immutable digests for the API, portal, broker, Nginx, and PostgreSQL images;
- a local-only Compose release plus an optional connected override pinned to those digests; and
- `linux/amd64` and `linux/arm64` manifests.

Do not move the `v1.0.0` tag until the complete release candidate passes the gates below.

### Build controls

- Build only from the protected public release commit/tag.
- Pin base images by digest for the release build.
- Produce SPDX SBOM attestations.
- Produce maximum build provenance attestations.
- Sign image digests and attestations with a verifiable release identity.
- Avoid build arguments or provenance fields containing credentials.
- Publish checksums, image digests, source commit, build workflow, and verification commands.

### Security gates

- Docker Scout vulnerability and policy evaluation.
- No known fixable critical vulnerabilities.
- No unreviewed known fixable high vulnerabilities.
- Default non-root user policy.
- Required SBOM and provenance attestations.
- Approved and current base-image policy.
- Source dependency scanning for Maven and pnpm.
- Static analysis and secret scanning.
- Container configuration checks: read-only root filesystem where compatible, dropped capabilities, `no-new-privileges`, bounded writable mounts, and health checks.
- Loopback and egress-isolation acceptance.
- Retention and deletion acceptance.

Document justified exceptions with VEX only after confirming applicability. Never use an exception merely to obtain a passing dashboard.

## Documentation deliverables

- [x] `PRIVACY.md` — plain-language collection, storage, processing, retention, deletion, and external-boundary statement.
- [x] `docs/DATA_FLOW.md` — source-to-storage-to-destination data-flow inventory.
- [x] `docs/THREAT_MODEL.md` — assets, actors, trust boundaries, threats, mitigations, and accepted risks.
- [x] Updated `SECURITY.md` — supported deployment, hardening, reporting, and incident response.
- [x] Updated installation and onboarding guidance.
- [x] Tabular data inventory mapped to retention classes.
- [x] Connected-assistance transmission disclosure and reviewed connected runtime.
- [x] Image verification and SBOM inspection instructions.
- [x] Known limitations and non-goals.
- [x] Versioned privacy notice acceptance displayed and enforced by the application.

## Workstreams and progress

### 1. Data inventory and policy model

- [x] Inventory every database table and configured workspace scope.
- [x] Classify data and identify authoritative timestamps used by the implemented derived-data cleanup.
- [x] Confirm the V1 mode presets and the 7/30/90-day Time-bound controls.
- [x] Add the versioned privacy-policy migration and backend model.
- [x] Add policy read/update APIs with server-owned notice-version validation.

### 2. Retention and deletion engine

- [x] Implement non-mutating preview counts for derived-data cleanup and stricter proposed policies.
- [x] Implement transactional derived-record cleanup; no derived file category currently exists.
- [x] Implement startup and scheduled cleanup for accepted Time-bound policies.
- [x] Enforce Stateless at write time and implement a bounded, non-durable Session-only context store with expiry/restart tests.
- [x] Implement the approved transient-import retention policy across applicable database rows and workspace files.
- [x] Implement exact-scope export, category deletion, and delete-all with preserved reference catalogs and a payload-free operation journal.
- [x] Add payload-free cleanup receipts.
- [x] Complete integrated acceptance for retention, cascade, path-safety, partial filesystem failure, and replay cases. The full backend suite passed 89 tests with 2 intentional skips.

### 3. Onboarding and Profile controls

- [x] Add the first-run Privacy & intelligence step, gated by current notice acceptance.
- [x] Add Profile → Data & privacy.
- [x] Display privacy/personalization trade-offs in plain language.
- [x] Add Codex-boundary guidance and privacy documentation links.
- [x] Add visible local-only/connected-policy status without claiming that an outbound route exists.
- [x] Add transmission preview and review confirmation.

### 4. Runtime isolation and hardening

- [x] Design and validate the isolated Compose network on Docker Desktop for Windows.
- [x] Remove PostgreSQL, API, and portal host publication; expose only the fixed-route loopback gateway.
- [x] Default connected assistance to disabled and fail closed when the accepted policy does not permit it.
- [x] Add least-privilege container settings and a hardened gateway boundary.
- [x] Keep cleanup receipts and runtime verification output payload-free.
- [x] Add a reviewed connected runtime with a fixed OpenAI route and per-request validated live job-page destinations.
- [x] Add reproducible local-only and connected-runtime contract acceptance scripts; the local/static checks and all 14 connected-runtime checks passed.
- [x] Record WSL2 Ubuntu 24.04 empty- and demo-mode locality/egress acceptance with the reusable POSIX host harness.
- [x] Record Apple Silicon and Intel GitHub-hosted macOS installer preflight evidence without claiming Docker Desktop runtime coverage.
- [ ] Record standalone native-Linux and macOS locality/egress acceptance, or retain those exact release limitations.

### 5. Image publication and security evidence

- [x] Create the tag-driven multi-architecture build workflow.
- [ ] Generate SBOM and provenance attestations.
- [ ] Sign and verify release images.
- [x] Add CVE, policy, static, dependency, and secret scanning automation.
- [ ] Assemble the public release evidence package.
- [ ] Publish only after all release gates pass.

### 6. Release and demonstration updates

- [x] Repeat isolated clean empty- and demo-mode acceptance with all 34 migrations. Empty mode remained empty and demo mode loaded the expected synthetic dataset.
- [x] Repeat Windows Docker Desktop runtime and fresh-install acceptance.
- [x] Complete the WSL2 Ubuntu 24.04 empty- and demo-mode smoke tests.
- [x] Complete the Apple Silicon and Intel macOS installer preflight.
- [ ] Complete native-Linux and macOS smoke tests or explicitly record them as release limitations.
- [ ] Update Clip 01 to demonstrate privacy selection and visible local-only status.
- [ ] Review Clips 02–07 for wording affected by the new trust model.
- [ ] Finalize LinkedIn post copy only after the updated seven-clip set is accepted.

## Acceptance criteria

The milestone is complete only when all applicable criteria pass against an exact release commit.

### Privacy controls

- A first-run user can understand and choose Stateless, Session only, or Time-bound personalization without reading external documentation.
- Explicitly saved records remain unaffected by Stateless unless the user separately configures their deletion.
- Shortening a policy provides a preview before deletion.
- Expired derived and transient data are removed from both PostgreSQL and the local filesystem.
- Cleanup is idempotent and cannot escape the selected workspace.
- Delete-all clearly identifies what is and is not under application control.

### Connected assistance

- Local-only is the initial state.
- Connected assistance cannot run without explicit opt-in.
- The user sees the destination and data categories before sensitive transmission.
- External requests minimize data and honor the selected local retention policy.
- Provider-side retention limitations are disclosed accurately.

### Runtime security

- Portal and API are reachable only through the loopback-bound fixed-route gateway.
- Portal, API, and PostgreSQL are not published directly to the host.
- API and portal cannot reach a controlled external test endpoint in local-only mode.
- The gateway boundary and its ordinary ingress-network outbound reachability are disclosed and verified against the declared fixed-route, no-data-mount hardening contract.
- Required service-to-service traffic remains healthy.
- Runtime containers remain non-root and use least-privilege settings compatible with the application.

### Supply chain

- Public image digests resolve to the tested artifacts.
- Signatures, provenance, and SBOMs verify independently.
- Security scans meet the defined severity gates or have reviewed, documented VEX statements.
- The release evidence names the exact source commit, workflow, timestamp, platforms, and image digests.

### Documentation and claims

- Privacy, data-flow, threat-model, security, installation, and verification documentation agree with the implementation.
- Public wording distinguishes local storage, container egress, Codex, the host browser, and provider processing.
- No documentation claims absolute privacy, guaranteed secure erasure, or universal non-exfiltration.

## V1 release gates

- [ ] Product behavior and schema accepted.
- [ ] Privacy and deletion acceptance passed.
- [x] Local-only egress acceptance passed on Docker Desktop for Windows.
- [x] Empty-mode and demo-mode fresh installation passed with all 34 migrations.
- [ ] Security and supply-chain scans passed.
- [ ] Image signatures, SBOMs, and provenance verified.
- [ ] Public documentation and screenshots reviewed for sensitive data.
- [ ] Demo set updated and reviewed.
- [ ] Exact release commit approved.
- [ ] `v1.0.0` tag created only after all preceding gates pass.

## Known limitations to preserve unless implementation changes them

- V1 remains a trusted, single-user, unauthenticated local application.
- A local administrator or another process running as the user may inspect or alter local files and database contents.
- Loopback binding is not authentication.
- Application deletion cannot recall data already copied, exported, backed up, synchronized, screenshotted, or transmitted externally.
- Filesystem deletion does not guarantee physical erasure from SSD media or external snapshots.
- Codex, browsers, registries, operating systems, and connected services have separate controls and retention policies.
- The loopback ingress gateway has ordinary outbound reachability and sees proxied traffic in transit; its fixed routes and hardening reduce risk but do not prove non-exfiltration.
- Vulnerability scanning reduces known risk but cannot prove the absence of unknown vulnerabilities.

## Decision log

| Date | Decision | Rationale | Status |
|---|---|---|---|
| 18 Sep 2026 | Make Privacy and Trust a focused V1 milestone before launch. | The release should establish enforceable local controls and credible evidence before public promotion. | Approved |
| 18 Sep 2026 | Define Stateless as no durable assistant-derived context, independent of deliberately saved records. | The tracker must preserve records the user intentionally saves without retaining unnecessary conversational intelligence. | Approved |
| 18 Sep 2026 | Default to local-only and make connected assistance opt-in. | A denied outbound route is stronger and more testable than a policy statement alone. | Approved |
| 18 Sep 2026 | Treat Codex and provider retention as separate boundaries. | The local application cannot enforce settings owned by another product or service. | Approved |
| 18 Sep 2026 | Keep the approved Stateless, Session-only, and 7/30/90-day Time-bound presets. | The current defaults provide a clear privacy/personalization dial without unnecessary configuration. | Approved |
| 18 Sep 2026 | Connected V1 should support both the OpenAI provider and live job-page fetching. | These are the two application-controlled egress use cases required for the intended assisted workflows. | Approved |
| 18 Sep 2026 | Application-managed backups are outside V1 scope. | Current data is recoverable but not sufficiently critical to justify automatic backup lifecycle machinery before launch. A later low-priority one-shot timestamped database dump may be added. | Approved |
| 18 Sep 2026 | Keep macOS and Linux smoke tests parked pending reprioritization. | Windows and Compose evidence are sufficient for current development; the missing host evidence remains disclosed. | Approved |
| 19 Sep 2026 | Resume cross-platform smoke testing with WSL2 first and pursue resource-efficient cloud evidence where it represents the claimed host. | WSL2 exercises the POSIX installer, Linux permissions, Compose runtime, persistence, and locality controls; cloud-container evidence must not be presented as macOS evidence. | Approved |
| 18 Sep 2026 | Use Docker Hub repository `dhruvjawalkar/job-search-command-center` with component-prefixed tags. | One repository holds independently addressable API, portal, and broker manifests without requiring additional Docker Hub repositories. | Approved |
| 18 Sep 2026 | Publish the connected egress broker as `broker-v1.0.0` alongside the API and portal. | The optional reviewed connected profile must be reproducible, scanned, and pinned independently rather than built from an unverified local context. | Approved |

## Open confirmations

These do not block the approved implementation work but must be resolved before the corresponding release step is finalized.

- Decide whether standalone native-Linux and macOS Docker Desktop installation evidence is required before the final tag; otherwise preserve both exact limitations in release evidence.

## References

- [Codex memories](https://learn.chatgpt.com/docs/customization/memories)
- [Codex permissions](https://learn.chatgpt.com/docs/permissions)
- [Codex agent approvals and security](https://learn.chatgpt.com/docs/agent-approvals-security)
- [OpenAI API data controls](https://developers.openai.com/api/docs/guides/your-data)
- [Docker Compose networks](https://docs.docker.com/reference/compose-file/networks/)
- [Docker Scout](https://docs.docker.com/scout/)
- [Docker Scout policy evaluation](https://docs.docker.com/scout/policy/)
- [Docker build attestations](https://docs.docker.com/build/metadata/attestations/)
- [Docker image attestation lab](https://docs.docker.com/guides/lab-attestation-basics/)
