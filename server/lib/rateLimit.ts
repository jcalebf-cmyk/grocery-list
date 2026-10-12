import { getCache } from "@vercel/functions";

const DAY_SECONDS = 24 * 60 * 60;

/**
 * Per-user daily limits, counted in Vercel's Runtime Cache. The cache has no atomic increment, so
 * simultaneous requests can occasionally slip one past the limit; that's fine for a guard against
 * runaway usage. If the cache is unavailable, requests are allowed rather than blocking users.
 */
export async function overDailyLimit(scope: string, uid: string, limit: number): Promise<boolean> {
  const day = Math.floor(Date.now() / 1000 / DAY_SECONDS);
  const key = `ratelimit:${scope}:${uid}:${day}`;
  try {
    const cache = getCache();
    const used = Number((await cache.get(key)) ?? 0);
    if (used >= limit) return true;
    await cache.set(key, used + 1, { ttl: DAY_SECONDS, name: `${scope}-rate-limit` });
    return false;
  } catch (e) {
    console.warn("rate limit check failed; allowing request", e);
    return false;
  }
}

export function tooManyRequests(message: string): Response {
  const secondsUntilTomorrow = DAY_SECONDS - (Math.floor(Date.now() / 1000) % DAY_SECONDS);
  return Response.json({ error: message }, { status: 429, headers: { "Retry-After": String(secondsUntilTomorrow) } });
}
