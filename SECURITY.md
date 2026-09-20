# Security policy and local trust boundary

## Supported use

V1 is a trusted, single-user application for one local Windows, macOS, or Linux workstation. Docker publishes only a fixed-route gateway on `127.0.0.1:3000` and `127.0.0.1:8080`; the portal, API, and PostgreSQL have no direct host ports. Those three data-processing services use only the internal `local_only` network and have no ordinary outbound route.

There is no application authentication or authorization layer. Do not expose V1 through a LAN bind, tunnel, reverse proxy, port-forward, shared host, or public deployment. CORS narrows browser origins; it is not authentication and does not protect the API from other processes on the same computer.

The gateway is the sole dual-network service. It has fixed routes only to the portal and API, no workspace or secret mounts, a read-only root filesystem, dropped capabilities, and `no-new-privileges`. Its ingress-network attachment has ordinary outbound reachability, however, so it is a narrow ingress boundary rather than proof of universal non-exfiltration. The host browser, Codex, Docker Desktop or Engine, the operating system, and other local processes are also outside the internal data-processing network. Passing the local-only acceptance check is evidence for the tested Compose configuration only.

## Private data

The local workspace selected during setup is separate from tracked source. The default `workspace/` is inside the checkout but Git-ignored; a custom path may be elsewhere. It may contain:

- the generated `.env` and database password;
- PostgreSQL files and private backups;
- resumes and preserved job descriptions;
- LinkedIn exports, contacts, outreach notes, and account archives;
- daily opening workbooks, target-company files, preparation resources, and skill plans; and
- scratch notes and runtime logs.

Keep that folder out of source control and unencrypted cloud sharing. Synthetic demo data should use its own local workspace folder so it never becomes mixed with personal records.

The portal also keeps the unsaved quick scratchpad and dismissed calendar-notice identifiers in host-browser local storage. Application database cleanup does not clear that browser storage. Existing backup scripts create local, unencrypted copies; protect, rotate, and delete those separately.

## Privacy and retention controls

The local privacy policy distinguishes records the user deliberately saves from application-owned assistant-derived context. Profile → Data & privacy can preview and manually remove eligible `assistance_run` and `assistance_decision` rows without deleting saved openings, applications, contacts, outreach, preparation, reviews, or workspace files.

Stateless assistance produces no durable run row and uses a signed response-carried artifact only when the user deliberately saves reviewed output. Session-only assistance uses bounded backend memory; Time-bound policies persist derived context and run cleanup at startup and when daily due. The API has no outbound route in the default local-only topology. Optional OpenAI and live-page operations use the explicit connected override, a dedicated authenticated broker, policy opt-in, and a one-time confirmation token bound to the previewed payload and destination. See [Privacy](PRIVACY.md), [Connected runtime](docs/CONNECTED_RUNTIME.md), [Data flow](docs/DATA_FLOW.md), and [Threat model](docs/THREAT_MODEL.md).

Deletion is not guaranteed physical erasure. Copies in browser storage, backups, synchronized folders, screenshots, exports, Codex tasks, provider systems, or storage snapshots have separate lifecycles.

## Host hardening

Use an encrypted disk, a protected operating-system account, current Docker and browser releases, and restrictive filesystem permissions for the selected workspace. Do not store provider keys in tracked files or publish `.env`, logs, backups, database directories, resumes, or contact exports. Import files only from sources you trust.

## Evidence is not a tamper-resistant audit log

Application stage events, import hashes, immutable application artifacts, assistance decisions, weekly-review revisions, and timestamps provide useful workflow evidence. V1 does not identify an authenticated actor, log every read/edit, or prevent a local administrator from changing files or PostgreSQL directly.

## Reporting a vulnerability

Open a private security advisory in the official GitHub repository when available. Do not include real resumes, credentials, account exports, personal contacts, or database contents in an issue. For non-sensitive defects, use the public issue tracker.

## Operational response

If exposure or credential leakage is suspected, stop the Compose stack, remove any tunnel or broad listener, rotate affected credentials, and restore only from a verified private snapshot. Never restore over the live database merely to diagnose a problem.

Modified builds belong on the [`source` branch](https://github.com/DhruvJawalkar/job-search-command-center/tree/source) and must pass its release gates before distribution. The protected workflow design, approval boundary, severity gates, and digest-based verification commands are in [Container release and verification](docs/CONTAINER_RELEASE.md). Treat signing, SBOM/provenance, vulnerability scans, and runtime checks as evidence only for the exact published artifacts they identify.
