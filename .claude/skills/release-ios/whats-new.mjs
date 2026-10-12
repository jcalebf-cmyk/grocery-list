// Sets TestFlight "What to Test" for an uploaded build via the App Store Connect API.
// Waits for Apple to finish processing the build first (usually 5–30 minutes).
//
//   node .claude/skills/release-ios/whats-new.mjs <version> <build> <notes-file> [--dry-run]
//
// The API key lives outside the repo in ../GroceryList-signing/app-store-connect.json.

import { createPrivateKey, sign } from "node:crypto";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BUNDLE_ID = "com.householdgroceries.app";
const POLL_SECONDS = 30;
const TIMEOUT_MINUTES = 60;

const args = process.argv.slice(2);
const dryRun = args.includes("--dry-run");
const [version, build, notesFile] = args.filter((a) => a !== "--dry-run");
if (!version || !build || !notesFile) {
  console.error("Usage: whats-new.mjs <version> <build> <notes-file> [--dry-run]");
  process.exit(2);
}
const whatsNew = readFileSync(notesFile, "utf8").trim();
if (whatsNew.length > 4000) throw new Error(`Notes are ${whatsNew.length} characters; Apple allows 4000.`);

const signingDir = join(dirname(fileURLToPath(import.meta.url)), "../../../../GroceryList-signing");
const config = JSON.parse(readFileSync(join(signingDir, "app-store-connect.json"), "utf8"));
const key = createPrivateKey(readFileSync(join(signingDir, config.keyFile)));

function token() {
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const body = `${b64({ alg: "ES256", kid: config.keyId, typ: "JWT" })}.${b64({
    iss: config.issuerId, iat: now, exp: now + 15 * 60, aud: "appstoreconnect-v1",
  })}`;
  const sig = sign("sha256", Buffer.from(body), { key, dsaEncoding: "ieee-p1363" });
  return `${body}.${sig.toString("base64url")}`;
}

async function api(method, path, body) {
  const res = await fetch(`https://api.appstoreconnect.apple.com${path}`, {
    method,
    headers: { Authorization: `Bearer ${token()}`, "Content-Type": "application/json" },
    body: body && JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status}\n${text}`);
  return text ? JSON.parse(text) : null;
}

const apps = await api("GET", `/v1/apps?filter[bundleId]=${BUNDLE_ID}&fields[apps]=bundleId`);
const appId = apps.data.find((a) => a.attributes.bundleId === BUNDLE_ID)?.id;
if (!appId) throw new Error(`No app with bundle ID ${BUNDLE_ID}.`);

// The build can take a few minutes to show up after the upload, then sits in PROCESSING.
const deadline = Date.now() + TIMEOUT_MINUTES * 60_000;
let buildId;
for (;;) {
  const builds = await api("GET",
    `/v1/builds?filter[app]=${appId}&filter[version]=${build}` +
    `&filter[preReleaseVersion.version]=${version}&fields[builds]=processingState`);
  const found = builds.data[0];
  const state = found?.attributes.processingState ?? "NOT YET LISTED";
  if (state === "VALID") { buildId = found.id; break; }
  if (state === "FAILED" || state === "INVALID") throw new Error(`Build ${version} (${build}) is ${state}.`);
  if (dryRun) { console.log(`Build ${version} (${build}): ${state}`); process.exit(0); }
  if (Date.now() > deadline) throw new Error(`Gave up after ${TIMEOUT_MINUTES} min; build is ${state}.`);
  console.log(`Build ${version} (${build}): ${state}, checking again in ${POLL_SECONDS}s…`);
  await new Promise((r) => setTimeout(r, POLL_SECONDS * 1000));
}

const locs = await api("GET", `/v1/builds/${buildId}/betaBuildLocalizations`);
const existing = locs.data.find((l) => l.attributes.locale === "en-US") ?? locs.data[0];
const current = existing?.attributes.whatsNew?.trim() ?? "";
console.log(`Build ${version} (${build}) is processed. Current "What to Test": ` +
  (current === "" ? "empty" : current === whatsNew ? "already matches the notes" : "different text"));
if (dryRun || current === whatsNew) process.exit(0);

if (existing) {
  await api("PATCH", `/v1/betaBuildLocalizations/${existing.id}`, {
    data: { type: "betaBuildLocalizations", id: existing.id, attributes: { whatsNew } },
  });
} else {
  await api("POST", "/v1/betaBuildLocalizations", {
    data: {
      type: "betaBuildLocalizations",
      attributes: { locale: "en-US", whatsNew },
      relationships: { build: { data: { type: "builds", id: buildId } } },
    },
  });
}
console.log(`Set "What to Test" (${existing?.attributes.locale ?? "en-US"}) from ${notesFile}.`);
