# V1 data inventory and flow map

**Status:** Living pre-release document
**Applies to:** V1 Privacy and Trust milestone
**Last updated:** 18 September 2026

This document inventories data handled by Job Search Command Center and identifies the boundaries it can and cannot enforce. Keep it synchronized with database migrations, mounted workspace folders, connected features, and the public privacy notice.

## Trust boundaries

1. **Host browser** — renders the portal from `127.0.0.1:3000` and calls the API on `127.0.0.1:8080`.
2. **Gateway container** — the only service with host-published ports; fixed routes proxy loopback traffic to the portal and API. It bridges the internal and ingress networks and has no workspace or secret mounts.
3. **Portal container** — serves the interface; it should not hold durable personal data.
4. **API container** — validates requests, implements workflows, reads selected workspace files, and persists application records.
5. **PostgreSQL container** — stores durable structured application state.
6. **Selected local workspace** — stores database files and user-visible files separately from tracked source (the default `workspace/` is inside the checkout but Git-ignored).
7. **Codex task** — a separate assistant context that may read permitted project/workspace files; its transcript and memories are not controlled by this application.
8. **External destinations** — optional AI provider, user-selected job pages, and external sites opened by the host browser.
9. **Build and registry services** — process source and image metadata during release construction; they do not need personal runtime data.

## Primary local flow

```text
User
  │
  ▼
Host browser ──loopback──▶ fixed-route gateway ──internal network──▶ Portal
                                      │
                                      └──────────internal network──▶ API
                                                                        │
                                                                        ├──▶ PostgreSQL
                                                                        └──▶ selected workspace folders
```

The portal, API, and PostgreSQL use only the internal `local_only` network, have no host-published ports, and have no ordinary outbound route. PostgreSQL is reachable only by the API. The gateway is the sole dual-network service: it joins `local_only` and an ordinary `ingress` bridge so loopback-published ports remain usable on Docker Desktop. Its routes are fixed to the portal and API; it has no workspace or secret mounts, but it has ordinary outbound reachability through `ingress`. The host browser and gateway boundary therefore remain outside the stronger data-processing-service egress boundary.

Runtime acceptance must treat this as a layered boundary, not universal non-exfiltration: failed API/portal DNS and HTTPS probes demonstrate the tested data-processing topology, while the gateway, host browser, Codex, Docker, and operating system are separately disclosed boundaries. Current full runtime evidence was captured on Windows Docker Desktop; real-host macOS and Linux validation remains pending.

## Structured database inventory

### User configuration and policy

| Data | Tables | Sensitivity | Retention category |
|---|---|---|---|
| Local profile and targeting preferences | `local_user_profile` | Personal and professional | Explicit user record |
| Summary schedule and presentation preferences | `local_summary_preferences` | Personal routine and preferences | Explicit user record |
| Privacy and intelligence selection | `privacy_policy` | Privacy preference and notice acceptance metadata | Policy and audit metadata |
| Cleanup outcomes | `privacy_cleanup_receipt` | Policy revision, cutoff, counts, outcome, timestamp | Minimal payload-free receipt |
| Connected transmission authorization and outcomes | `transmission_preview`, `transmission_receipt` | Destination, purpose, minimized field names, payload hash, outcome, timestamps; no request payload | Minimal payload-free authorization/receipt metadata |
| Data lifecycle previews and receipts | `data_lifecycle_operation` | Categories, plan/token hashes, counts, status, timestamps; no exported/deleted payload or absolute path | Retained payload-free accountability journal |

### Opportunities and applications

| Data | Tables | Sensitivity | Retention category |
|---|---|---|---|
| Resume variants and labels | `resume_variant` | Professional | Explicit user record |
| Openings, decisions, links, and archive state | `job_opportunity` | Professional | Explicit user record |
| Applications, stages, notes, and follow-ups | `job_application`, `application_event` | Professional and potentially sensitive | Explicit user record |
| Preserved resume/job-description evidence metadata | `application_artifact` | Sensitive | Sensitive file metadata |
| Daily imports and ranked evidence | `import_batch`, `opportunity_observation`, `daily_priority_action` | Professional | Explicit record plus transient source |
| Generic inbox parsing and review | `generic_inbox_item`, `generic_inbox_candidate`, `inbox_duplicate_match` | Professional; raw text may be sensitive | Transient ingestion until accepted; then explicit record |

### Network and outreach

| Data | Tables | Sensitivity | Retention category |
|---|---|---|---|
| Contacts, employers, profile links, email, relationship notes | `network_contact` | Personal information about the user and third parties | Explicit user record |
| Outreach drafts/status, notes, outcomes, and follow-ups | `outreach_activity` | Personal communications metadata | Explicit user record |
| Referral paths and research notes | `referral_candidate` | Personal information about third parties | Explicit user record |
| LinkedIn import batches and connections | `linkedin_connection_import_batch`, `linkedin_connection` | Sensitive third-party personal data | Sensitive file/import plus explicit record |

