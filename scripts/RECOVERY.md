# Local backup and isolated restore

These scripts provide an on-demand, single-user maintenance backup. They never restore over the live database. Docker Desktop, PowerShell 7, JDK 21 and a packaged backend executable are required. Run from the project root. No scheduler, remote upload or automatic deletion is configured.

## Scope and privacy

- Includes a binary-safe PostgreSQL custom dump, logical row fingerprints, every database-linked application artifact, other files in the local resume directory, saved notes, preparation resources, research/import files, local plans, source tree and packaged backend.
- Records SHA-256 for every included file plus a manifest checksum. These detect accidental corruption, not malicious replacement of the manifest and checksum together. Database row digests are logical equality checks, not cryptographic attestations.
- Keeps outputs inside ignored `backups/`. Backups contain personal data, are **unencrypted**, and may inherit this project's OneDrive synchronization. Do not commit, attach, or publish them. Same-drive copies are not protection against disk loss; arrange an encrypted off-device copy separately.
- Excludes credentials/environment secrets, browser localStorage, external accounts (including Overleaf), Git history, caches, previous backups, generated output and logs. Save scratchpad drafts to disk first. Keep secrets in a separate secure store.
- Designed for the repository-default storage roots. If artifact/notes/source folders have been configured outside this project, stop and extend/review the backup scope first. Runtime credentials and environment overrides must be supplied separately during real recovery.

## 1. Prepare and snapshot

1. Finish/suspend app edits and any direct database/file writers. Build the current backend: `mvn -DskipTests package` from `backend/` (run tests separately before release).
2. Stop the API in its terminal with Ctrl+C; leave PostgreSQL running. The backup command refuses an occupied API port or other database clients. Do not edit project files until it finishes.
3. Run:

```powershell
./scripts/Backup-Workspace.ps1 -MaintenanceConfirmed -SourceRevision '<commit plus working-tree description>'
```

The command creates a unique `backups/<timestamp>-<id>/`. It takes fingerprints before/after and checks source/copy hashes, so concurrent changes fail the backup rather than produce a COMPLETE manifest. Only COMPLETE, checksum-valid snapshots are usable. Failures remain for inspection; do not promote them by hand. A uniquely named staging dump is retained in the source container's `/tmp/`; no automatic deletion is performed.

4. Restart the normal API using the original configuration. For a controlled recovery check, disable startup imports for that launch (`APP_DAILY_HIGH_FIT_IMPORT_ENABLED=false`) so restart does not change the snapshot baseline. The manual Sync action remains available. Do not keep maintenance overrides in shared shell configuration.

The snapshot includes source hashes and a packaged runtime to preserve uncommitted work without altering Git. New application writes can resume once the backup command finishes.

## 2. Verify and rehearse

```powershell
./scripts/Test-RecoverySafety.ps1
./scripts/Test-BackupRestore.ps1 -BackupFolder 'backups/<snapshot>' -JavaExecutable '<absolute JDK path>/bin/java.exe' -VerifyOnly
./scripts/Test-BackupRestore.ps1 -BackupFolder 'backups/<snapshot>' -JavaExecutable '<absolute JDK path>/bin/java.exe'
```

The rehearsal:

- Verifies checksums before starting a container; rejects incomplete manifests and paths escaping the backup root.
- Creates a new PostgreSQL container/volume using the captured image, with a generated password and a loopback-only port (default 55432). It never connects to the live database.
- Copies files into a new isolated directory and restores with `pg_restore --exit-on-error --single-transaction`, without `--clean`.
- Compares all public tables and sequence positions with the saved logical baseline, and checks artifact sizes/hashes against database metadata.
- Starts the captured API runtime on loopback port 8081 with every storage root redirected to the restored workspace; imports, demo seeds and AI calls are disabled. Inherited app/Spring/JVM configuration is removed for the child process and restored in the caller afterward.
- Validates Flyway/Hibernate schema compatibility and reads applications, opportunities, contacts, outreach, preparation, skills, calendar and weekly-review endpoints.
- Stops its own API, restarts the isolated database, then repeats startup, API reads and equality checks.
- Stops the isolated API/container in a finally block and writes a private `verification.json`. PASS requires both startups and equality checks. Containers and restored files are retained, including failed attempts.

Use distinct `-DatabasePort` and `-ApiPort` values if defaults are occupied; production ports are refused. Scripts do not indiscriminately terminate Java processes. Windows launcher working directories are kept short while all restored storage paths remain absolute.

## 3. Real recovery, not just rehearsal

1. Do not delete or overwrite the damaged/live store. Stop its writers and preserve it for investigation.
2. Select a checksum-valid COMPLETE backup and run the isolated rehearsal with matching PostgreSQL/JDK/runtime versions. If a step fails, investigate before trying an older valid snapshot.
3. Inspect the PASS report, counts, application files and sprint state. Missing historical data cannot be reconstructed by this procedure; zero-row tables are explicitly preserved as zero.
4. Promotion is a separate deliberate operation: retain the new database/volume and restored files, configure fresh credentials and normal app settings pointing to that recovered database and artifact roots, then start the app on the intended local port. Do not copy records back into an unknown live database or enable startup ingestion until verification is complete.
5. Recheck application/artifact relationships, calendar/sprint/history, user workflows and backups. Securely restore external credentials separately. Keep the prior store until the recovery is accepted.

Rehearsal scripts intentionally do not perform promotion, password recovery, remote hosting or destructive cleanup. Before later removing any retained clone, resolve its exact container/volume and filesystem paths, confirm the recovery label/report, and obtain permission. `docker compose down -v` is not a recovery step.

## Limits

The first rehearsal checks real stored data. Empty saved-note or weekly-review stores cannot demonstrate recovery of nonexistent records. Logical hashes detect row changes independent of row order; they do not replace application tests, full disaster recovery, or a separate secure backup destination. Public-schema fingerprints assume this application's current modest data volume; review the aggregation approach before very large datasets or additional schemas are introduced.

## Release checks

Run these from the project root at a local release boundary:

```powershell
./scripts/Test-ReleaseGate.ps1
./scripts/Test-FreshSetup.ps1
```

The release gate verifies loopback source defaults and live listeners, private-path ignore coverage, absence of ignored tracked files, a high-confidence tracked credential scan, repository whitespace and live portal/API health. The fresh-setup check creates a uniquely named and labeled disposable PostgreSQL 17 container, packages the backend, starts it on a separate loopback port with demo/import behavior disabled, verifies all migrations and an empty initial store, then removes that exact container. A JDK 21 or newer must be available through `JAVA_HOME` or another discovered JDK location; an older `java.exe` earlier on `PATH` is not sufficient.

Reports are written under ignored `tmp/c6-*` folders. These checks complement rather than replace the full backend/frontend suites and the backup/restore rehearsal. They do not authorize a hosted or multi-user deployment.
