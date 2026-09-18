import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);
  return worker.fetch(
    new Request("http://localhost/", { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the command center shell", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);
  const html = await response.text();
  assert.match(html, /<title>Job Search Command Center<\/title>/i);
  assert.match(html, /Morning command center/);
  assert.match(html, /Good morning/);
  assert.match(html, /Local search preferences and personalization/);
  assert.match(html, /Daily perspective/);
  assert.match(html, /Weekly application goal/);
  assert.match(html, /aria-label="0 of 10 weekly applications completed"/);
  assert.match(html, /weekly applications completed/);
  assert.match(html, />Summary</);
  assert.match(html, /Opportunities/);
  assert.match(html, /Network/);
  assert.match(html, /Market &amp; skills/);
  assert.match(html, /Preparation/);
  assert.match(html, /Weekly review/);
  assert.match(html, /Open quick notes/);
  // Evidence archive belongs to Opportunities, not the compact Summary pipeline panel.
  assert.doesNotMatch(html, /immutable application evidence/i);
  assert.match(html, /Application pipeline/);
  assert.match(html, /Application follow-ups/);
  assert.match(html, /No application follow-ups are due in the next 48 hours/);
  assert.match(html, /Top three priorities/);
  assert.match(html, /Customize Summary/);
  assert.match(html, /Daily operating priorities/);
  assert.match(html, /Morning operating plan/);
  assert.ok(html.indexOf("Daily operating priorities") < html.indexOf("Morning operating plan"),
    "Daily priorities should precede the morning schedule");
  assert.match(html, /Personalized daily recommendations/);
  assert.match(html, /Your personalized briefing/);
  assert.match(html, /Focused workspaces/);
  assert.match(html, /seven deliberate views/i);
  assert.doesNotMatch(html, /Milestone\s+\d/i);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape|react-loading-skeleton/i);
});

test("privacy controls preserve explicit records and expose the complete local policy workflow", async () => {
  const source = await readFile(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /Privacy &amp; intelligence/);
  assert.match(source, /Stateless applies only to assistant-derived context/);
  assert.match(source, /Records you deliberately save.*separate retention controls/);
  assert.match(source, /STATELESS/);
  assert.match(source, /SESSION_ONLY/);
  assert.match(source, /TIME_BOUND/);
  assert.match(source, /\[7, 30, 90\]/);
  assert.match(source, /Transient import retention/);
  assert.match(source, /transientIngestionRetentionDays/);
  assert.match(source, /Recommended default/);
  assert.match(source, /Longer import review window/);
  assert.match(source, /\/api\/v1\/privacy-policy\/cleanup-preview/);
  assert.match(source, /cleanup-preview", \{ method: "POST"/);
  assert.match(source, /\/api\/v1\/privacy-policy\/cleanup/);
  assert.match(source, /Run cleanup now/);
  assert.match(source, /Confirm the stricter privacy policy/);
  assert.match(source, /Explicitly saved records remain untouched/);
  assert.match(source, /previewBasis/);
  assert.match(source, /Codex has separate controls/);
  assert.match(source, /Allow connected assistance/);
  assert.match(source, /reviewed connected runtime and confirmation of every transmission/);
  assert.match(source, /Assistant-derived results remain response-only/);
  assert.match(source, /Assistant-derived results remain in memory for this backend session/);
});

test("connected actions require a one-time transmission preview and preserve stateless reviewed saves", async () => {
  const source = await readFile(new URL("../app/page.tsx", import.meta.url), "utf8");
  const confirmation = await readFile(new URL("../app/privacy/TransmissionConfirmation.tsx", import.meta.url), "utf8");
  assert.match(source, /\/live-extractions\/preview/);
  assert.match(source, /\/transmission-preview/);
  assert.match(source, /confirmationToken/);
  assert.match(source, /statelessSaveArtifact/);
  assert.match(source, /\/api\/v1\/assistance\/stateless\/inbox\/\$\{candidate\.id\}\/apply/);
  assert.match(source, /\/api\/v1\/assistance\/stateless\/inbox\/\$\{candidate\.id\}\/skills/);
  assert.match(confirmation, /Review exactly what leaves the local-only boundary/);
  assert.match(confirmation, /Destination/);
  assert.match(confirmation, /Purpose/);
  assert.match(confirmation, /Minimized fields/);
  assert.match(confirmation, /One request only/);
});
