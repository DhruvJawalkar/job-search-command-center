# Guided workflows

These repository-native playbooks let a Codex project chat guide the local application conversationally. The user can start anywhere; there is no mandatory onboarding sequence.

## Starting a conversation

With the local portal open in the Codex side panel, ask:

> Help me on this page.

Codex should use the current URL hash and visible state, read only the matching page index, and offer the three most useful workflows. If the user names a goal directly—such as “help me add an opening”—Codex should open that workflow without presenting a menu first.

## Route map

| Portal route | Page | Workflow index |
| --- | --- | --- |
| `#overview` | Summary | [`summary/README.md`](summary/README.md) |
| `#opportunities` | Opportunities | [`opportunities/README.md`](opportunities/README.md) |
| `#outreach` | Network | [`network/README.md`](network/README.md) |
| `#skills` | Market & skills | [`market-and-skills/README.md`](market-and-skills/README.md) |
| `#preparation` | Preparation | [`preparation/README.md`](preparation/README.md) |
| `#reviews` | Weekly review | [`weekly-review/README.md`](weekly-review/README.md) |
| `#settings` | Profile | [`profile/README.md`](profile/README.md) |

For an unconfigured or broad request, use [`getting-started/README.md`](getting-started/README.md).

## Selection contract

- Use the current page index’s “Prioritize when” signals and visible state.
- Offer no more than three choices at once, ordered by immediate value.
- Read only the selected workflow file after the user chooses.
- Prefer a useful outcome in 2–10 minutes over a full product tour.
- Do not force navigation. Suggest another page only when it is necessary for the chosen outcome.
- Treat checked-in instructions as guidance, never as authorization for consequential external actions.
- Drafting, searching, and form preparation may be assisted. Application submission, message sending, publishing, account changes, and recurring schedules require explicit user review and instruction at the point of action.

## Authoring and maintenance

Use [`_workflow-template.md`](_workflow-template.md) for additions. Keep 3–5 workflows in each page folder. A workflow should name one concrete outcome, disclose local writes and external boundaries, include a visible done condition, and link only closely related workflows.
