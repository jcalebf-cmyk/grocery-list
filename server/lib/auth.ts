import { createRemoteJWKSet, jwtVerify } from "jose";

// Firebase ID tokens are JWTs signed by Google; verifying them needs only the project ID, not a service account.
const FIREBASE_PROJECT_ID = "grocery-list-d5a77";
const jwks = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);

/** Returns the Firebase user ID, or null if the request isn't from a signed-in app user. */
export async function verifyFirebaseUser(request: Request): Promise<string | null> {
  const token = request.headers.get("authorization")?.match(/^Bearer (.+)$/)?.[1];
  if (!token) return null;
  try {
    const { payload } = await jwtVerify(token, jwks, {
      issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`,
      audience: FIREBASE_PROJECT_ID,
    });
    return payload.sub ?? null;
  } catch {
    return null;
  }
}
