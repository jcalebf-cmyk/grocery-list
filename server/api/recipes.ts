import { z } from "zod";
import { verifyFirebaseUser } from "../lib/auth.js";
import { extractSimpleRecipes, searchQueriesFor } from "../lib/claude.js";
import { getVideos, searchVideoIds } from "../lib/youtube.js";

const Body = z.object({ items: z.array(z.string().trim().min(1).max(60)).min(1).max(30) });

// Longer videos are rarely "simple", and their descriptions are often blog-length.
const MAX_VIDEO_MINUTES = 25;

/**
 * POST /api/recipes  { items: ["chicken breast", "broccoli", ...] }
 * Requires a Firebase ID token: Authorization: Bearer <token>
 * Returns { recipes: [...] }, each tied to the YouTube video it came from.
 */
export async function POST(request: Request): Promise<Response> {
  const uid = await verifyFirebaseUser(request);
  if (!uid) return Response.json({ error: "Sign in required" }, { status: 401 });

  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return Response.json({ error: "Send { items: string[] }" }, { status: 400 });
  const items = body.data.items;

  try {
    const queries = await searchQueriesFor(items);
    const idLists = await Promise.all(queries.map((q) => searchVideoIds(q, 5)));
    const videos = (await getVideos([...new Set(idLists.flat())])).filter(
      (v) => v.durationMinutes <= MAX_VIDEO_MINUTES && v.description.length > 80,
    );

    const byId = new Map(videos.map((v) => [v.id, v]));
    const recipes = (await extractSimpleRecipes(items, videos)).flatMap((r) => {
      const video = byId.get(r.videoId);
      if (!video) return []; // model referenced a video we didn't send
      return [
        {
          ...r,
          videoUrl: `https://www.youtube.com/watch?v=${video.id}`,
          videoTitle: video.title,
          channel: video.channel,
          thumbnailUrl: video.thumbnailUrl,
        },
      ];
    });

    console.log(`uid=${uid} items=${items.length} queries=${queries.length} videos=${videos.length} recipes=${recipes.length}`);
    return Response.json({ recipes });
  } catch (e) {
    console.error("recipe generation failed", e);
    return Response.json({ error: "Couldn't fetch recipes right now" }, { status: 502 });
  }
}
