import { verifyFirebaseUser } from "../lib/auth.js";
import { sendEmail, toOurPage } from "../lib/email.js";
import { confirmEmail } from "../lib/emailTemplates.js";
import { adminAuth } from "../lib/firebaseAdmin.js";
import { overDailyLimit, tooManyRequests } from "../lib/rateLimit.js";

// One at sign-up plus a few "Resend" taps in Settings.
const DAILY_LIMIT = 5;

/**
 * POST /api/send-verification  {}
 * Requires a Firebase ID token. Emails the signed-in user a link to confirm their email address.
 * Returns { ok: true, alreadyConfirmed } (no email is sent if it's already confirmed).
 */
export async function POST(request: Request): Promise<Response> {
  const uid = await verifyFirebaseUser(request);
  if (!uid) return Response.json({ error: "Sign in required" }, { status: 401 });
  if (await overDailyLimit("verify-email", uid, DAILY_LIMIT)) {
    return tooManyRequests("Confirmation emails are limited for today");
  }

  try {
    const auth = adminAuth();
    const user = await auth.getUser(uid);
    if (!user.email || user.emailVerified) return Response.json({ ok: true, alreadyConfirmed: user.emailVerified });
    const link = await auth.generateEmailVerificationLink(user.email);
    await sendEmail(confirmEmail({ email: user.email, displayName: user.displayName }, toOurPage(link)));
    return Response.json({ ok: true, alreadyConfirmed: false });
  } catch (e) {
    console.error("confirmation email failed", e);
    return Response.json({ error: "Couldn't send the email right now" }, { status: 502 });
  }
}
