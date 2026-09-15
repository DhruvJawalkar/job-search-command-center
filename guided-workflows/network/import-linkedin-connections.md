---
id: network.import-linkedin-connections
page: outreach
title: Import LinkedIn connections
summary: Load a reviewed LinkedIn connections export into the local contact index.
approx_minutes: 5-10
priority: 40
local_writes: local contact records and an import receipt
external_actions: none
---

# Import LinkedIn connections

## Before starting

- Explain that the export contains personal data and should remain in the Git-ignored local workspace.

## Steps

1. Confirm the file is the user’s own connections CSV and inspect its headers before import.
2. Place it in the documented workspace import folder without copying it into tracked repository paths.
3. Run the app’s import action and review created, updated, skipped, or failed counts.
4. Spot-check several contacts for name, company, role, and connection date accuracy.
5. Keep or remove the source export according to the user’s local retention preference.

## Done when

- The local contact index reflects the reviewed export and the source file remains outside version control.
