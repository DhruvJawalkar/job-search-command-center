import crypto from "node:crypto";
import dns from "node:dns/promises";
import http from "node:http";
import https from "node:https";
import net from "node:net";

const MAX_REQUEST_BYTES = 1024 * 1024;
const MAX_JOB_PAGE_BYTES = 4 * 1024 * 1024;
const MAX_PROVIDER_BYTES = 4 * 1024 * 1024;
const MAX_REDIRECTS = 5;
const REQUEST_TIMEOUT_MS = 20_000;
const PROVIDER_TIMEOUT_MS = 75_000;
const OPENAI_DESTINATION = new URL("https://api.openai.com/v1/responses");

function json(response, status, body) {
  const serialized = JSON.stringify(body);
  response.writeHead(status, { "content-type": "application/json", "content-length": Buffer.byteLength(serialized),
    "cache-control": "no-store", "x-content-type-options": "nosniff" });
  response.end(serialized);
}

function safeEqual(left, right) {
  const a = Buffer.from(left ?? "");
  const b = Buffer.from(right ?? "");
  return a.length === b.length && a.length > 0 && crypto.timingSafeEqual(a, b);
}

async function readBody(request, limit = MAX_REQUEST_BYTES) {
  const chunks = [];
  let total = 0;
  for await (const chunk of request) {
    total += chunk.length;
    if (total > limit) throw new BrokerError(413, "Request body exceeds the connected-runtime limit.");
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

class BrokerError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

function ipv4Number(address) {
  return address.split(".").reduce((value, part) => (value * 256) + Number(part), 0) >>> 0;
}

function ipv4In(address, base, prefix) {
  const bits = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0;
  return (ipv4Number(address) & bits) === (ipv4Number(base) & bits);
}

export function isPublicAddress(address) {
  if (net.isIPv4(address)) {
    const denied = [["0.0.0.0", 8], ["10.0.0.0", 8], ["100.64.0.0", 10], ["127.0.0.0", 8],
      ["169.254.0.0", 16], ["172.16.0.0", 12], ["192.0.0.0", 24], ["192.0.2.0", 24],
      ["192.168.0.0", 16], ["198.18.0.0", 15], ["198.51.100.0", 24], ["203.0.113.0", 24],
      ["224.0.0.0", 4], ["240.0.0.0", 4]];
    return !denied.some(([base, prefix]) => ipv4In(address, base, prefix));
  }
  if (!net.isIPv6(address)) return false;
  const normalized = address.toLowerCase();
  if (normalized === "::" || normalized === "::1" || normalized.startsWith("::ffff:")
      || normalized.startsWith("fc") || normalized.startsWith("fd") || normalized.startsWith("fe")
      || normalized.startsWith("ff") || normalized.startsWith("2001:db8:")
      || normalized.startsWith("2001:0:") || normalized.startsWith("2001:2:")
      || normalized.startsWith("2001:10:") || normalized.startsWith("2001:20:")
      || normalized.startsWith("2002:") || normalized.startsWith("64:ff9b:")) return false;
  return true;
}

export function validateOpenAiBody(raw) {
  let body;
  try { body = JSON.parse(raw.toString("utf8")); } catch { throw new BrokerError(400, "Request must be valid JSON."); }
  if (!body || Array.isArray(body) || typeof body !== "object" || body.store !== false
      || typeof body.model !== "string" || !body.model.trim()
      || typeof body.instructions !== "string" || typeof body.input !== "string") {
    throw new BrokerError(400, "The provider request must use the reviewed schema-constrained, store:false contract.");
  }
  return raw;
}

export function validateJobUrl(value) {
  let target;
  try { target = new URL(value); } catch { throw new BrokerError(400, "A valid HTTPS job-page URL is required."); }
  if (target.protocol !== "https:" || target.port && target.port !== "443" || target.username || target.password) {
    throw new BrokerError(400, "Connected V1 fetches only public HTTPS job pages on port 443.");
  }
  if (target.hostname === "localhost" || target.hostname.endsWith(".localhost")) {
    throw new BrokerError(400, "Local job-page destinations are blocked.");
  }
  return target;
}

async function resolvePublic(target, resolver = dns.lookup) {
  const answers = await resolver(target.hostname, { all: true, verbatim: true });
  if (!answers.length || answers.some(answer => !isPublicAddress(answer.address))) {
    throw new BrokerError(400, "The destination resolved to a blocked or non-public address.");
  }
  return answers[0];
}

function pinnedRequest(target, answer, options, body = null) {
  return new Promise((resolve, reject) => {
    const request = https.request(target, {
      method: options.method ?? "GET",
      headers: options.headers,
      servername: target.hostname,
      lookup: (_hostname, _options, callback) => callback(null, answer.address, answer.family),
      timeout: options.timeout,
    }, response => resolve(response));
    request.on("timeout", () => request.destroy(new BrokerError(504, "The outbound request timed out.")));
    request.on("error", reject);
    if (body) request.write(body);
    request.end();
  });
}

async function readLimited(stream, limit) {
  const chunks = [];
  let total = 0;
  for await (const chunk of stream) {
    total += chunk.length;
    if (total > limit) {
      stream.destroy();
      throw new BrokerError(502, "The remote response exceeded the connected-runtime limit.");
    }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

export async function fetchJobPage(url, resolver = dns.lookup) {
  let target = validateJobUrl(url);
  for (let redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
    const answer = await resolvePublic(target, resolver);
    const response = await pinnedRequest(target, answer, { timeout: REQUEST_TIMEOUT_MS,
      headers: { accept: "text/html,application/xhtml+xml", "user-agent": "JobSearchCommandCenter-EgressBroker/1.0" } });
    if (response.statusCode >= 300 && response.statusCode < 400) {
      response.resume();
      if (!response.headers.location) throw new BrokerError(502, "The job page returned an invalid redirect.");
      target = validateJobUrl(new URL(response.headers.location, target).toString());
      continue;
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      response.resume();
      throw new BrokerError(502, `The job page returned HTTP ${response.statusCode}.`);
    }
    const type = String(response.headers["content-type"] ?? "").toLowerCase();
    if (!type.includes("text/html") && !type.includes("application/xhtml+xml")) {
      response.resume();
      throw new BrokerError(415, "The destination did not return an HTML job page.");
    }
    const body = await readLimited(response, MAX_JOB_PAGE_BYTES);
    return { finalUrl: target.toString(), contentType: type, body };
  }
  throw new BrokerError(502, "The job page redirected too many times.");
}

async function forwardOpenAi(body, apiKey, resolver = dns.lookup) {
  if (!apiKey) throw new BrokerError(503, "The optional OpenAI provider is not configured in the broker.");
  const answer = await resolvePublic(OPENAI_DESTINATION, resolver);
  const response = await pinnedRequest(OPENAI_DESTINATION, answer, { method: "POST", timeout: PROVIDER_TIMEOUT_MS,
    headers: { authorization: `Bearer ${apiKey}`, "content-type": "application/json", accept: "application/json",
      "content-length": body.length } }, body);
  const type = String(response.headers["content-type"] ?? "").toLowerCase();
  if (!type.includes("application/json")) {
    response.resume();
    throw new BrokerError(502, "The provider returned a non-JSON response.");
  }
  const responseBody = await readLimited(response, MAX_PROVIDER_BYTES);
  return { status: response.statusCode, type, body: responseBody };
}

export function createBrokerServer({ sharedToken, openAiApiKey = "" }) {
  if (!sharedToken || sharedToken.length < 32) throw new Error("BROKER_SHARED_TOKEN must contain at least 32 characters.");
  return http.createServer(async (request, response) => {
    const started = Date.now();
    try {
      if (request.method === "GET" && request.url === "/health") return json(response, 200, { status: "UP" });
      const bearer = String(request.headers.authorization ?? "").replace(/^Bearer\s+/i, "");
      if (!safeEqual(bearer, sharedToken)) throw new BrokerError(401, "Broker authorization failed.");
      if (request.method !== "POST") throw new BrokerError(405, "Only reviewed POST operations are supported.");
      const raw = await readBody(request);
      if (request.url === "/v1/job-page") {
        let input;
        try { input = JSON.parse(raw.toString("utf8")); } catch { throw new BrokerError(400, "Request must be valid JSON."); }
        const result = await fetchJobPage(input.url);
        response.writeHead(200, { "content-type": result.contentType, "content-length": result.body.length,
          "x-jscc-final-url": Buffer.from(result.finalUrl).toString("base64url"), "cache-control": "no-store",
          "x-content-type-options": "nosniff" });
        response.end(result.body);
        console.info(JSON.stringify({ event: "egress", operation: "job-page", destination: new URL(result.finalUrl).hostname,
          outcome: "success", durationMs: Date.now() - started }));
        return;
      }
      if (request.url === "/openai/v1/responses") {
        const result = await forwardOpenAi(validateOpenAiBody(raw), openAiApiKey);
        response.writeHead(result.status, { "content-type": result.type, "content-length": result.body.length,
          "cache-control": "no-store", "x-content-type-options": "nosniff" });
        response.end(result.body);
        console.info(JSON.stringify({ event: "egress", operation: "openai-responses", destination: "api.openai.com",
          outcome: result.status >= 200 && result.status < 300 ? "success" : "provider-error",
          durationMs: Date.now() - started }));
        return;
      }
      throw new BrokerError(404, "This outbound route is not allowlisted.");
    } catch (error) {
      const status = error instanceof BrokerError ? error.status : 502;
      console.warn(JSON.stringify({ event: "egress", operation: "rejected", outcome: "failed",
        errorType: error?.constructor?.name ?? "Error", durationMs: Date.now() - started }));
      json(response, status, { error: error instanceof BrokerError ? error.message : "The broker could not complete the outbound request." });
    }
  });
}

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1].replaceAll("\\", "/")}`).href) {
  const server = createBrokerServer({ sharedToken: process.env.BROKER_SHARED_TOKEN, openAiApiKey: process.env.OPENAI_API_KEY });
  server.listen(Number(process.env.PORT ?? 8787), "0.0.0.0");
}
