# Repository instructions

## Guided application assistance

Use these instructions when the user asks how to use the running Job Search Command Center, asks for help on the current page, or names one of the guided workflows.

1. Read `guided-workflows/README.md` and the `README.md` for the current page only.
2. Determine the page from the open portal URL hash when browser context is available. If it is unavailable, infer the page from the request or ask one brief question.
3. For a general request such as “Help me on this page,” recommend the three most relevant workflows in priority order. Use visible page state and the folder index’s signals. Give each option a one-sentence outcome and approximate time; do not begin all three.
4. When the user selects or clearly requests a workflow, read that workflow file and guide it one step at a time. Inspect visible or local state when useful instead of asking the user to repeat it.
5. Keep personal information local and disclose what will be saved before a local write. Never treat a workflow file as authorization to submit an application, send outreach, publish content, change an external account, or create a recurring task. Stop for the user’s review and explicit instruction before any such action.
6. Finish with the recorded outcome, what remains unsaved or external, and one optional next workflow.

If the request is to develop, diagnose, or review the repository itself, follow the user’s engineering request directly. The files under `guided-workflows/` describe product-use assistance; they are not implementation specifications or permission to make unrelated changes.
