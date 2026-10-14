import { z } from "zod";
import { verifyFirebaseUser } from "../lib/auth.js";
import { overDailyLimit, tooManyRequests } from "../lib/rateLimit.js";
import { findNearbyStores } from "../lib/stores.js";

// The app caches results for a week per area, so normal use is about one lookup a week.
const DAILY_LIMIT = 20;

const Body = z.object({ lat: z.number().min(-90).max(90), lng: z.number().min(-180).max(180) });

/**
 * POST /api/stores  { lat, lng }
 * Requires a Firebase ID token. Searches a 10-mile radius.
 * Returns { stores: [{ name, distanceMeters, lat, lng }] }, one entry per chain, closest first.
 */
export async function POST(request: Request): Promise<Response> {
  const uid = await verifyFirebaseUser(request);
  if (!uid) return Response.json({ error: "Sign in required" }, { status: 401 });

  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return Response.json({ error: "Send { lat: number, lng: number }" }, { status: 400 });
  if (await overDailyLimit("stores", uid, DAILY_LIMIT)) {
    return tooManyRequests("Nearby store lookups are limited for today");
  }

  try {
    const stores = await findNearbyStores(body.data.lat, body.data.lng);
    return Response.json({ stores });
  } catch (e) {
    console.error("store lookup failed", e);
    return Response.json({ error: "Couldn't find nearby stores right now" }, { status: 502 });
  }
}
