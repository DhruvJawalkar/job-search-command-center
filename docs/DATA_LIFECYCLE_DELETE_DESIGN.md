# Data lifecycle inventory, export, and deletion

**Status:** Implemented; automated integrated acceptance passed and manual UI review remains pending.
**Scope:** Application-owned data in the local Job Search Command Center workspace only.

## Inventory contract

`GET /api/v1/privacy-policy/data-inventory` counts every application-owned database table and each configured workspace scope without reading file contents. Table names come from a fixed server allowlist; no request value is interpolated into SQL. The current inventory covers 46 tables after migrations V32 and V33.

The inventory separates deliberately saved records, sensitive workspace files, transient import/review data, assistant-derived context, audit/policy metadata, and application reference data. `transmission_preview` and `transmission_receipt` are audit/policy metadata. `data_lifecycle_operation` is a payload-free accountability journal that is always retained. `canonical_skill` and `skill_alias` are non-personal reference catalogs and are not deletable.

Filesystem inventory resolves the configured workspace and target paths canonically, rejects targets outside the workspace, does not follow symbolic links, and reports unreadable or unsafe entries rather than traversing them. Missing paths are reported as missing, not created. The API cannot traverse the unmounted `.env`, PostgreSQL data directory, or backup folders.

## Preview and authorization contract

Every export or destructive action begins with `POST /api/v1/privacy-policy/data-lifecycle/preview` and the `X-JSCC-Action: preview-data-lifecycle` header. The server returns:

- an immutable operation UUID used as the preview revision;
- requested and effective categories;
- exact database-row, regular-file, and byte counts;
- fixed table and logical workspace-scope names, never absolute filesystem paths;
- retained application scopes and outside-control boundaries;
- a random, ten-minute, single-preview token (only its SHA-256 hash is persisted); and
- for deletion, the exact phrase `DELETE <rows> ROWS AND <files> FILES`.

Execution requires the operation UUID, raw preview token, a purpose-specific `X-JSCC-Action` header, and—for deletion—the exact typed phrase. The server recomputes the plan before execution. Added or removed rows, or a changed file path/size/modified time, invalidates the preview.

The Profile UI defaults export to deliberately saved records plus sensitive workspace files. Destructive category selection starts empty. Delete all is a separate danger action and always expands to every deletable category; it ignores a client attempt to narrow that fixed server-owned scope.

## Export

`POST /api/v1/privacy-policy/data-lifecycle/export` creates a local ZIP containing a manifest, JSON rows for the selected fixed table scopes, and the selected regular workspace files. The export itself is sensitive and is not encrypted by the application. Browser download history, copied exports, synchronized folders, and user-created backups are outside application deletion controls.

Workspace files are opened relative to no-follow secure directory handles held from the filesystem root through the planned file, and their file identity, size, and modified time are checked before and after the read. A Java filesystem provider that cannot supply `SecureDirectoryStream` fails closed for workspace-file export rather than falling back to a replaceable path lookup. Database-only exports remain available on that runtime.

The operation journal records only categories, counts, status, timestamps, hashes, and exported byte count. It does not store exported payloads, absolute paths, or a usable confirmation token.

## Category deletion and delete all

`POST /api/v1/privacy-policy/data-lifecycle/delete` executes the authorized fixed plan. Category deletion of deliberately saved records also includes dependent transient import/review data so hidden foreign-key cascades cannot remove uncounted rows.

Database deletion is child-first and transactional. The order includes transmission receipts before transmission previews, assistance decisions before runs, import/review children before their parents, application events/artifacts/rounds before applications and opportunities, outreach/referrals before contacts, preparation children before tracks, and review snapshots/revisions before reviews. The transaction rolls back if the affected-row count differs from the preview.

Transient-only deletion removes only job-description snapshots that are not referenced by an explicit `job_skill_observation`. A referenced snapshot is preserved with its deliberately reviewed skill evidence. Delete all, which explicitly includes both categories, deletes observations before their snapshots.

Only after the database transaction succeeds does the service delete planned regular files. At deletion time, every file is resolved again and must still:

- have the same canonical path captured by the preview;
- remain beneath the original canonical workspace root;
- not be a symbolic link;
- be a regular file; and
- match the previewed size and modified time.

Filesystem deletion cannot share the database transaction. A partial file failure therefore leaves the completed database deletion intact and records only deleted/failed counts. The old preview token cannot be reused: retrying remaining files requires a fresh inventory preview, fresh token, and newly typed exact phrase. This prevents an old authorization from deleting files created after the original preview.

Successful and replayed delete calls return the same payload-free receipt. Application reference catalogs and the lifecycle journal are preserved by delete all.

## What the controls cannot remove

- Codex task transcripts, Codex memories, or account-level data.
- Provider-side copies created after an opted-in external transmission.
- Browser history, downloads, cookies, local storage, or external-account activity.
- User-created exports, screenshots, copied or synchronized folders, and unmounted backups.
- Operating-system, Docker, registry, or connected-service telemetry.
- Physical remnants in SSD blocks, PostgreSQL storage pages, filesystem snapshots, or external backup media.

Logical deletion is not secure physical erasure. Final integrated backend/frontend and clean-install acceptance remains a release gate; this document describes the implemented contract, not evidence that every supported host has completed that gate.
