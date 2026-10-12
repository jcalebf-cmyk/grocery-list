/** GET /api/health – confirms the deployment is up and its keys are configured (without revealing them). */
export function GET(): Response {
  return Response.json({
    ok: true,
    youtubeKey: Boolean(process.env.YOUTUBE_API_KEY),
    anthropicKey: Boolean(process.env.ANTHROPIC_API_KEY),
    commit: process.env.VERCEL_GIT_COMMIT_SHA?.slice(0, 7) ?? null,
  });
}
