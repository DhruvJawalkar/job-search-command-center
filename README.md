# Job Search Command Center

A local-first command center for turning job-search activity into a deliberate operating system: discover high-fit roles, preserve evidence, manage applications and referral paths, prepare for interviews, and review progress.

V1 is a complete, Docker-packaged, single-user release. It runs on your computer, starts empty by default, and offers an explicit synthetic-demo option.

## Start in 5–10 minutes

Requirements: Docker Desktop on Windows or macOS, or Docker Engine with Compose v2 on Linux. You do not need to install Java, Maven, Node.js, pnpm, or PostgreSQL.

Windows PowerShell:

```powershell
./setup.ps1
```

macOS or Linux:

```bash
./setup.sh
```

The guided script explains the setup, proposes the Git-ignored `workspace` folder inside the checked-out repository for user-specific data, offers empty or synthetic-demo data, creates the expected folders and a generated database password, builds the containers, and waits for the app to become healthy. You can enter another workspace path when prompted. Open `http://127.0.0.1:3000` when it finishes.

The Windows helper exists because Docker Compose alone does not choose a safe data location, create the local folder contract, generate secrets, explain the demo choice, check prerequisites, or wait for a healthy first start. It performs the same guided orchestration as `setup.sh`; the application itself remains containerized.

See [Installation](docs/INSTALLATION.md) for non-interactive setup and troubleshooting.

## The first useful outcome

Do not configure the entire system before getting value from it:

1. Save a small local profile with your target role, level, location, work style, company preferences, career direction, values, and technology inclusions/exclusions.
2. Add or import one real opening.
3. Review the evidence and choose one next action: shortlist, seek a referral, apply, or skip.

After that, use the broader operating loop:

- import a daily high-fit workbook and optional top-three-actions file;
- compare ranked opportunities and preserve observation history;
- register resume variants and retain the exact submitted resume and job description;
- track application stages, follow-ups, interviews, and calendar commitments;
- map contacts, referral candidates, outreach, and responses;
- build preparation tracks, sprints, practice evidence, and skill plans; and
- freeze append-only weekly review snapshots.

The profile is stored in the local PostgreSQL database, not only in a Codex conversation and not in a hosted profile service.

An import-ready fictional example with ten ranked openings and its companion top-three-actions file is available in [Sample daily files](samples/daily-high-fit-job-roles/README.md). The installer does not copy these into an empty-mode workspace automatically.

## Empty and demo modes

Empty mode is the recommended personal starting point. Demo mode is opt-in and contains only synthetic people, companies, links, and activity. The app displays a persistent demo banner when it is enabled.

To compare both experiences, use two different local workspace folders. This keeps synthetic records and personal data in separate PostgreSQL data directories.

## Codex-assisted workflows in V1

V1 can be used alongside Codex for guided, user-controlled workflows such as researching high-fit openings, generating a three-opening example workbook, opening the local app in a browser tab, tailoring a resume in a service the user controls, and drafting outreach. Codex assistance does not turn V1 into an autonomous application or outreach agent; users review and authorize consequential actions in the relevant service.

The proposed daily discovery task and the under-one-minute example workflow are in [Codex onboarding](docs/CODEX_ONBOARDING.md). The product walk-through and short demo-video set are in [Demo guide](docs/DEMO_GUIDE.md).

## Local trust boundary

V1 has no application authentication or authorization. The portal, API, and PostgreSQL port bind to `127.0.0.1` and are intended for one trusted user on one local machine. Do not expose them through a LAN bind, tunnel, reverse proxy, port-forward, or public host.

The selected local workspace contains the generated `.env`, PostgreSQL data, resumes, job descriptions, LinkedIn exports, notes, workbooks, and backups. Keep it out of source control and cloud sharing unless you deliberately use an encrypted private backup. See [Security](SECURITY.md).

## Repository map

```text
backend/       Spring Boot API, Flyway migrations, and tests
frontend/      React/TypeScript local portal
docs/          installation, onboarding, data, demo, and development guides
scripts/       validation and recovery helpers
compose.yaml   loopback-only PostgreSQL, API, and portal stack
setup.ps1      guided Windows setup
setup.sh       guided macOS/Linux setup
```

## Development and release evidence

The accepted private V1 was reconstructed as the first auditable commit in this public history. [V1 reconstruction](docs/V1_RECONSTRUCTION.md) records its scope. The complete Docker-packaged release follows in later commits. The final manually accepted release commit will be tagged `v1.0.0`.

Build and validation commands are in [Development](docs/DEVELOPMENT.md). The completed V1 gates are recorded in [Release evidence](docs/V1_RELEASE_EVIDENCE.md). The daily workbook contract is in [Daily high-fit ETL](docs/DAILY_HIGH_FIT_ETL.md).

## License and project identity

Job Search Command Center is **source-available for non-commercial use** under the [PolyForm Noncommercial License 1.0.0](LICENSE). Copyright © 2026 Dhruv Jawalkar.

Creator attribution and the required notice must be retained. Modified versions and forks must say that they are modified, must not claim to be official or endorsed, and should use a distinct name and visual identity where confusion is reasonably likely. Commercial use requires a separate written agreement.

Read [NOTICE](NOTICE), [Names and branding](TRADEMARKS.md), and [Commercial licensing](COMMERCIAL-LICENSE.md) before redistributing or modifying the project.

## Beyond V1

V1 demonstrates disciplined local workflows and Codex-assisted execution. V2 will add within the next two weeks a governed agent platform with scoped roles, explicit approvals, auditable tool use, revocation, recovery, and evaluation-driven controls. See [V2 teaser](docs/V2_TEASER.md); those capabilities are not part of V1.
