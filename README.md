# Job Search Command Center

A local-first operating system for job discovery, application tracking, resume variants, referrals, market skills, interview preparation, and accountability.

V1 is a local-first, single-user product spanning a durable opportunity-to-application workflow, replay-safe ingestion, contact/referral follow-up, deliberate preparation, market-skill evidence, calendar/interview tracking, and weekly accountability.

## What works now

- Morning dashboard with opportunity, shortlist, pipeline, and weekly-application counts
- Collapsed, five-per-page application follow-up queue with stage/date rescheduling and reminder-only drop controls
- Automatically imports the dated Excel workbooks and companion top-three-action files on API startup
- Idempotent manual sync with validation, content hashing, source provenance, and import-run history
- Filterable high-fit openings feed with daily observations, four fit scores, recommendation, and resume variant
- Opening detail view with role summary, fit rationale, risks, eligibility, score history, and original listing
- Persisted daily priority actions that can be completed and reopened
- Opportunity capture with source, location, work mode, fit decision, score, and rationale
- Resume-variant registry
- Application creation with selected resume variant, required exact submitted PDF, optional preserved job description, current stage, channel, and optional follow-up action
- Immutable local application evidence under `application-resumes/<Company Name>/`, with SHA-256 hashes, collision-safe filenames, and an in-app evidence archive
- Guarded application-stage transitions and an immutable event timeline
- Reusable contact registry with relationship strength and profile metadata
- Referral, introduction, recruiter-message, and follow-up activity linked to openings
- Persisted outreach status progression, due dates, overdue indicators, outcomes, and notes
- Per-opening referral discovery with LinkedIn first-/second-degree and focused recruiter, former-colleague, and alumni search launchers
- Referral-channel map covering former colleagues, alumni, recruiters, communities, and direct channels
- Candidate shortlisting with explainable path strength and one-step conversion into tracked outreach
- Preparation tracks with categories, target dates, milestones, and prioritized actionable items
- One focused daily preparation commitment connected to a real prep item
- Practice-session evidence with duration, result, mistakes, next steps, confidence change, and review date
- Seven-day preparation totals and recent practice history
- Canonical market-skill taxonomy with normalized aliases
- Immutable job-description snapshots with provenance and content-hash replay protection
- Source-backed required/preferred/mentioned skill observations with a Proposed review state
- Explicit accept/reject review before evidence enters the trusted market-skill record
- Safe, manually triggered live job-page capture plus deterministic, replay-safe extraction from immutable full-description snapshots; workbook summaries are excluded from market demand
- Paginated skill review with bulk accept/reject, correction, and evidence-preserving taxonomy merge
- Official LinkedIn Connections.csv import with provenance and per-opening first-degree company matches
- Optional schema-constrained assistance for inbox structuring and weekly-reflection drafts, with exact outbound-content preview, explicit transmission confirmation, replay-safe provenance, selective field application, and separately reviewed skill publication
- PostgreSQL persistence with versioned Flyway migrations
- Demo seeding is opt-in and hidden from the real-data dashboard when present
- Responsive local portal with a safe preview when the API is offline
- Six focused workspaces for executive decisions, opportunities, networking, market skills, preparation, and weekly review
- Database-backed personal calendar with month, week, and day views; interview/meeting details; optional in-app reminders; and an upcoming-commitments summary
- Application-linked interview rounds with preparation notes, debriefs, outcomes and shared calendar scheduling; completing a round never automatically changes application stage
- Floating quick-notes scratchpad on every workspace with automatic browser persistence and explicit timestamped text snapshots in the private local `notes/` folder
- Preparation portfolio with bi-weekly sprint focus, track-level progress, timeline estimates, calendar context, and story/task drill-down

## Stack

- Java 21 bytecode and Spring Boot 4.1
- PostgreSQL 17 and Flyway
- React 19, TypeScript, Vinext/Vite, and CSS
- Docker Compose for local infrastructure

## Start locally

Prerequisites: Docker Desktop, JDK 21 or newer, Maven 3.6.3 or newer, Node 22.13 or newer, and pnpm.

Run these in three terminals from this directory.

### 1. Database

```powershell
docker compose up -d postgres
```

### 2. API

```powershell
Set-Location backend
mvn spring-boot:run
```

The API is available only on the local machine at `http://127.0.0.1:8080`. Health is reported at `http://127.0.0.1:8080/actuator/health`.

### 3. Portal

```powershell
Set-Location frontend
pnpm install
pnpm run dev
```

Open `http://127.0.0.1:3000`. If the development server is unavailable in a restricted shell, use the validated production preview:

```powershell
pnpm run build
pnpm run start
```

## Local-only trust boundary

V1 has no user authentication or authorization layer. The API, portal, and PostgreSQL port therefore bind to loopback by default and must not be exposed through a LAN bind, tunnel, reverse proxy, port-forward, or public deployment. CORS limits browser origins; it is not authentication. Resume files, LinkedIn exports, notes, backups, local plans, credentials, and runtime logs remain private and excluded from Git. See `scripts/RECOVERY.md` before backup or restore operations.

## Daily high-fit import

Place each workbook and optional action file in `daily-high-fit-job-roles` using these names:

```text
YYYY-MM-DD-high-fit-openings.xlsx
YYYY-MM-DD-top-three-actions.txt
```

The API scans this folder at startup. Use **Sync daily files** in the portal to import additions without restarting. Unchanged content is skipped, while an edited daily file is safely reprocessed using its content hash. The workbook must retain the `High-Fit Openings` sheet and its named headers; column order can change.

See [docs/DAILY_HIGH_FIT_ETL.md](docs/DAILY_HIGH_FIT_ETL.md) for the mapping, validation, and recovery behavior.

## Project map

```text
backend/   Spring Boot API, domain model, migration, and tests
frontend/  Morning portal and workflow forms
docs/      Product blueprint, API contract, and development guide
compose.yaml
```

Read [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for validation, data backup, configuration, and implementation notes. The longer product roadmap remains in [docs/PRODUCT_BLUEPRINT.md](docs/PRODUCT_BLUEPRINT.md).

## V1 release status

Backup and restore tooling is available in [scripts/RECOVERY.md](scripts/RECOVERY.md): create a consistent private snapshot, validate its checksums, and rehearse recovery in a separate PostgreSQL container without overwriting live data.

Milestones 3A–3D, 4A–4D, 5A, and 5D are implemented, together with later daily-use improvements. Closure gates C1–C6 are complete, and V1 is accepted for trusted, single-user, localhost use. The next product phase is the governed-agent foundation, not public hosting of the current unauthenticated runtime.

The local [V1 closure checklist](docs/V1_CLOSURE_CHECKLIST.md), [C6 release record](docs/C6_RELEASE_READINESS.md), and [project status](docs/PROJECT_STATUS.md) preserve the decision, evidence and residual risks. The [governed agent platform proposal](docs/GOVERNED_AGENT_PLATFORM_PROPOSAL.md) sequences agent foundation, scout pilot, reviewed drafts, controlled execution and evaluation-driven improvement. The first agent demo must reject original-resume overwrite, unapproved submission, stale approval and revoked access, and recover safely after restart.

Planning documents under `docs/` are intentionally local-only under the existing Git ignore policy. Include them in private backups; a normal commit/push does not preserve them remotely.
