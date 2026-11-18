const API = "https://www.googleapis.com/youtube/v3";

export interface VideoDetails {
  id: string;
  url: string;
  title: string;
  channel: string;
  channelId: string;
  description: string;
  /** The creator's own comments, top ones first. Recipes are often in a pinned one. */
  creatorComments: string[];
}

/**
 * The video ID if `text` is a YouTube link with little else around it (a share sheet's "Check out this
 * video" is fine). Covers watch, youtu.be, Shorts, live, and embed links.
 */
export function youTubeVideoId(text: string): string | null {
  const match = text.match(
    /https?:\/\/(?:www\.|m\.|music\.)?(?:youtube\.com\/(?:watch\?(?:[^\s#]*&)?v=|shorts\/|live\/|embed\/)|youtu\.be\/)([A-Za-z0-9_-]{11})\S*/,
  );
  if (!match) return null;
  const rest = text.replace(match[0], "").trim();
  return rest.length <= 80 ? match[1] : null; // a longer paste is a recipe that happens to mention a video
}

function apiUrl(path: string, params: Record<string, string>): URL {
  const key = process.env.YOUTUBE_API_KEY;
  if (!key) throw new Error("YOUTUBE_API_KEY is not set");
  const url = new URL(`${API}/${path}`);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  url.searchParams.set("key", key);
  return url;
}

/**
 * Title, channel, description, and the creator's own comments, through YouTube's official API. Costs 2
 * quota units (of 10,000/day).
 */
export async function videoDetails(id: string): Promise<VideoDetails | null> {
  const res = await fetch(apiUrl("videos", { part: "snippet", id }));
  if (!res.ok) throw new Error(`YouTube videos failed: ${res.status} ${await res.text()}`);
  const snippet = (await res.json()).items?.[0]?.snippet;
  if (!snippet) return null; // private, removed, or a bad ID
  const channelId = snippet.channelId ?? "";
  return {
    id,
    url: `https://www.youtube.com/watch?v=${id}`,
    title: snippet.title ?? "",
    channel: snippet.channelTitle ?? "",
    channelId,
    description: snippet.description ?? "",
    creatorComments: await creatorComments(id, channelId),
  };
}

/**
 * The creator's top-level comments among the video's 25 most relevant. The API doesn't say which comment is
 * pinned, so this keeps any the creator wrote; a pinned recipe should be near the top. Empty when comments
 * are off or the call fails: the description may still have the recipe.
 */
async function creatorComments(videoId: string, channelId: string): Promise<string[]> {
  if (!channelId) return [];
  try {
    const res = await fetch(
      apiUrl("commentThreads", { part: "snippet", videoId, order: "relevance", maxResults: "25", textFormat: "plainText" }),
    );
    if (!res.ok) {
      console.warn(`YouTube comments unavailable for ${videoId}: ${res.status}`); // 403 when comments are off
      return [];
    }
    return ((await res.json()).items ?? [])
      .map((item: any) => item.snippet?.topLevelComment?.snippet)
      .filter((c: any) => c?.authorChannelId?.value === channelId && c.textDisplay)
      .map((c: any) => String(c.textDisplay))
      .slice(0, 3);
  } catch (e) {
    console.warn(`YouTube comments failed for ${videoId}`, e);
    return [];
  }
}