### Skills and preparation

| Data | Tables | Sensitivity | Retention category |
|---|---|---|---|
| Tracks, milestones, preparation items, practice sessions, commitments | `preparation_track`, `preparation_milestone`, `preparation_item`, `practice_session`, `daily_prep_commitment` | Professional development | Explicit user record |
| Saved sprints and sprint membership | `preparation_sprint`, `preparation_sprint_item` | Professional development | Explicit user record |
| Track resources | `preparation_track_resource` | Notes and links may be personal | Explicit user record |
| Skill taxonomy and evidence | `canonical_skill`, `skill_alias`, `job_description_snapshot`, `job_skill_observation` | Professional; snapshots may contain source content | Explicit record plus cached external content |
| Personal skill backlog, learning resources, and project evidence | `personal_skill_backlog`, `personal_skill_preparation_link`, `skill_learning_resource`, `skill_project_evidence` | Professional development | Explicit user record |

### Reviews and calendar

| Data | Tables | Sensitivity | Retention category |
|---|---|---|---|
| Weekly review and metric snapshot | `weekly_review`, `weekly_metric_snapshot` | Personal performance | Explicit user record |
| Append-only reflection revisions | `weekly_review_revision` | Personal reflection | Explicit user record |
| Calendar events and reminders | `calendar_event` | Personal schedule | Explicit user record |
| Interview rounds, notes, and debriefs | `interview_round` | Sensitive professional record | Explicit user record |

### Assistant-derived state

| Data | Tables | Sensitivity | Retention category |
|---|---|---|---|
| Provider/model, result payload, provider response ID, errors, token counts | `assistance_run` | Derived intelligence; payload may contain personal context | Derived intelligence |
| Applied/dismissed decisions and selected fields | `assistance_decision` | Derived intelligence and review metadata | Derived intelligence |

Stateless applies to this assistant-derived category. It must not delete explicitly saved domain records merely because an assistant helped prepare them.

## Local workspace inventory

| Folder/file | Contents | Sensitivity | Proposed handling |
|---|---|---|---|
| `.env` | Database credential and runtime configuration | Secret | Deny assistant access by default; never export or log |
| `postgres-data/` | Complete structured database | Highly sensitive | Application-managed lifecycle; never source-control |
| `application-resumes/` | Resume files and preserved application artifacts | Highly sensitive | Explicit retention/export/delete controls |
| `notes/` | User-authored scratch notes | Potentially sensitive | Explicit retention/export/delete controls |
| `daily-high-fit-job-roles/` | Opening workbooks and action files | Professional | Processed source retention control |
| `linkedin-data-import/` | Connection/account exports | Highly sensitive third-party data | Minimize retention; explicit deletion after import |
| `preparation-workspace/` | Exercises and preparation artifacts | Personal/professional | Explicit retention/export/delete controls |
| `company-targets/` | Target-company source files | Professional preferences | Explicit retention/export/delete controls |
| `backups/` | User-created or script-created copies of workspace state | Highly sensitive | Local backups are unencrypted; keep private and arrange encryption/rotation separately |

Derived-context retention cleanup deletes eligible database rows only. The separate user-confirmed lifecycle operation can export or delete approved workspace-file categories. It resolves and validates each target beneath the canonical selected workspace, does not follow symbolic links, and revalidates canonical path, size, and modified time immediately before export or deletion.

## Host-browser storage inventory

| Key/category | Contents | Retention/control | Boundary |
|---|---|---|---|
| `job-search-command-center.scratch-note.v1` | Unsaved quick-scratchpad text | Persists until the user clears it or browser storage is cleared | Host browser, not PostgreSQL or the workspace |
| Calendar notice keys | Identifiers of dismissed calendar notices | Persists until browser storage is cleared or the application rotates the stored set | Host browser, not PostgreSQL or the workspace |

Application cleanup does not clear browser local storage.

## Assistant and external flows

### Codex-guided workflows

```text
User ──▶ Codex task ──permitted reads/actions──▶ repository or selected workspace
  │             │
  │             └──▶ OpenAI service processing governed by Codex/account controls
  └──▶ explicit saves through the local portal/API
```

The application can display guidance and provide a project permission profile, but it cannot enforce Codex transcript or memory retention. Codex memory controls and application retention controls must remain visibly separate.

### Optional application AI assistance

```text
Selected local record
  ──field minimization and transmission review──▶ API
  ──one-time token──▶ authenticated egress broker
  ──fixed connected route──▶ configured AI provider
  ──structured response──▶ local review
  ──explicit apply/dismiss──▶ application database
```

The optional connected profile uses the OpenAI Responses API with `store: false`. This does not guarantee zero provider retention. Provider-backed calls require an accepted privacy notice, connected-assistance policy opt-in, a server-issued preview, and a one-time confirmation token bound to the exact operation, provider destination, and payload hash. The API cannot call the provider directly; only the authenticated broker owns the provider key and fixed provider route. The default `compose.yaml` has no broker or data-processing-service egress.

