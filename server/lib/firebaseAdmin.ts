import { cert, getApps, initializeApp } from "firebase-admin/app";
import { getAuth, type Auth } from "firebase-admin/auth";
import { getFirestore, type Firestore } from "firebase-admin/firestore";

/**
 * Firebase Admin, used to create email confirmation and password reset links, and to save recipes shared
 * from another app. Needs the FIREBASE_SERVICE_ACCOUNT env var: the JSON key from Firebase console →
 * Project settings → Service accounts.
 */
function init(): void {
  if (getApps().length) return;
  const key = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!key) throw new Error("FIREBASE_SERVICE_ACCOUNT isn't set");
  initializeApp({ credential: cert(JSON.parse(key)) });
}

export function adminAuth(): Auth {
  init();
  return getAuth();
}

export function adminDb(): Firestore {
  init();
  return getFirestore();
}
