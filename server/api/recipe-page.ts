import { LINK_BASE, lookUpSharedRecipe, type SharedRecipe } from "../lib/sharedRecipes.js";
import { youTubeVideoId } from "../lib/youtube.js";

const SITE = "https://www.householdgroceries.app";

/**
 * GET /r/<code> (rewritten to /api/recipe-page?code=<code> in vercel.json)
 * The web page behind a recipe share link, for people without the app, and the preview Messages or WhatsApp show
 * (Open Graph tags). With the app installed, the link opens the app instead (universal links / app links).
 */
export async function GET(request: Request): Promise<Response> {
  const code = new URL(request.url).searchParams.get("code") ?? "";
  try {
    const found = await lookUpSharedRecipe(code);
    if ("missing" in found) {
      return found.missing === "expired"
        ? page(410, "This link has expired", notice("This link has expired", "Recipe links last 30 days. Ask whoever sent it to share it again."))
        : page(404, "Recipe not found", notice("Couldn't find that recipe", "Check the link, or ask whoever sent it to share it again."));
    }
    return page(200, found.recipe.title || "Recipe", recipeBody(found.recipe), {
      description: found.recipe.description,
      image: thumbnail(found.recipe),
      url: LINK_BASE + code,
    });
  } catch (e) {
    console.error("recipe page failed", e);
    return page(502, "Groceries", notice("Couldn't open that recipe", "Try again in a moment."));
  }
}

function recipeBody(r: SharedRecipe): string {
  const image = thumbnail(r);
  const summary = [
    r.totalMinutes > 0 ? `${r.totalMinutes} min` : null,
    r.servings > 0 ? `Serves ${r.servings}` : null,
  ]
    .filter(Boolean)
    .join(" · ");
  const ingredients = r.ingredients
    .map((i) => {
      const name = i.name.trim();
      const quantity = i.quantity.trim();
      return `<li>${quantity ? `<span class="qty">${esc(quantity)}</span> ${esc(name)}` : esc(capitalize(name))}</li>`;
    })
    .join("");
  const steps = r.steps.map((s) => `<li>${esc(s.trim())}</li>`).join("");
  const source = sourceNote(r);
  const link = videoLink(r);
  return `
    ${image && link ? `<a class="thumb" href="${esc(link)}" rel="noopener"><img src="${esc(image)}" alt="Video thumbnail"></a>` : ""}
    <h1>${esc(r.title)}</h1>
    ${r.description.trim() ? `<p class="lead">${esc(r.description.trim())}</p>` : ""}
    ${summary ? `<p class="meta">${esc(summary)}</p>` : ""}
    ${source ? `<p class="source">${source}</p>` : ""}
    ${ingredients ? `<h2>Ingredients</h2><ul class="ingredients">${ingredients}</ul>` : ""}
    ${steps ? `<h2>Steps</h2><ol class="steps">${steps}</ol>` : ""}
    ${r.notes.trim() ? `<h2>Notes</h2><p class="notes">${esc(r.notes.trim())}</p>` : ""}
    <div class="save">Have the Groceries app? Open this link on your phone to save the recipe to your household.</div>`;
}

/** Same wording as `sourceNote` in RecipeScreen.kt and RecipeView.swift, with the video linked. */
function sourceNote(r: SharedRecipe): string | null {
  const channel = r.sourceName.trim() ? ` by ${esc(r.sourceName.trim())}` : "";
  const link = videoLink(r);
  const video = link ? `<a href="${esc(link)}" rel="noopener">a video${channel}</a>` : `a video${channel}`;
  if (r.source === "youtube") return `From the creator's notes on ${video}`;
  if (r.source === "ai" && r.sourceUrl) {
    const theVideo = link ? `<a href="${esc(link)}" rel="noopener">the video</a>` : "the video";
    return `The video${channel} didn't come with the recipe written out, so AI wrote this one for the same dish. It may differ from ${theVideo}.`;
  }
  if (r.source === "ai") return "Suggested by AI";
  return null;
}

/** The video's address, only if it's http(s): recipe documents are written by the apps, so never trust it as a link. */
function videoLink(r: SharedRecipe): string | null {
  try {
    const url = new URL(r.sourceUrl.trim());
    return url.protocol === "https:" || url.protocol === "http:" ? url.href : null;
  } catch {
    return null;
  }
}

