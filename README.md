# Job Search Command Center

A local-first command center for turning job-search activity into a deliberate operating system: discover high-fit roles, preserve evidence, manage applications and referral paths, prepare for interviews, and review progress.

V1 is a complete, Docker-packaged, single-user release. It runs on your computer, starts empty by default, and offers an explicit synthetic-demo option.

## Connect the clone to Codex

For the Codex-assisted experience, open **Projects** in the Codex desktop app, create a local project named **Job Search Command Center**, add the cloned `job-search-command-center` repository folder, and make it the primary folder. Start a new chat inside that project before running setup.

Attaching the repository gives the project chat access to the application files and checked-in guidance. After setup finishes, return to that chat, open a **Browser** tab in the right-side panel, and enter `http://127.0.0.1:3000`. Ask **“Help me on this page.”** Codex can use the open portal tab and the repository’s guided-workflow catalog to offer the three most relevant outcomes for that page. This Codex project is recommended but is not required to run the Docker application.

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

## Ask for a guided workflow

There is no mandatory onboarding sequence. Open any page and ask **“Help me on this page.”** Codex reads the matching page index under [`guided-workflows/`](guided-workflows/README.md), considers the visible state, and offers no more than three short workflows with outcomes and time estimates. Choose one, or state the outcome directly—for example, “Help me add one opening” or “Help me find a trusted contact for this role.”

Useful starting outcomes include adding one real opening, understanding the Summary, setting search preferences, finding a referral path, or creating a preparation track. Codex loads only the selected playbook and guides it step by step; the application does not force a redirect or a fixed tour.

The broader operating loop can:

- import a daily high-fit workbook and optional top-three-actions file;
- compare ranked opportunities and preserve observation history;
- register resume variants and retain the exact submitted resume and job description;
- track application stages, follow-ups, interviews, and calendar commitments;
- map contacts, referral candidates, outreach, and responses;
- build preparation tracks, sprints, practice evidence, and skill plans;
- personalize the Summary’s recommendation emphasis, insight cards, weekly application goal, top-three priorities, specialization, and daily schedule; and
- freeze append-only weekly review snapshots.

The profile and Summary preferences are stored in the local PostgreSQL database, not only in a Codex conversation and not in a hosted profile service.

An import-ready fictional example with ten ranked openings and its companion top-three-actions file is available in [Sample daily files](samples/daily-high-fit-job-roles/README.md). The installer does not copy these into an empty-mode workspace automatically.

## Empty and demo modes

Empty mode is the recommended personal starting point. Demo mode is opt-in and contains only synthetic people, companies, links, and activity. The app displays a persistent demo banner when it is enabled.

To compare both experiences, use two different local workspace folders. This keeps synthetic records and personal data in separate PostgreSQL data directories.

## Codex-assisted workflows in V1

V1 includes a repository-native [guided-workflow catalog](guided-workflows/README.md) for page-aware, user-controlled assistance. It covers opportunity intake, referral paths, local personalization, preparation, weekly review, and related tasks such as generating a three-opening example workbook. Codex assistance does not turn V1 into an autonomous application or outreach agent; users review and authorize consequential actions in the relevant service.

The proposed daily discovery task and the under-one-minute example workflow are in [Codex onboarding](docs/CODEX_ONBOARDING.md). The product walk-through and short demo-video set are in [Demo guide](docs/DEMO_GUIDE.md).

## Local trust boundary

V1 has no application authentication or authorization. A hardened fixed-route gateway publishes the portal and API endpoints only on `127.0.0.1`; the portal, API, and PostgreSQL have no direct host ports and use an internal network with no ordinary outbound route. The gateway is a separately disclosed ingress boundary, not proof of universal non-exfiltration. The stack is intended for one trusted user on one local machine. Do not expose it through a LAN bind, tunnel, reverse proxy, port-forward, or public host.

The selected local workspace contains the generated `.env`, PostgreSQL data, resumes, job descriptions, LinkedIn exports, notes, workbooks, and backups. Keep it out of source control and cloud sharing unless you deliberately use an encrypted private backup. Optional provider assistance and live public job-page retrieval require the separate [reviewed connected runtime](docs/CONNECTED_RUNTIME.md), policy opt-in, and confirmation of a destination/purpose/data preview for each request. Application privacy settings do not control Codex task history, the host browser, or external services. Read [Privacy](PRIVACY.md), [Security](SECURITY.md), and the [Threat model](docs/THREAT_MODEL.md) before adding real personal or third-party data.

## Repository map

```text
backend/       Spring Boot API, Flyway migrations, and tests
frontend/      React/TypeScript local portal
gateway/       fixed-route loopback ingress proxy
egress-broker/ authenticated, narrow broker used only by the connected override
docs/          installation, onboarding, data, demo, and development guides
guided-workflows/ page indexes and step-by-step Codex-assisted playbooks
scripts/       validation and recovery helpers
compose.yaml   local-only stack: loopback gateway, isolated data-processing services
compose.connected.yaml explicit opt-in overlay for reviewed connected operations
setup.ps1      guided Windows setup
setup.sh       guided macOS/Linux setup
```

## Development and release evidence

The accepted private V1 was reconstructed as the first auditable commit in this public history. [V1 reconstruction](docs/V1_RECONSTRUCTION.md) records its scope. The complete Docker-packaged release follows in later commits. The final manually accepted release commit will be tagged `v1.0.0`.

Build and validation commands are in [Development](docs/DEVELOPMENT.md). The completed V1 gates are recorded in [Release evidence](docs/V1_RELEASE_EVIDENCE.md). The prepared, protected container workflow and post-release verification steps are described in [Container release and verification](docs/CONTAINER_RELEASE.md); that source file is not evidence that a release workflow has run. The daily workbook contract is in [Daily high-fit ETL](docs/DAILY_HIGH_FIT_ETL.md).

## License and project identity

Job Search Command Center is **source-available for non-commercial use** under the [PolyForm Noncommercial License 1.0.0](LICENSE). Copyright © 2026 Dhruv Jawalkar.

Creator attribution and the required notice must be retained. Modified versions and forks must say that they are modified, must not claim to be official or endorsed, and should use a distinct name and visual identity where confusion is reasonably likely. Commercial use requires a separate written agreement.

Read [NOTICE](NOTICE), [Names and branding](TRADEMARKS.md), and [Commercial licensing](COMMERCIAL-LICENSE.md) before redistributing or modifying the project.

## Beyond V1

V1 demonstrates disciplined local workflows and Codex-assisted execution. V2 will add within the next two weeks a governed agent platform with scoped roles, explicit approvals, auditable tool use, revocation, recovery, and evaluation-driven controls. See [V2 teaser](docs/V2_TEASER.md); those capabilities are not part of V1.
