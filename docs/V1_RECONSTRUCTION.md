# V1 reconstruction record

Date: 2026-09-08

This repository begins with a clean-history reconstruction of the accepted local V1 product. The source product was developed in a private repository. No Git history, personal data, ignored runtime files, credentials, resumes, account exports, notes, backups, or machine-specific acceptance output was copied here.

## Accepted product boundary

The reconstruction retains the application and closure work through C1-C6, including:

- the opportunity, application, resume-evidence, contact, outreach, market-skill, preparation, calendar, interview-round, and weekly-review workflows;
- immutable application artifacts and event history;
- replay-safe ingestion and duplicate review;
- backup, isolated restore, regression, accessibility, performance, fresh-install, and release-gate tooling; and
- loopback-only defaults for the unauthenticated single-user application.

The reconstructed database history ends at `V26__application_interview_rounds.sql`.

## Deliberate exclusions

Post-V1 governed-agent foundation work is excluded. The reconstruction contains no:

- governed-agent role contracts;
- agent-role registry package or API;
- agent-role database migration;
- agent-role configuration or startup synchronization; or
- AF1/AF2 implementation claims or tests.

## Initial validation

Before the clean-history repository was initialized:

- the backend test suite passed;
- frontend lint passed;
- the frontend production build passed; and
- the rendered-shell test passed.

Fresh PostgreSQL, Docker packaging, installer, onboarding, demo-data, documentation, and final release gates are validated separately before the `v1.0.0` tag.
