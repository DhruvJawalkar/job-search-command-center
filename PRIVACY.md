# Privacy notice

**Notice version:** v1
**Last updated:** 18 September 2026

Job Search Command Center is a local-first, single-user application. The supported Docker Compose setup keeps access on your computer, stores application data in the workspace you select during setup, and gives the data-processing services no outbound route by default. A small fixed-route gateway provides loopback access. This notice explains what the application controls and, just as importantly, what it does not control.

## What the application stores

The application stores data when you enter, import, or deliberately save it, and stores fictional seed records when you opt into demo mode. Depending on the features you use, that can include:

- profile, role-targeting, schedule, Summary, and privacy preferences;
- job openings, fit evidence, applications, stages, follow-ups, and calendar items;
- resumes, job descriptions, notes, preparation work, and weekly reviews;
- contacts, LinkedIn imports, referral research, and outreach records, which may include information about other people;
- imported workbooks and action files;
- application-owned assistant runs, generated results, review decisions, provider metadata, and token counts; and
- privacy-cleanup, connected-transmission, and data-lifecycle records containing policy or routing metadata, hashes, counts, outcomes, and timestamps—but not saved, transmitted, exported, or deleted payloads.

Structured records are stored in the local PostgreSQL data directory. User-visible files are stored in folders under the selected local workspace. The portal also uses the host browser's local storage for the unsaved quick scratchpad and dismissed calendar-notice keys. A complete inventory is in [Data flow and inventory](docs/DATA_FLOW.md).

The supported release does not intentionally include application analytics or telemetry. The application does produce ordinary container and application logs; do not publish those logs without reviewing them for private paths or unexpected error content.

## Saved records and assistant-derived context are separate

Records that you deliberately save—such as an opening, application, contact, outreach item, preparation item, review, or profile—are **explicit saved records**. They remain in the local database or workspace until you delete them through an available feature or remove the local workspace. Selecting Stateless does not delete those records.

Application-owned assistant runs and their review decisions are **assistant-derived context**. Profile → Data & privacy provides three policy choices:

- **Stateless:** the intended policy is no durable assistant-derived context between interactions.
- **Session only:** the intended policy is temporary context for the active local session.
- **Time-bound:** derived context is eligible for cleanup after 7, 30, or 90 days.

V1 enforces Stateless by returning assistant-derived output without a durable run row; a signed, short-lived response artifact lets the user deliberately save reviewed fields or skill evidence as an explicit record. Session only keeps assistant-derived runs in bounded backend memory and loses them when the backend restarts. Time-bound stores runs and review decisions until the selected cutoff, with cleanup at startup and when the daily schedule becomes due. **Preview cleanup** and **Run cleanup now** remain available for eligible persisted rows.

Cleanup does not delete deliberately saved records, workspace files, browser storage, Codex tasks, or provider-side data. A successful cleanup records a payload-free receipt. It does not guarantee physical erasure from SSD media, backups, snapshots, or synchronized copies.

## Local-only and connected boundaries

The supported Compose configuration:

- publishes only a gateway on `127.0.0.1:3000` and `127.0.0.1:8080`;
- gives the gateway fixed proxy routes to the portal and API and no workspace or secret mounts;
- keeps the portal, API, and PostgreSQL on an internal Docker network with no direct host ports or outbound route; and
- drops Linux capabilities from the gateway, API, and portal, enables `no-new-privileges`, and gives the gateway a read-only root filesystem with bounded temporary storage.

Runtime acceptance on Windows Docker Desktop confirms that DNS and HTTPS probes fail from the API and portal while local access and API-to-database communication remain healthy. Equivalent real-host macOS and Linux evidence is still pending. This is a bounded control, not a claim that information can never leave the computer. The gateway is dual-networked to provide ingress and has ordinary outbound reachability through that ingress network; hardening and fixed routes reduce its role but do not make it a universal non-exfiltration boundary. The host browser, Codex desktop app, Docker Desktop or Engine, operating system, other local processes, backups, screenshots, clipboard, and user-opened external sites are also outside the internal data-processing network.

The privacy policy includes a connected-assistance preference, but enabling it does not create an outbound route in the supported local-only Compose stack. V1 provides a separate, explicit `compose.connected.yaml` override. In that profile the portal, API, and PostgreSQL remain on the internal network; only a dedicated broker joins the egress network. Every OpenAI or live-page request requires accepted notice consent, policy opt-in, a server-issued preview showing destination, purpose, minimized field names and outbound content, and one-time confirmation bound to that exact operation, destination, and payload. Return immediately to local-only by stopping the connected profile and restarting `compose.yaml` alone. Do not edit the network or add API keys to the API service as a substitute.

The broker's optional OpenAI request uses `store: false`. That flag does not guarantee zero provider retention. The provider receives the previewed minimized input plus ordinary request metadata, and provider processing remains governed by the provider account and current data controls. For live page retrieval, the broker permits reviewed public HTTPS URLs only, revalidates DNS and every redirect, blocks local/private/link-local destinations, and bounds time, content type, and response size. A compromised broker or host remains capable of disclosure; these controls narrow risk rather than proving non-exfiltration.

## Codex and the host browser

Codex-assisted workflows happen in a separate Codex task. This application's privacy selection cannot change or delete Codex task transcripts, memories, account retention, or service-side processing. Review Codex's [memory controls](https://learn.chatgpt.com/docs/customization/memories) and [permissions](https://learn.chatgpt.com/docs/permissions), and avoid sharing secrets or unnecessary personal information in a task.

Links to job sites, LinkedIn, Overleaf, Google Drive, and similar services open in the host browser. Those services can receive the information you submit to them and are governed by their own terms and privacy controls. The application does not send an outreach message, submit an application, upload a file, or change an external account by itself.

## Your controls

You can:

- review and change the application privacy policy in Profile → Data & privacy;
- preview the count and cutoff for eligible assistant-derived records before manual cleanup;
- run that cleanup and review its local receipt;
- inventory application-owned database rows and configured workspace scopes;
- preview and download a category-level ZIP export;
- preview and delete selected application-owned categories or use the separate delete-all action;
- delete individual records where the relevant application screen offers deletion;
- clear the quick scratchpad in the portal;
- stop the Compose stack and inspect, back up, or remove the selected workspace; and
- keep synthetic demo data in a different workspace from personal data.

Every export or destructive lifecycle action is bound to an exact, expiring preview. Deletion additionally requires typing the displayed `DELETE <rows> ROWS AND <files> FILES` phrase. Delete all preserves the non-personal skill catalogs and the payload-free lifecycle journal. Database deletion is transactional; filesystem deletion follows it and can partially fail. A partial failure requires a fresh preview and confirmation before retrying remaining files. Existing exports and backup-script copies are unencrypted and outside application control, so protect and rotate them yourself. See [Data lifecycle inventory, export, and deletion](docs/DATA_LIFECYCLE_DELETE_DESIGN.md).

## Protecting your local data

Treat the selected workspace as sensitive. Keep it out of source control and unencrypted cloud sharing, restrict access through operating-system permissions, use full-disk encryption, and protect the host account and Docker installation. The `.env`, PostgreSQL directory, resumes, contact exports, and backups deserve particular care.

V1 has no application authentication or authorization. Loopback binding limits network exposure but does not protect against another process or administrator on the same computer. Do not expose the stack through a LAN bind, tunnel, reverse proxy, port-forward, shared host, or public deployment.

For technical boundaries, incident guidance, and vulnerability reporting, see [Security](SECURITY.md). For the detailed storage map, see [Data flow and inventory](docs/DATA_FLOW.md) and [Threat model](docs/THREAT_MODEL.md).
