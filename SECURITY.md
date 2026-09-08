# Security policy and local trust boundary

## Supported use

V1 is a trusted, single-user application for one local Windows, macOS, or Linux workstation. Docker publishes the portal, API, and PostgreSQL only on `127.0.0.1`.

There is no application authentication or authorization layer. Do not expose V1 through a LAN bind, tunnel, reverse proxy, port-forward, shared host, or public deployment. CORS narrows browser origins; it is not authentication and does not protect the API from other processes on the same computer.

## Private data

The local workspace selected during setup is outside the repository and may contain:

- the generated `.env` and database password;
- PostgreSQL files and private backups;
- resumes and preserved job descriptions;
- LinkedIn exports, contacts, outreach notes, and account archives;
- daily opening workbooks, target-company files, preparation resources, and skill plans; and
- scratch notes and runtime logs.

Keep that folder out of source control and unencrypted cloud sharing. Synthetic demo data should use its own local workspace folder so it never becomes mixed with personal records.

## Evidence is not a tamper-resistant audit log

Application stage events, import hashes, immutable application artifacts, assistance decisions, weekly-review revisions, and timestamps provide useful workflow evidence. V1 does not identify an authenticated actor, log every read/edit, or prevent a local administrator from changing files or PostgreSQL directly.

## Reporting a vulnerability

Open a private security advisory in the official GitHub repository when available. Do not include real resumes, credentials, account exports, personal contacts, or database contents in an issue. For non-sensitive defects, use the public issue tracker.

## Operational response

If exposure or credential leakage is suspected, stop the Compose stack, remove any tunnel or broad listener, rotate affected credentials, and restore only from a verified private snapshot. Never restore over the live database merely to diagnose a problem.

Run the release checks in [Development](docs/DEVELOPMENT.md) before distributing a modified build.
