import assert from "node:assert/strict";
import test from "node:test";
import { createBrokerServer } from "../server.mjs";

test("health is local and outbound operations require the shared token", async t => {
  const token = "test-token-that-is-at-least-thirty-two-characters";
  const server = createBrokerServer({ sharedToken: token });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  t.after(() => server.close());
  const address = server.address();
  const base = `http://127.0.0.1:${address.port}`;

  const health = await fetch(`${base}/health`);
  assert.equal(health.status, 200);
  const denied = await fetch(`${base}/v1/job-page`, { method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ url: "https://example.com/job" }) });
  assert.equal(denied.status, 401);
  const unknown = await fetch(`${base}/anything`, { method: "POST", headers: { authorization: `Bearer ${token}` } });
  assert.equal(unknown.status, 404);
});