### Live job-page retrieval

The API contains an optional live job-page fetch path. It is unavailable in local-only mode. In the explicit connected profile, the user previews the selected URLs and confirms once before the API can ask the broker to fetch them. The broker:

- accepts HTTPS port 443 only, authenticates the internal API request, and has no arbitrary proxy route;
- resolves and pins the reviewed public address, then repeats validation for every redirect;
- blocks loopback, link-local, private-network, documentation, mapped-private, and unsafe redirect targets;
- limits redirects, duration, response size, and HTML content type;
- retain only the minimum required source snapshot; and
- classify the snapshot as cached external content or an explicitly preserved record.

### Host-browser external actions

External job links, LinkedIn, Overleaf, Google Drive, and similar sites open in the host browser and are outside the container network boundary. Opening, submitting, sending, uploading, or changing an external account remains a deliberate user-controlled action governed by that destination's policies.

## Runtime logging

Application logs must exclude:

- request and response bodies containing personal data;
- resumes, job descriptions, messages, notes, prompts, and assistant output;
- database passwords, API keys, cookies, authorization headers, and full `.env` content;
- raw LinkedIn exports; and
- absolute private paths where a category-level identifier is sufficient.

Permitted operational logging should use bounded identifiers, status, duration, counts, policy version, and error categories. Stack traces must be reviewed for payload leakage before public release.

## Current retention and deletion flow

```text
Effective policy
   │
   ├──▶ classify eligible `assistance_run` and `assistance_decision` rows
   ├──▶ produce counts and cutoff preview
   ├──▶ user review for a manual run, or accepted Time-bound startup/daily enforcement
   ├──▶ transactional database cleanup
   └──▶ payload-free receipt with counts, cutoff, status, and timestamp
```

Time-bound cleanup considers persisted assistant-derived rows older than the selected 7-, 30-, or 90-day cutoff. An accepted Time-bound policy runs cleanup at application startup and on its daily due schedule. Stateless creates no durable assistance run, while Session only uses bounded in-memory runs that disappear when the backend restarts. Transmission previews and payload-free receipts are audit-and-policy metadata rather than assistant output; their hashes and routing metadata do not enable result replay. The UI previews affected counts before cleanup or a stricter policy. Cleanup does not delete deliberately saved records.

Category export and deletion use a separate exact-scope flow:

```text
fixed category selection
   ├──▶ canonical database/file inventory and plan digest
   ├──▶ expiring preview revision + one-preview token
   ├──▶ user review (and exact typed phrase for deletion)
   ├──▶ unchanged-plan check
   ├──▶ transactional child-first database deletion, when selected
   ├──▶ canonical regular-file revalidation and deletion, when selected
   └──▶ payload-free operation receipt; partial files require a fresh preview
```

The ZIP export contains the selected payload and is therefore sensitive. Delete all preserves application reference catalogs and the lifecycle journal. Neither flow clears browser data, recalls copies, or guarantees physical media erasure.

## Planned release and build flow

```text
Protected source commit/tag
  ──▶ multi-platform API and portal builds
  ──▶ SBOM + provenance attestations
  ──▶ vulnerability and policy gates
  ──▶ signature
  ──▶ Docker Hub images by immutable digest
  ──▶ digest-pinned Compose release and verification evidence
```

Personal runtime workspaces must never enter the build context. Release workflows must not use build arguments for credentials because provenance metadata can expose build inputs.

## Release verification still required

- Database inventory matches the latest Flyway migration.
- Every mounted workspace folder has a documented owner, sensitivity, and retention category.
- Stateless cleanup removes derived assistance state without removing deliberately saved records.
- Shortened retention is previewed and applied consistently.
- Data export/category-delete/delete-all scope, cascade ordering, unsafe-link rejection, replay, and partial-file behavior pass integrated acceptance.
- Local-only containers cannot resolve or connect to a controlled public endpoint.
- Portal and API remain reachable only through loopback host bindings.
- PostgreSQL has no host port.
- Connected requests show destination and data categories before sensitive transmission.
- Logs and cleanup receipts contain no personal payloads.
- Release images contain no workspace data, `.env`, credentials, or test fixtures with real personal information.

## References

- [V1 Privacy and Trust milestone](V1_PRIVACY_AND_TRUST_MILESTONE.md)
- [Security policy](../SECURITY.md)
- [Installation guide](INSTALLATION.md)
- [Codex memories](https://learn.chatgpt.com/docs/customization/memories)
- [Codex permissions](https://learn.chatgpt.com/docs/permissions)
- [OpenAI API data controls](https://developers.openai.com/api/docs/guides/your-data)
- [Docker Compose networks](https://docs.docker.com/reference/compose-file/networks/)
