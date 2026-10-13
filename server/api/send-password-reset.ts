import { createHash } from "node:crypto";
import { ipAddress, waitUntil } from "@vercel/functions";
import { z } from "zod";
import { sendEmail, toOurPage } from "../lib/email.js";
import { resetPassword } from "../lib/emailTemplates.js";
import { adminAuth } from "../lib/firebaseAdmin.js";
import { overDailyLimit, tooManyRequests } from "../lib/rateLimit.js";

// Anyone can call this (the user is signed out), so limit it per address and per network.
const DAILY_LIMIT_PER_EMAIL = 5;
const DAILY_LIMIT_PER_IP = 20;

const Body = z.object({ email: z.string().trim().toLowerCase().max(320) });

/**
 * POST /api/send-password-reset  { email }
 * No sign-in needed. Emails a password reset link if the address has an account. Always replies
 * { ok: true }, straight away, so neither the answer nor its timing reveals who has an account.
 */
export async function POST(request: Request): Promise<Response> {
  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return Response.json({ error: "Send { email: string }" }, { status: 400 });
  const email = body.data.email;

  const emailKey = createHash("sha256").update(email).digest("hex"); // keeps addresses out of the cache
  if (
    (await overDailyLimit("reset-ip", ipAddress(request) ?? "unknown", DAILY_LIMIT_PER_IP)) ||
    (await overDailyLimit("reset-email", emailKey, DAILY_LIMIT_PER_EMAIL))
  ) {
    return tooManyRequests("Password reset emails are limited for today");
  }

  waitUntil(sendReset(email));
  return Response.json({ ok: true });
}

async function sendReset(email: string): Promise<void> {
  if (!z.email().safeParse(email).success) return;
  try {
    const auth = adminAuth();
    const user = await auth.getUserByEmail(email);
    const link = await auth.generatePasswordResetLink(email);
    await sendEmail(resetPassword({ email: user.email ?? email, displayName: user.displayName }, toOurPage(link)));
  } catch (e) {
    if ((e as { code?: string }).code === "auth/user-not-found") return;
    console.error("password reset email failed", e);
  }
}
