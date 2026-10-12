import { z } from "zod";
import { verifyFirebaseUser } from "../lib/auth.js";
import { writeRecipes } from "../lib/claude.js";
import { overDailyLimit, tooManyRequests } from "../lib/rateLimit.js";
import { searchVideos } from "../lib/youtube.js";

const Item = z.string().trim().min(1).max(60);
const Cuisine = z.string().trim().min(1).max(30);
const Body = z
  .object({
    items: z.array(Item).max(30).default([]), // bought often
    onHand: z.array(Item).max(30).default([]), // already in the kitchen or delivered by subscription
    cuisines: z.array(Cuisine).max(20).default([]), // the household's favorites
    mood: z.array(Cuisine).max(3).default([]), // what they're in the mood for tonight; overrides favorites
  })
  .refine((b) => b.items.length + b.onHand.length > 0);

// Covers the daily refresh, a few kitchen edits, and some "New ideas" taps. Each request costs one
// Claude call plus a YouTube search per recipe: about 600 of YouTube's 10,000 daily quota units.
const DAILY_LIMIT = 8;

/**
 * POST /api/recipes  { items: ["broccoli", ...], onHand: ["ground beef", ...], cuisines: ["Thai", ...], mood: ["Mexican"] }
 * Requires a Firebase ID token: Authorization: Bearer <token>
 * Returns { recipes: [...] }: recipes Claude writes for the household, each with a YouTube video of a
 * similar dish to watch.
 */
export async function POST(request: Request): Promise<Response> {
  const uid = await verifyFirebaseUser(request);
  if (!uid) return Response.json({ error: "Sign in required" }, { status: 401 });

  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return Response.json({ error: "Send { items: string[], onHand: string[] }" }, { status: 400 });
  const { items, onHand, cuisines, mood } = body.data;
  if (await overDailyLimit("recipes", uid, DAILY_LIMIT)) {
    return tooManyRequests("You've reached today's limit for new recipe ideas");
  }

  try {
    const written = (await writeRecipes(items, onHand, cuisines, mood)).slice(0, 6);
    const candidates = await Promise.all(
      written.map((r) =>
        searchVideos(r.videoQuery).catch((e) => {
          console.warn(`video search failed for "${r.videoQuery}"`, e);
          return [];
        }),
      ),
    );

    // The apps key recipes by video, so each recipe needs its own. A recipe with no video is dropped.
    const used = new Set<string>();
    const recipes = written.flatMap(({ videoQuery, ...r }, i) => {
      const video = candidates[i].find((v) => !used.has(v.id));
      if (!video) return [];
      used.add(video.id);
      return [
        {
          ...r,
          videoId: video.id,
          videoUrl: `https://www.youtube.com/watch?v=${video.id}`,
          videoTitle: video.title,
          channel: video.channel,
          thumbnailUrl: video.thumbnailUrl,
        },
      ];
    });

    console.log(`uid=${uid} items=${items.length} onHand=${onHand.length} cuisines=${cuisines.length} mood=${mood.join("/")} written=${written.length} recipes=${recipes.length}`);
    return Response.json({ recipes });
  } catch (e) {
    console.error("recipe generation failed", e);
    return Response.json({ error: "Couldn't fetch recipes right now" }, { status: 502 });
  }
}
