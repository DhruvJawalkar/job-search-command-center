# Your local Job Search Command Center workspace

This folder holds the private data for one local installation. Keep it out of source control and cloud sharing unless you have intentionally configured an encrypted private backup.

## Folder map

- `postgres-data/` — application database files; do not edit manually.
- `application-resumes/` — immutable PDFs and job descriptions preserved with applications.
- `daily-high-fit-job-roles/` — dated opening workbooks and optional top-three action files.
- `linkedin-data-import/` — optional local LinkedIn `Connections.csv` import.
- `notes/` — explicit scratchpad snapshots.
- `preparation-workspace/` — local preparation resources.
- `company-targets/` — optional target-company workbook.
- `backups/` — private recovery snapshots.
- `.env` — generated local configuration and database password; never share it.

The app listens only on `127.0.0.1`. Do not expose its ports through a tunnel, LAN binding, reverse proxy, or public host.

To start, stop, or update the installation, use the setup and Compose instructions in the downloaded source repository while retaining this folder as the selected local workspace. Rerunning setup preserves this folder; use a different folder when switching between demo and personal data.

The advanced PowerShell recovery rehearsal under `scripts/` was preserved from V1 closure and assumes a source-layout workspace. It is not yet a one-command backup for this external Docker data folder. Until a Docker-workspace backup command is added, use a reviewed PostgreSQL dump plus an encrypted copy of the non-`postgres-data` folders; never copy live PostgreSQL data files as a database backup.
