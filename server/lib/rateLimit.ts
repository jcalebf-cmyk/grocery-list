import { createHash } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";
import { adminDb } from "./firebaseAdmin.js";

const DAY_MS = 24 * 60 * 60 * 1000;
const TIMEOUT_MS = 5_000;

/**
 * The most the whole app may use each endpoint in a day, however many accounts are calling it: accounts are
 * free to make, so per-user limits alone don't cap what the paid services cost. With every
 * request about as large as it's allowed to be, chat and import come to about $25 a day at Haiku 5.5's prices
 * (Nov 2026). Raise these as the app grows.
 */
const APP_LIMITS: Record<string, number> = {
  "recipe-chat": 1000, // Claude
  "recipe-import": 500, // Claude, and 2 of YouTube's 10,000 daily quota units
  stores: 300, // Google Places
  "verify-email": 200, // Resend
  "recipe-share": 2000, // Firestore only: a cap on how many copies pile up
  // No app-wide limit on password resets: anyone could use it up and lock everyone out of resetting.
};

/**
 * Counts this request and says whether it's over a daily limit: `limit` for this `key` (a user ID, or
 * whatever else identifies the caller), or the app-wide one. Counted in Firestore in a transaction, so
 * simultaneous requests can't slip past. Refused requests aren't counted.
 *
 * Throws if the count can't be checked: without it there's nothing to stop runaway cost, so callers refuse
 * the request (see limitsUnavailable) rather than let it through.
 *
 * The counts are in `rateLimits`, which the security rules give the apps no access to. Each document is reused from
 * day to day; `expiresAt` lets a Firestore TTL policy delete the ones nobody is using.
 */
export async function overDailyLimit(
  scope: string,
  key: string,
  limit: number,
  appLimit: number | undefined = APP_LIMITS[scope],
): Promise<boolean> {
  const day = Math.floor(Date.now() / DAY_MS);
  const db = adminDb();
  const counts = db.collection("rateLimits");
  const mine = counts.doc(`${scope}-${createHash("sha256").update(key).digest("hex")}`);
  const all = appLimit === undefined ? null : counts.doc(`${scope}-all`);

  const counting = db.runTransaction(async (tx) => {
    const usedToday = (doc: FirebaseFirestore.DocumentSnapshot | undefined): number =>
      doc?.get("day") === day ? Number(doc.get("count")) : 0;
    const [mineDoc, allDoc] = await tx.getAll(...(all ? [mine, all] : [mine]));
    const used = usedToday(mineDoc);
    const usedByAll = usedToday(allDoc);
    if (used >= limit) return "user";
    if (appLimit !== undefined && usedByAll >= appLimit) return "app";
    const expiresAt = Timestamp.fromMillis((day + 2) * DAY_MS);
    tx.set(mine, { day, count: used + 1, expiresAt });
    if (all) tx.set(all, { day, count: usedByAll + 1, expiresAt });
    return null;
  });

  const over = await withTimeout(counting);
  if (over === "app") console.error(`app-wide daily limit reached for ${scope} (${appLimit})`);
  return over !== null;
}

async function withTimeout<T>(work: Promise<T>): Promise<T> {
  let timer: NodeJS.Timeout | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error("rate limit check timed out")), TIMEOUT_MS);
  });
  try {
    return await Promise.race([work, timeout]);
  } finally {
    clearTimeout(timer);
  }
}

export function tooManyRequests(message: string): Response {
  const secondsUntilTomorrow = Math.ceil((DAY_MS - (Date.now() % DAY_MS)) / 1000);
  return Response.json({ error: message }, { status: 429, headers: { "Retry-After": String(secondsUntilTomorrow) } });
}

/** The reply when a limit couldn't be checked. */
export function limitsUnavailable(e: unknown): Response {
  console.error("rate limit check failed; refusing the request", e);
  return Response.json({ error: "Try again in a moment" }, { status: 503 });
}
