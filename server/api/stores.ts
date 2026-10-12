import { z } from "zod";
import { verifyFirebaseUser } from "../lib/auth.js";
import { findNearbyStores } from "../lib/stores.js";

const Body = z.object({ lat: z.number().min(-90).max(90), lng: z.number().min(-180).max(180) });

/**
 * POST /api/stores  { lat, lng }
 * Requires a Firebase ID token. The app rounds coordinates to ~1 km before sending. Searches a 10-mile radius.
 * Returns { stores: [{ name, distanceMeters }] }, one entry per chain, closest first.
 */
export async function POST(request: Request): Promise<Response> {
  const uid = await verifyFirebaseUser(request);
  if (!uid) return Response.json({ error: "Sign in required" }, { status: 401 });

  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return Response.json({ error: "Send { lat: number, lng: number }" }, { status: 400 });

  try {
    const stores = await findNearbyStores(body.data.lat, body.data.lng);
    return Response.json({ stores });
  } catch (e) {
    console.error("store lookup failed", e);
    return Response.json({ error: "Couldn't find nearby stores right now" }, { status: 502 });
  }
}
