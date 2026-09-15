---
id: opportunities.import-daily-files
page: opportunities
title: Import daily files
summary: Validate and import a high-fit workbook and optional top-three-actions file.
approx_minutes: 5-8
priority: 30
local_writes: imported opportunities, observations, actions, and an import receipt
external_actions: none
---

# Import daily files

## Before starting

- Confirm the files are in the workspace `daily-high-fit-job-roles` folder and follow `docs/DAILY_HIGH_FIT_ETL.md`.

## Steps

1. Identify the dated workbook and optional matching actions file; do not silently select an unexpected date.
2. Review that links and content are appropriate before import.
3. Use **Sync daily files** and inspect the import receipt for created, unchanged, or failed files.
4. Verify the imported count and confirm records appear in the openings view.
5. On the user’s first successful import, optionally offer to open **Customize Summary** and adjust the default weekly application goal of 10 based on the quality and volume of relevant openings. Do not require this to complete the import.
6. If validation fails, explain the exact contract mismatch and preserve the source file for correction.

## Done when

- The import receipt is successful and the expected openings are visible.
