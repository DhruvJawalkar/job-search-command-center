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

To start, stop, inspect, or repull the installed release, use `.jscc/jscc.ps1` on Windows or `.jscc/jscc.sh` on macOS/Linux. Rerunning the verified setup preserves this folder; use a different folder when switching between demo and personal data.

For a backup, stop the application and make a protected copy of the complete workspace, including `.env`, `.jscc`, `postgres-data`, and the user-file folders. The copy is not encrypted by the application. Never copy live PostgreSQL data files while the database container is running, and test important restores in a separate workspace.
