import { adminDb } from "./firebaseAdmin.js";
import type { Recipe } from "./claude.js";

/**
 * Saves a recipe to the user's household recipe box, as the apps would: the same fields as Android's and
 * iOS's Recipe, timestamps in millis. Admin writes skip the security rules, so this checks membership itself.
 * Returns the new recipe's ID, or null if the user isn't in a household.
 */
export async function saveToRecipeBox(
  uid: string,
  recipe: Recipe,
  extra: { source: string; originalText: string; sourceUrl: string; sourceName: string },
): Promise<string | null> {
  const db = adminDb();
  const householdId = (await db.collection("users").doc(uid).get()).get("householdId");
  if (typeof householdId !== "string" || !householdId) return null;
  const household = await db.collection("households").doc(householdId).get();
  const members: unknown = household.get("memberIds");
  if (!Array.isArray(members) || !members.includes(uid)) return null;
  const now = Date.now();
  const doc = await db
    .collection("households")
    .doc(householdId)
    .collection("savedRecipes")
    .add({ ...recipe, ...extra, createdBy: uid, createdAt: now, updatedAt: now });
  return doc.id;
}
