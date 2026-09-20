# Codex-assisted use

Codex is a conversational guide around the local V1 application, not an autonomous application-submission or outreach system. The user can begin on any page and choose an outcome that is useful now.

## Create the local Codex project

Open **Projects** in the Codex desktop app and create a local project named **Job Search Command Center**. Add the cloned `job-search-command-center` repository folder and make it the primary folder, then start a new chat inside that project. This lets the project chat read the product guidance, [`AGENTS.md`](../AGENTS.md), examples, and the checked-in workflow catalog. The implementation source is deliberately not part of this default branch.

## Keep the application and Codex boundaries separate

The application's Privacy & intelligence setting governs application-owned assistant context and does not change Codex task transcripts, memories, permissions, or account retention. If the default `workspace/` is inside the project folder, it is Git-ignored but may still be within the filesystem scope available to the Codex task. Do not ask Codex to read `.env`, `postgres-data/`, backups, full contact exports, or other private files unless the selected workflow truly needs them. Never paste passwords, API keys, or unnecessary third-party personal data into the chat.

Review Codex [memory controls](https://learn.chatgpt.com/docs/customization/memories) and [permissions](https://learn.chatgpt.com/docs/permissions) separately. The application can display your preferred privacy mode, but a generated instruction is guidance rather than an enforcement boundary. See the project's [Privacy notice](../PRIVACY.md) for the current cleanup limits.

## Open the local app

After setup reports healthy, return to the project chat, open a **Browser** tab in the right-side panel, and enter `http://127.0.0.1:3000`. On any page, ask:

> Help me on this page.

Codex uses the URL hash and visible page state, reads the matching index under [`guided-workflows/`](../guided-workflows/README.md), and offers the three most relevant workflows with an outcome and time estimate. It reads the detailed playbook only after one is selected. The app does not require the user to complete Profile first, redirect a first opening automatically, or follow a fixed tour.

The user can also ask for a specific outcome directly:

- “Help me understand this Summary.”
- “Help me add and review this opening.”
- “Help me find a trusted contact for this role.”
- “Help me turn this skill gap into a two-week preparation plan.”
- “Help me review and preserve this week.”

## Proposed daily discovery task

Creating a recurring task is optional and must be explicitly requested. The schedule should be selected by the user; 8:00 AM in the profile’s local time zone is only a starting suggestion. During setup, produce one small, immediate result so the user can validate the relevance and import experience before waiting for the first scheduled run.

Suggested setup instructions:

> Help me configure a recurring daily high-fit opening discovery task. Use the schedule and time zone I choose; ask me before creating the task if either is missing. Immediately after the schedule is created, perform a one-time bootstrap run: read my saved Job Search Command Center profile, find up to three currently open roles that fit it, prefer official company career pages, and verify each direct job link. Aim to finish within one minute by stopping at three strong results. Write today’s `YYYY-MM-DD-high-fit-openings.xlsx` to my local `daily-high-fit-job-roles` folder using the exact V1 workbook contract, and write the matching `YYYY-MM-DD-top-three-actions.txt` with one concise, ranked action for each opening. Then guide me to import and review those files. This bootstrap is setup-only and must not be included as a recurring instruction. Do not log in, apply, contact anyone, or modify my command-center data directly.

Recurring task instructions to save:

> Each scheduled run, research currently open roles that match my Job Search Command Center profile. Prefer official company career pages and verify each direct job link. Exclude technologies, domains, locations, levels, and work arrangements listed in my profile exclusions. Rank at most 10 high-fit openings using recruiter-screen strength, technical scope, growth potential, and an overall weighted score from 0 to 10. Write `YYYY-MM-DD-high-fit-openings.xlsx` to my local `daily-high-fit-job-roles` folder using the exact Job Search Command Center V1 workbook contract. Also write `YYYY-MM-DD-top-three-actions.txt` with three concise, ranked next actions. Research and write files only: do not log in, apply, contact anyone, or modify my command-center data directly. Stay quiet if there are no verified new results or if the task cannot safely write to the selected local folder; report only actionable failures.

Keep the one-time bootstrap in the setup conversation and only the recurring block in the saved task. Scheduled work depends on the local computer, Codex environment, connected sources, and permissions being available at run time. Review every generated workbook before importing it. See [Import daily files](../guided-workflows/opportunities/import-daily-files.md) for the app workflow.

## Generate a three-opening example now

To learn the import workflow without waiting for a schedule, ask:

> Using only fictional companies and clearly synthetic content, create a three-row workbook that follows `docs/DAILY_HIGH_FIT_ETL.md`. Save it as today’s `YYYY-MM-DD-high-fit-openings.xlsx` in my local `daily-high-fit-job-roles` folder, create a matching top-three-actions text file, then guide me to import and review it. Finish the files in under one minute if possible. Do not use any personal account or send anything externally.

In the app, open **Opportunities**, choose **Sync daily files**, review the three records, and inspect one opening’s evidence history. A ready-made ten-opening example is also available under `samples/daily-high-fit-job-roles/`.

## Refine relevance

Use **Profile** in the app—not only a chat message—to preserve role families, level, locations, work modes, company preferences, previous employers, career direction, culture and values, and technology inclusions or exclusions. The [Profile workflow index](../guided-workflows/profile/README.md) offers focused ways to refine only the fields that matter.

## Advanced V1 demonstrations

With explicit user review at every consequential step, Codex can help demonstrate:

- tailoring a resume in Overleaf or Google Drive while preserving the original;
- preparing an application package and filling a form for review;
- finding relevant engineers or recruiters on LinkedIn;
- drafting a connection note or referral request; and
- recording the user-approved outcome back in the app.

External services have their own terms, privacy boundaries, authentication, and confirmation screens. V1 does not provide a hidden approval layer for them; the user remains the decision-maker. Application submission, message sending, publication, account changes, and recurring schedules always require explicit user review and instruction at the point of action.
