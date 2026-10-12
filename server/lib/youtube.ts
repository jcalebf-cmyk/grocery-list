const API = "https://www.googleapis.com/youtube/v3";

export interface Video {
  id: string;
  title: string;
  channel: string;
  description: string;
  thumbnailUrl: string;
  durationMinutes: number;
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

/** Costs 100 quota units per call (of 10,000/day), so callers should keep queries few. */
export async function searchVideoIds(query: string, max = 5): Promise<string[]> {
  const data = await get("search", {
    part: "id",
    type: "video",
    q: query,
    maxResults: String(max),
    videoDuration: "medium", // 4-20 minutes: skips Shorts, which rarely list ingredients
    videoEmbeddable: "true",
    safeSearch: "strict",
    relevanceLanguage: "en",
  });
  return (data.items ?? []).map((item: any) => item.id.videoId).filter(Boolean);
}

/** 1 quota unit per call, up to 50 IDs. */
export async function getVideos(ids: string[]): Promise<Video[]> {
  if (ids.length === 0) return [];
  const data = await get("videos", { part: "snippet,contentDetails", id: ids.join(",") });
  return (data.items ?? []).map((item: any) => ({
    id: item.id,
    title: item.snippet.title,
    channel: item.snippet.channelTitle,
    description: item.snippet.description ?? "",
    thumbnailUrl:
      item.snippet.thumbnails?.high?.url ?? item.snippet.thumbnails?.medium?.url ?? item.snippet.thumbnails?.default?.url ?? "",
    durationMinutes: isoDurationToMinutes(item.contentDetails?.duration ?? ""),
  }));
}

function isoDurationToMinutes(iso: string): number {
  const m = iso.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?/);
  if (!m) return 0;
  return Number(m[1] ?? 0) * 60 + Number(m[2] ?? 0) + Math.round(Number(m[3] ?? 0) / 60);
}
