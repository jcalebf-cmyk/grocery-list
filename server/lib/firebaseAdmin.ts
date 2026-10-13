import { cert, getApps, initializeApp } from "firebase-admin/app";
import { getAuth, type Auth } from "firebase-admin/auth";

/**
 * Firebase Admin, used only to create email confirmation and password reset links. Needs the
 * FIREBASE_SERVICE_ACCOUNT env var: the JSON key from Firebase console → Project settings → Service accounts.
 */
export function adminAuth(): Auth {
  if (!getApps().length) {
    const key = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (!key) throw new Error("FIREBASE_SERVICE_ACCOUNT isn't set");
    initializeApp({ credential: cert(JSON.parse(key)) });
  }
  return getAuth();
}
