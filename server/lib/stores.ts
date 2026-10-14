// Nearby grocery stores. Uses Google Places when GOOGLE_PLACES_API_KEY is set (reliable, small free
// monthly allowance); otherwise OpenStreetMap's Overpass API (free and keyless, but often overloaded).
// The main instance is often too busy (504), so fall back to public mirrors in order.
const OVERPASS_URLS = [
  "https://overpass-api.de/api/interpreter",
  "https://overpass.kumi.systems/api/interpreter",
  "https://overpass.private.coffee/api/interpreter",
];
const RADIUS_METERS = 16_000; // about 10 miles
const MAX_STORES = 15;

// Department stores are mostly not grocery stores; these two are the exceptions people shop at for food.
const GROCERY_DEPARTMENT_STORES = new Set(["target", "walmart", "walmart supercenter"]);

export interface NearbyStore {
  name: string;
  distanceMeters: number;
  // So the app can re-measure distances while it reuses a saved lookup.
  lat: number;
  lng: number;
}

export async function findNearbyStores(lat: number, lng: number): Promise<NearbyStore[]> {
  const places = process.env.GOOGLE_PLACES_API_KEY
    ? await fromGooglePlaces(lat, lng, process.env.GOOGLE_PLACES_API_KEY)
    : await fromOpenStreetMap(lat, lng);
  return closestPerChain(places, lat, lng);
}

interface Place {
  name: string;
  lat: number;
  lng: number;
}

async function fromGooglePlaces(lat: number, lng: number, key: string): Promise<Place[]> {
  const res = await fetch("https://places.googleapis.com/v1/places:searchNearby", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": key,
      // Only these fields, which keeps each call in the cheaper pricing tier.
      "X-Goog-FieldMask": "places.displayName,places.location",
    },
    body: JSON.stringify({
      includedTypes: ["grocery_store", "supermarket", "warehouse_store"],
      maxResultCount: 20,
      rankPreference: "DISTANCE",
      locationRestriction: { circle: { center: { latitude: lat, longitude: lng }, radius: RADIUS_METERS } },
    }),
    signal: AbortSignal.timeout(15_000),
  });
  if (!res.ok) throw new Error(`Google Places failed: ${res.status} ${await res.text()}`);
  const data: { places?: any[] } = await res.json();
  return (data.places ?? []).flatMap((p) => {
    const name = p.displayName?.text?.trim();
    const loc = p.location;
    return name && loc ? [{ name, lat: loc.latitude, lng: loc.longitude }] : [];
  });
}

async function fromOpenStreetMap(lat: number, lng: number): Promise<Place[]> {
  const query = `[out:json][timeout:25];
nwr["shop"~"^(supermarket|wholesale|department_store)$"](around:${RADIUS_METERS},${lat},${lng});
out center tags;`;
  const data = await queryOverpass(query);
  return (data.elements ?? []).flatMap((el) => {
    const tags = el.tags ?? {};
    const name: string | undefined = (tags.brand ?? tags.name)?.trim();
    if (!name) return [];
    if (tags.shop === "department_store" && !GROCERY_DEPARTMENT_STORES.has(name.toLowerCase())) return [];
    const elLat = el.lat ?? el.center?.lat;
    const elLng = el.lon ?? el.center?.lon;
    return elLat == null || elLng == null ? [] : [{ name, lat: elLat, lng: elLng }];
  });
}

async function queryOverpass(query: string): Promise<{ elements?: any[] }> {
  const failures: string[] = [];
  for (const url of OVERPASS_URLS) {
    try {
      const res = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "User-Agent": "grocery-list-app (https://github.com/jcalebf-cmyk/grocery-list)",
        },
        body: new URLSearchParams({ data: query }),
        signal: AbortSignal.timeout(25_000),
      });
      if (res.ok) return await res.json();
      failures.push(`${url}: ${res.status}`);
    } catch (e) {
      failures.push(`${url}: ${e instanceof Error ? e.message : e}`);
    }
  }
  throw new Error(`All Overpass servers failed: ${failures.join("; ")}`);
}

/** Groups branches of the same chain and keeps the closest one. */
function closestPerChain(places: Place[], fromLat: number, fromLng: number): NearbyStore[] {
  const closest = new Map<string, NearbyStore>();
  for (const p of places) {
    const distanceMeters = Math.round(haversineMeters(fromLat, fromLng, p.lat, p.lng));
    const key = p.name.toLowerCase();
    const existing = closest.get(key);
    if (!existing || distanceMeters < existing.distanceMeters) {
      closest.set(key, { name: p.name, distanceMeters, lat: p.lat, lng: p.lng });
    }
  }
  return [...closest.values()].sort((a, b) => a.distanceMeters - b.distanceMeters).slice(0, MAX_STORES);
}

function haversineMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.sqrt(a));
}
