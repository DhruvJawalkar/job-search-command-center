---
id: profile.review-local-data-boundary
page: settings
title: Review the local data boundary
summary: Understand what the repository, workspace, containers, and browser expose.
approx_minutes: 3-5
priority: 40
local_writes: none
external_actions: none
---

# Review the local data boundary

## Steps

1. Identify the checked-out repository and the selected Git-ignored `workspace` folder.
2. Explain that profile data lives in local PostgreSQL while resumes, exports, notes, workbooks, and backups live under the workspace.
3. Confirm the portal, API, and database bind only to `127.0.0.1`.
4. Review `.gitignore`, backup, and cloud-sync considerations relevant to the user’s chosen path.
5. Point to `SECURITY.md` and `docs/DATA_AND_DEMO.md` for the durable contract.

## Done when

- The user knows which data is source-controlled, which is local, and what should not be shared.
