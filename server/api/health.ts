/** GET /api/health – confirms the deployment is up and its keys are configured (without revealing them). */
export function GET(): Response {
  return Response.json({
    ok: true,
    youtubeKey: Boolean(process.env.YOUTUBE_API_KEY),
    anthropicKey: Boolean(process.env.ANTHROPIC_API_KEY),
    resendKey: Boolean(process.env.RESEND_API_KEY),
    firebaseServiceAccount: Boolean(process.env.FIREBASE_SERVICE_ACCOUNT),
    commit: process.env.VERCEL_GIT_COMMIT_SHA?.slice(0, 7) ?? null,
  });
}
