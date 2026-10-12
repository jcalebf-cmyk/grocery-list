const API = "https://www.googleapis.com/youtube/v3";

export interface Video {
  id: string;
  title: string;
  channel: string;
  thumbnailUrl: string;
}

function key(): string {
  const k = process.env.YOUTUBE_API_KEY;
  if (!k) throw new Error("YOUTUBE_API_KEY is not set");
  return k;
}

async function get(path: string, params: Record<string, string>): Promise<any> {
  const url = new URL(`${API}/${path}`);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  url.searchParams.set("key", key());
  const res = await fetch(url);
  if (!res.ok) throw new Error(`YouTube ${path} failed: ${res.status} ${await res.text()}`);
  return res.json();
}

/**
 * Top results for a search, Shorts excluded. Costs 100 quota units per call (of 10,000/day) whatever
 * the result count, so callers should keep searches few.
 */
export async function searchVideos(query: string, max = 3): Promise<Video[]> {
  const data = await get("search", {
    part: "snippet",
    type: "video",
    q: query,
    maxResults: String(max),
    videoDuration: "medium", // 4-20 minutes: skips Shorts, whose thumbnails are portrait
    videoEmbeddable: "true",
    safeSearch: "strict",
    relevanceLanguage: "en",
  });
  return (data.items ?? [])
    .filter((item: any) => item.id?.videoId)
    .map((item: any) => ({
      id: item.id.videoId,
      title: decodeEntities(item.snippet.title),
      channel: decodeEntities(item.snippet.channelTitle),
      thumbnailUrl:
        item.snippet.thumbnails?.high?.url ?? item.snippet.thumbnails?.medium?.url ?? item.snippet.thumbnails?.default?.url ?? "",
    }));
}

/** Search snippets come HTML-escaped ("Mom&#39;s Kitchen"). */
function decodeEntities(text: string): string {
  return text
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
    .replace(/&quot;/g, '"')
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}
