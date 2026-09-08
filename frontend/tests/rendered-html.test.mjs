import assert from "node:assert/strict";
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
  assert.match(html, /Daily operating priorities/);
  assert.match(html, /Morning operating plan/);
  assert.ok(html.indexOf("Daily operating priorities") < html.indexOf("Morning operating plan"),
    "Daily priorities should precede the morning schedule");
  assert.match(html, /Personalized daily recommendations/);
  assert.match(html, /Milestone 5D · focused workspaces/);
  assert.match(html, /seven deliberate views/i);
  assert.doesNotMatch(html, /codex-preview|Your site is taking shape|react-loading-skeleton/i);
});
