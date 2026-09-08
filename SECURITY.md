# Security boundary

## Supported use

V1 is a trusted, single-user application for one local Windows workstation. The portal, API, and PostgreSQL port bind to `127.0.0.1` by default. The product is not approved for LAN access, public hosting, shared accounts, or multi-user use.

There is no application authentication or authorization layer in V1. CORS narrows browser origins but is not authentication and does not protect the API from other local processes.

## Private data

Keep these outside Git and include them only in private, access-controlled backups:

- `.env*` credentials and provider tokens;
- application resumes and job-description evidence;
- LinkedIn exports, contact/outreach notes, and account archives;
- daily opening workbooks, target-company files, preparation resources, and personal skill plans;
- scratch notes, PostgreSQL dumps, backup manifests, runtime logs, browser state, and future agent logs.

The repository contains `.env.example` with local development placeholders only. Replace defaults through ignored local environment state if the machine is shared or the trust boundary changes.

## Existing evidence versus an audit log

Application stage events, import batches and hashes, immutable application artifacts, assistance runs/decisions, weekly-review revisions, and record timestamps provide useful workflow evidence. They are not a complete or tamper-resistant audit trail: V1 does not identify an authenticated actor, log every read/edit, or prevent a local administrator from changing PostgreSQL or files directly.

## Operational response

If exposure or credential leakage is suspected, stop the portal/API, remove any tunnel or broad listener, rotate affected credentials at their provider, inspect recent application/import/assistance evidence, and restore only from a verified private snapshot when necessary. Follow `scripts/RECOVERY.md`; never restore over the live database as a diagnostic experiment.

Run `scripts/Test-ReleaseGate.ps1` before a V1 release or material runtime change. Run `scripts/Test-FreshSetup.ps1` when validating a clean installation against disposable infrastructure.
