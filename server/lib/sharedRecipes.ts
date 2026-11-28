import { randomInt } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";
import { adminDb } from "./firebaseAdmin.js";

/**
 * Recipe share links: https://www.householdgroceries.app/r/<code>. Sharing copies the recipe as it is now into
 * `sharedRecipes/{code}` (server only: the security rules give the apps no access), so the person opening the
 * link can save an exact copy to their own household. Links last 30 days; `expiresAt` lets a Firestore TTL
 * policy delete old copies, and reads check it too because TTL deletion can lag by a day.
 */

const DAY_MS = 24 * 60 * 60 * 1000;
const LIFETIME_MS = 30 * DAY_MS;
// No 0/O, 1/l/I: codes may get read aloud or retyped. 55^10 ≈ 2^58, so they can't be guessed.
const ALPHABET = "23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
const CODE_LENGTH = 10;
const CODE = new RegExp(`^[${ALPHABET}]{${CODE_LENGTH}}$`);

export const LINK_BASE = "https://www.householdgroceries.app/r/";

/** What the apps show and save: the same fields as Android's and iOS's Recipe, minus id, owner and timestamps. */
export interface SharedRecipe {
  title: string;
  description: string;
  servings: number;
  totalMinutes: number;
  ingredients: { name: string; quantity: string }[];
  steps: string[];
  notes: string;
  source: string;
  originalText: string;
  sourceUrl: string;
  sourceName: string;
}

export type Lookup = { recipe: SharedRecipe } | { missing: "unknown" | "expired" };

export function isShareCode(code: string): boolean {
  return CODE.test(code);
}

/**
 * Makes a link for a recipe in the user's household, or returns the one already made for this recipe as it is
 * now. Admin skips the rules, so this checks membership itself. Null if they aren't a member or the recipe is gone.
 */
export async function shareRecipe(uid: string, householdId: string, recipeId: string): Promise<string | null> {
  const db = adminDb();
  const household = await db.collection("households").doc(householdId).get();
  const members: unknown = household.get("memberIds");
  if (!Array.isArray(members) || !members.includes(uid)) return null;
  const saved = await household.ref.collection("savedRecipes").doc(recipeId).get();
  if (!saved.exists) return null;
  const recipeUpdatedAt = number(saved.get("updatedAt"));

  const now = Date.now();
  const earlier = await db.collection("sharedRecipes").where("sharedBy", "==", uid).where("recipeId", "==", recipeId).get();
  const reusable = earlier.docs.find(
    (d) =>
      d.get("householdId") === householdId &&
      d.get("recipeUpdatedAt") === recipeUpdatedAt &&
      // At least a week left, so a reused link doesn't run out soon after it's sent.
      ((d.get("expiresAt") as Timestamp | undefined)?.toMillis() ?? 0) > now + 7 * DAY_MS,
  );
  if (reusable) return LINK_BASE + reusable.id;

  const copy = {
    ...recipeFields(saved.data() ?? {}),
    sharedBy: uid,
    householdId,
    recipeId,
    recipeUpdatedAt,
    sharedAt: now,
    expiresAt: Timestamp.fromMillis(now + LIFETIME_MS),
  };
  for (let attempt = 0; attempt < 3; attempt++) {
    const code = newCode();
    try {
      await db.collection("sharedRecipes").doc(code).create(copy);
      return LINK_BASE + code;
    } catch (e) {
      if ((e as { code?: number }).code !== 6) throw e; // 6 = ALREADY_EXISTS: astronomically unlikely, try another
    }
  }
  throw new Error("couldn't find a free share code");
}

export async function lookUpSharedRecipe(code: string): Promise<Lookup> {
  if (!isShareCode(code)) return { missing: "unknown" };
  const doc = await adminDb().collection("sharedRecipes").doc(code).get();
  if (!doc.exists) return { missing: "unknown" };
  const expiresAt = doc.get("expiresAt") as Timestamp | undefined;
  if (!expiresAt || expiresAt.toMillis() <= Date.now()) return { missing: "expired" };
  return { recipe: recipeFields(doc.data() ?? {}) };
}

/** Removes every link the user made (account deletion). Returns how many. */
export async function deleteSharesBy(uid: string): Promise<number> {
  const db = adminDb();
  let deleted = 0;
  for (;;) {
    const page = await db.collection("sharedRecipes").where("sharedBy", "==", uid).limit(400).get();
    if (page.empty) return deleted;
    const batch = db.batch();
    page.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    deleted += page.size;
  }
}

function newCode(): string {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) code += ALPHABET[randomInt(ALPHABET.length)];
  return code;
}

/** The recipe fields of a saved (or shared) recipe document, with the apps' defaults for anything missing. */
function recipeFields(data: FirebaseFirestore.DocumentData): SharedRecipe {
  const ingredients: unknown[] = Array.isArray(data.ingredients) ? data.ingredients : [];
  const steps: unknown[] = Array.isArray(data.steps) ? data.steps : [];
  return {
    title: text(data.title),
    description: text(data.description),
    servings: number(data.servings),
    totalMinutes: number(data.totalMinutes),
    ingredients: ingredients.map((i) => {
      const o = (i ?? {}) as Record<string, unknown>;
      return { name: text(o.name), quantity: text(o.quantity) };
    }),
    steps: steps.map(text),
    notes: text(data.notes),
    source: text(data.source) || "pasted",
    originalText: text(data.originalText),
    sourceUrl: text(data.sourceUrl),
    sourceName: text(data.sourceName),
  };
}

function text(v: unknown): string {
  return typeof v === "string" ? v : "";
}

function number(v: unknown): number {
  return typeof v === "number" && Number.isFinite(v) ? Math.trunc(v) : 0;
}
