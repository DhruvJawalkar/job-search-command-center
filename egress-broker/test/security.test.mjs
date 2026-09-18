import assert from "node:assert/strict";
import test from "node:test";
import { isPublicAddress, validateJobUrl, validateOpenAiBody } from "../server.mjs";

test("blocks local, private, link-local, documentation and multicast addresses", () => {
  for (const value of ["127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.169.254", "172.16.0.1",
    "192.168.1.1", "192.0.2.1", "198.51.100.2", "203.0.113.4", "224.0.0.1", "::1", "fc00::1",
    "fe80::1", "fec0::1", "ff02::1", "2001:db8::1", "2001:0:53aa::1", "2002:7f00:1::",
    "64:ff9b::7f00:1", "::ffff:127.0.0.1", "::ffff:7f00:1"]) assert.equal(isPublicAddress(value), false, value);
  assert.equal(isPublicAddress("8.8.8.8"), true);
  assert.equal(isPublicAddress("2606:4700:4700::1111"), true);
});

test("provider route accepts only the reviewed store-false contract", () => {
  const accepted = Buffer.from(JSON.stringify({ model: "reviewed-model", store: false, instructions: "Return JSON",
    input: "minimized input", text: { format: { type: "json_schema" } } }));
  assert.equal(validateOpenAiBody(accepted), accepted);
  for (const value of [{ model: "m", store: true, instructions: "i", input: "x" },
    { model: "m", instructions: "i", input: "x" }, { model: "m", store: false, input: "x" }]) {
    assert.throws(() => validateOpenAiBody(Buffer.from(JSON.stringify(value))));
  }
});

test("job fetch accepts only credential-free HTTPS port 443 URLs", () => {
  assert.equal(validateJobUrl("https://jobs.example.com/role?id=1").hostname, "jobs.example.com");
  for (const value of ["http://jobs.example.com/role", "https://localhost/role", "https://user:pass@example.com/",
    "https://example.com:8443/role", "file:///etc/passwd"]) assert.throws(() => validateJobUrl(value));
});
