import { adminDb } from "./firebaseAdmin.js";
import type { Recipe } from "./claude.js";

/**
 * Saves a recipe to the user's household recipe box, as the apps would: the same fields as Android's and
 * iOS's Recipe, timestamps in millis. Admin writes skip the security rules, so this checks membership itself.
 * It goes to the household the user has open. Returns null if they aren't in one; otherwise the new recipe's
 * ID, and that household's name if they're in several (so the app can say where it went).
 */
export async function saveToRecipeBox(
  uid: string,
  recipe: Recipe,
  extra: { source: string; originalText: string; sourceUrl: string; sourceName: string },
): Promise<{ id: string; householdName?: string } | null> {
  const db = adminDb();
  const householdId = (await db.collection("users").doc(uid).get()).get("householdId");
  if (typeof householdId !== "string" || !householdId) return null;
  const household = await db.collection("households").doc(householdId).get();
  const members: unknown = household.get("memberIds");
  if (!Array.isArray(members) || !members.includes(uid)) return null;
  const mine = await db.collection("households").where("memberIds", "array-contains", uid).limit(2).get();
  const now = Date.now();
  const doc = await db
    .collection("households")
    .doc(householdId)
    .collection("savedRecipes")
    .add({ ...recipe, ...extra, createdBy: uid, createdAt: now, updatedAt: now });
  const name: unknown = household.get("name");
  return { id: doc.id, householdName: mine.size > 1 && typeof name === "string" ? name : undefined };
}
