# Daily high-fit workbook contract

Place files in the selected local workspace’s `daily-high-fit-job-roles` folder.

## Filenames

```text
YYYY-MM-DD-high-fit-openings.xlsx
YYYY-MM-DD-top-three-actions.txt
```

The action file is optional. The workbook date is the observation date. The importer preserves source metadata and a content hash; an identical file is replay-safe, while changed content is imported as new evidence.

## Sample files

The repository includes an import-ready fictional example with ten listings at [Sample daily files](../samples/daily-high-fit-job-roles/README.md). Copy both dated files into your local workspace’s `daily-high-fit-job-roles` folder, then select **Sync daily files** in Opportunities. The setup scripts do not copy the sample automatically, so empty mode remains empty.

## Workbook

The workbook must contain a sheet named `High-Fit Openings` and these exact named headers. Column order may change.

```text
Rank
Company
Exact Title
Location / Work Arrangement
Posting Date
Overall Fit
Recruiter-Screen Strength
Technical Scope
Growth Potential
Weighted Total
Recommendation
Role Summary
Fit Rationale
Key Risks / Gaps
Direct Job Link
Recommended Resume Variant
Authorization / Eligibility
Verified Date
```

Use one opening per row. Scores are numeric from 0 to 10. `Rank`, company, exact title, location/work arrangement, the five scores, recommendation, role summary, fit rationale, direct link, and verified date are required. Use direct, verified job links and concise evidence-based rationale. Do not invent authorization eligibility.

## Top-three-actions text file

Use one non-empty action per line, in priority order. Keep it to three lines. The application imports the actions for the same date and preserves their completion state.

## Import and recovery

The API scans the folder at startup. Use **Sync daily files** in Opportunities to import without restarting. A failed file remains visible in import history with an error; fix the source and sync again. Do not rename a bad workbook to conceal an error because its provenance is part of the local record.