/** The YouTube thumbnail, as the apps show it. */
function thumbnail(r: SharedRecipe): string | null {
  const id = r.sourceUrl ? youTubeVideoId(r.sourceUrl) : null;
  return id ? `https://i.ytimg.com/vi/${id}/hqdefault.jpg` : null;
}

function notice(title: string, text: string): string {
  return `
    <div class="badge"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.5v.01"/></svg></div>
    <h1>${esc(title)}</h1>
    <p>${esc(text)}</p>`;
}

function page(
  status: number,
  title: string,
  body: string,
  preview?: { description: string; image: string | null; url: string },
): Response {
  const og = preview
    ? `
<meta property="og:type" content="article">
<meta property="og:site_name" content="Groceries">
<meta property="og:title" content="${esc(title)}">
${preview.description.trim() ? `<meta property="og:description" content="${esc(preview.description.trim())}">\n<meta name="description" content="${esc(preview.description.trim())}">` : ""}
<meta property="og:image" content="${esc(preview.image ?? `${SITE}/icon.png`)}">
<meta property="og:url" content="${esc(preview.url)}">
<meta name="twitter:card" content="${preview.image ? "summary_large_image" : "summary"}">`
    : "";
  const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="robots" content="noindex">
<meta name="referrer" content="no-referrer">
<meta name="color-scheme" content="light dark">
<title>${esc(title)}</title>${og}
<link rel="icon" href="/icon.png">
<link rel="apple-touch-icon" href="/icon.png">
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
<style>
  :root {
    --bg: #FBF8F2; --card: #FFFFFF; --line: #EBE6DC; --ink: #1C1B17; --body: #4A473F; --muted: #7A766C;
    --green: #1F6B3A; --green-soft: #C9F0D3; --on-green-soft: #002110; --amber-soft: #FFDDAF; --on-amber-soft: #2C1700;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #14130F; --card: #1F1E1A; --line: #33312B; --ink: #EDE8DD; --body: #CFC9BC; --muted: #9C968A;
      --green: #8FD8A5; --green-soft: #0C5128; --on-green-soft: #ABF5C0; --amber-soft: #6A3C00; --on-amber-soft: #FFDDAF;
    }
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; background: var(--bg); color: var(--body);
    font: 16px/1.55 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    display: flex; flex-direction: column; align-items: center; padding: 40px 16px;
  }
  .brand { display: flex; align-items: center; gap: 10px; margin-bottom: 20px; color: var(--ink); font-weight: 700; font-size: 20px; text-decoration: none; }
  .brand img { width: 36px; height: 36px; border-radius: 9px; }
  .card { width: 100%; max-width: 640px; background: var(--card); border: 1px solid var(--line); border-radius: 24px; padding: 32px; overflow-wrap: anywhere; }
  @media (max-width: 480px) { body { padding-top: 24px; } .card { padding: 24px 20px; } }
  .thumb { display: block; margin: -8px -8px 20px; }
  .thumb img { display: block; width: 100%; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 16px; }
  h1 { margin: 0 0 8px; color: var(--ink); font-size: 26px; line-height: 1.25; font-weight: 700; letter-spacing: -0.3px; }
  h2 { margin: 28px 0 10px; color: var(--ink); font-size: 18px; font-weight: 700; }
  p { margin: 0 0 12px; }
  .lead { font-size: 17px; }
  .meta, .source { color: var(--muted); font-size: 14px; }
  a { color: var(--green); }
  ul, ol { margin: 0; padding-left: 22px; }
  li { margin: 0 0 8px; }
  .qty { color: var(--ink); font-weight: 600; }
  .notes { white-space: pre-line; }
  .save { margin-top: 28px; padding: 14px 16px; border-radius: 14px; background: var(--green-soft); color: var(--on-green-soft); font-size: 14px; font-weight: 500; }
  .badge { width: 56px; height: 56px; border-radius: 50%; display: grid; place-items: center; margin-bottom: 20px; background: var(--amber-soft); color: var(--on-amber-soft); }
  .badge svg { width: 28px; height: 28px; }
  footer { margin-top: 24px; font-size: 12px; color: var(--muted); text-align: center; }
</style>
</head>
<body>
  <a class="brand" href="/"><img src="/icon.png" alt="">Groceries</a>
  <main class="card">${body}
  </main>
  <footer>The shared grocery list for your household</footer>
</body>
</html>
`;
  const headers: Record<string, string> = { "Content-Type": "text/html; charset=utf-8" };
  if (status === 200) headers["Cache-Control"] = "private, max-age=60";
  return new Response(html, { status, headers });
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}
