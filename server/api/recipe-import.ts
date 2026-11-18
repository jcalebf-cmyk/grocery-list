import { z } from "zod";
import { verifyFirebaseUser } from "../lib/auth.js";
import { importRecipe, recipeFromVideo } from "../lib/claude.js";
import { overDailyLimit, tooManyRequests } from "../lib/rateLimit.js";
import { saveToRecipeBox } from "../lib/savedRecipes.js";
import { videoDetails, youTubeVideoId } from "../lib/youtube.js";

// Long enough for a recipe page copied whole, ads and all. A YouTube link on its own is shorter than 20.
const Body = z.object({
  text: z.string().trim().min(10).max(30_000),
  // The iPhone share extension can't open the app to check the recipe first, so it asks the server to save it.
  save: z.boolean().default(false),
});

const DAILY_LIMIT = 20;

/**
 * POST /api/recipe-import  { text: "<a recipe pasted from anywhere, or a YouTube link>" }
 * Requires a Firebase ID token: Authorization: Bearer <token>
 * Returns { recipe: {...}, source, video? } or 422 if there's no recipe to be had.
 * - source "pasted": the pasted text, keeping the author's wording.
 * - source "youtube": copied from the video's description or the creator's comment, as they wrote it.
 * - source "ai": the creator didn't post the recipe, so Claude wrote one for the dish in the video's title.
 * `video` ({ url, title, channel }) comes with the last two. With `save: true` the recipe also goes into the
 * user's household recipe box, and `savedId` is its ID.
 */
export async function POST(request: Request): Promise<Response> {
  const uid = await verifyFirebaseUser(request);
  if (!uid) return Response.json({ error: "Sign in required" }, { status: 401 });

  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return Response.json({ error: "Send { text } with the recipe" }, { status: 400 });
  const { text, save } = body.data;
  const videoId = youTubeVideoId(text);
  if (!videoId && text.length < 20) return Response.json({ error: "That's too short to be a recipe" }, { status: 422 });
  if (await overDailyLimit("recipe-import", uid, DAILY_LIMIT)) {
    return tooManyRequests("You've reached today's limit for pasting recipes");
  }

  try {
    if (videoId) {
      const video = await videoDetails(videoId);
      if (!video) return Response.json({ error: "Couldn't find that video" }, { status: 422 });
      const result = await recipeFromVideo(video);
      console.log(`uid=${uid} video=${videoId} comments=${video.creatorComments.length} found=${result != null} fromCreator=${result?.fromCreator}`);
      if (!result) return Response.json({ error: "That doesn't look like a cooking video" }, { status: 422 });
      const source = result.fromCreator ? "youtube" : "ai";
      const savedId = save
        ? await saveToRecipeBox(uid, result.recipe, { source, originalText: "", sourceUrl: video.url, sourceName: video.channel })
        : undefined;
      if (savedId === null) return Response.json({ error: "Join a household first" }, { status: 409 });
      return Response.json({
        recipe: result.recipe,
        source,
        video: { url: video.url, title: video.title, channel: video.channel },
        savedId,
      });
    }
    const recipe = await importRecipe(text);
    console.log(`uid=${uid} chars=${text.length} found=${recipe != null} ingredients=${recipe?.ingredients.length ?? 0}`);
    if (!recipe) return Response.json({ error: "That doesn't look like a recipe" }, { status: 422 });
    const savedId = save
      ? await saveToRecipeBox(uid, recipe, { source: "pasted", originalText: text, sourceUrl: "", sourceName: "" })
      : undefined;
    if (savedId === null) return Response.json({ error: "Join a household first" }, { status: 409 });
    return Response.json({ recipe, source: "pasted", savedId });
  } catch (e) {
    console.error("recipe import failed", e);
    return Response.json({ error: "Couldn't read that recipe right now" }, { status: 502 });
  }
}
