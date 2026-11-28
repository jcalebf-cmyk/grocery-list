import { z } from "zod";
import { verifyFirebaseUser } from "../lib/auth.js";
import { readJson } from "../lib/body.js";
import { limitsUnavailable, overDailyLimit, tooManyRequests } from "../lib/rateLimit.js";
import { deleteSharesBy, lookUpSharedRecipe, shareRecipe } from "../lib/sharedRecipes.js";

const DAILY_LIMIT = 40;

const Body = z.object({
  householdId: z.string().min(1).max(128),
  recipeId: z.string().min(1).max(128),
});

/**
 * POST /api/recipe-share  { householdId, recipeId }
 * Requires a Firebase ID token. Copies the recipe as it is now and returns { url }, a link that lasts 30 days.
 * Sharing the same unchanged recipe again returns the same link.
 */
export async function POST(request: Request): Promise<Response> {
  const uid = await verifyFirebaseUser(request);
  if (!uid) return Response.json({ error: "Sign in required" }, { status: 401 });

  const body = Body.safeParse(await readJson(request, 2_000));
  if (!body.success) return Response.json({ error: "Send { householdId, recipeId }" }, { status: 400 });
  try {
    if (await overDailyLimit("recipe-share", uid, DAILY_LIMIT)) {
      return tooManyRequests("You've shared as many recipes as you can today");
    }
  } catch (e) {
    return limitsUnavailable(e);
  }

  try {
    const url = await shareRecipe(uid, body.data.householdId, body.data.recipeId);
    if (!url) return Response.json({ error: "Couldn't find that recipe" }, { status: 404 });
    return Response.json({ url });
  } catch (e) {
    console.error("recipe share failed", e);
    return Response.json({ error: "Couldn't share that recipe right now" }, { status: 502 });
  }
}

/**
 * GET /api/recipe-share?code=<code>
 * No sign-in: anyone with the link can see the recipe. Returns { recipe }, 404 for an unknown code, 410 once
 * the link has expired.
 */
export async function GET(request: Request): Promise<Response> {
  const code = new URL(request.url).searchParams.get("code") ?? "";
  try {
    const found = await lookUpSharedRecipe(code);
    if ("missing" in found) {
      return found.missing === "expired"
        ? Response.json({ error: "This link has expired" }, { status: 410 })
        : Response.json({ error: "Couldn't find that recipe" }, { status: 404 });
    }
    return Response.json({ recipe: found.recipe }, { headers: { "Cache-Control": "private, max-age=60" } });
  } catch (e) {
    console.error("shared recipe lookup failed", e);
    return Response.json({ error: "Couldn't open that recipe right now" }, { status: 502 });
  }
}

/**
 * DELETE /api/recipe-share
 * Requires a Firebase ID token. Removes every link the user made; the apps call it when deleting an account.
 */
export async function DELETE(request: Request): Promise<Response> {
  const uid = await verifyFirebaseUser(request);
  if (!uid) return Response.json({ error: "Sign in required" }, { status: 401 });
  try {
    const deleted = await deleteSharesBy(uid);
    return Response.json({ ok: true, deleted });
  } catch (e) {
    console.error("deleting shared recipes failed", e);
    return Response.json({ error: "Couldn't remove shared recipes right now" }, { status: 502 });
  }
}
