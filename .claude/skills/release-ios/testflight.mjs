// Finishes a TestFlight upload via the App Store Connect API: waits for Apple to finish processing
// the build (usually 5–30 minutes), sets "What to Test", then adds the build to every external
// group and submits it for Beta App Review. Internal testers get it by automatic distribution.
//
//   node .claude/skills/release-ios/testflight.mjs <version> <build> <notes-file> [--dry-run] [--internal-only]
//
// --dry-run only reports. --internal-only skips the external groups and review.
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
const internalOnly = args.includes("--internal-only");
const [version, build, notesFile] = args.filter((a) => !a.startsWith("--"));
if (!version || !build || !notesFile) {
  console.error("Usage: testflight.mjs <version> <build> <notes-file> [--dry-run] [--internal-only]");
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
if (dryRun || current === whatsNew) {
  // Nothing to set.
} else if (existing) {
  await api("PATCH", `/v1/betaBuildLocalizations/${existing.id}`, {
    data: { type: "betaBuildLocalizations", id: existing.id, attributes: { whatsNew } },
  });
  console.log(`Set "What to Test" (${existing.attributes.locale}) from ${notesFile}.`);
} else {
  await api("POST", "/v1/betaBuildLocalizations", {
    data: {
      type: "betaBuildLocalizations",
      attributes: { locale: "en-US", whatsNew },
      relationships: { build: { data: { type: "builds", id: buildId } } },
    },
  });
  console.log(`Set "What to Test" (en-US) from ${notesFile}.`);
}
if (internalOnly) process.exit(0);

// External testers only see a build once it's in their group and Beta App Review approves it.
// "What to Test" has to be set before submitting, so this comes last.
const groups = await api("GET",
  `/v1/apps/${appId}/betaGroups?fields[betaGroups]=name,isInternalGroup&limit=200`);
const external = groups.data.filter((g) => !g.attributes.isInternalGroup);
if (external.length === 0) { console.log("No external groups; internal testers only."); process.exit(0); }

for (const group of external) {
  const name = `external group "${group.attributes.name}"`;
  const inGroup = await api("GET",
    `/v1/builds?filter[id]=${buildId}&filter[betaGroups]=${group.id}&fields[builds]=version`);
  if (inGroup.data.length > 0) { console.log(`Already in ${name}.`); continue; }
  if (dryRun) { console.log(`Not yet in ${name}.`); continue; }
  await api("POST", `/v1/betaGroups/${group.id}/relationships/builds`,
    { data: [{ type: "builds", id: buildId }] });
  console.log(`Added to ${name}.`);
}

const reviews = await api("GET",
  `/v1/betaAppReviewSubmissions?filter[build]=${buildId}&fields[betaAppReviewSubmissions]=betaReviewState`);
const review = reviews.data[0]?.attributes.betaReviewState;
if (review) {
  console.log(`Beta App Review: ${review}.`);
} else if (dryRun) {
  console.log("Beta App Review: not submitted yet.");
} else {
  const sub = await api("POST", "/v1/betaAppReviewSubmissions", {
    data: { type: "betaAppReviewSubmissions",
      relationships: { build: { data: { type: "builds", id: buildId } } } },
  });
  console.log(`Submitted for Beta App Review: ${sub.data.attributes.betaReviewState}. ` +
    "External testers get the build once it's approved (usually under a day; " +
    "later builds of an approved version are often approved within minutes).");
}
