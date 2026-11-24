import { z } from "zod";
import { verifyFirebaseUser } from "../lib/auth.js";
import { readJson } from "../lib/body.js";
import { chat } from "../lib/claude.js";
import { recentTurns, Turn } from "../lib/conversation.js";
import { limitsUnavailable, overDailyLimit, tooManyRequests } from "../lib/rateLimit.js";

const Item = z.string().trim().min(1).max(60);
const Body = z.object({
  // The whole conversation so far, oldest first, ending with the user's new message. The server keeps nothing.
  messages: z.array(Turn).min(1).max(40),
  onHand: z.array(Item).max(40).default([]), // in the kitchen
  pantry: z.array(Item).max(60).default([]), // seasonings and sauces
});

// Well over 40 turns with three long recipes each.
const MAX_BODY_BYTES = 500_000;

// Plenty for a few conversations a day. Each message is one Claude Haiku call.
const DAILY_LIMIT = 40;

/**
 * POST /api/recipe-chat  { messages: [{ role: "user", text: "chicken thighs in the dutch oven" }], onHand, pantry }
 * Requires a Firebase ID token: Authorization: Bearer <token>
 * Returns { reply: "...", recipes: [...] }: a short message plus zero to three recipe cards.
 */
export async function POST(request: Request): Promise<Response> {
  const uid = await verifyFirebaseUser(request);
  if (!uid) return Response.json({ error: "Sign in required" }, { status: 401 });

  const body = Body.safeParse(await readJson(request, MAX_BODY_BYTES));
  if (!body.success) return Response.json({ error: "Send { messages: [{ role, text }] }" }, { status: 400 });
  const { messages, onHand, pantry } = body.data;
  // Turns must alternate, starting and ending with the user.
  const alternates = messages.every((m, i) => m.role === (i % 2 === 0 ? "user" : "assistant"));
  if (!alternates || messages.at(-1)?.role !== "user" || !messages.at(-1)?.text) {
    return Response.json({ error: "Messages must alternate and end with the user's" }, { status: 400 });
  }
  try {
    if (await overDailyLimit("recipe-chat", uid, DAILY_LIMIT)) {
      return tooManyRequests("You've reached today's limit for recipe chat");
    }
  } catch (e) {
    return limitsUnavailable(e);
  }

  try {
    const reply = await chat(recentTurns(messages), { onHand, pantry });
    if (!reply) return Response.json({ error: "Couldn't answer that" }, { status: 502 });
    console.log(`uid=${uid} turns=${messages.length} onHand=${onHand.length} pantry=${pantry.length} recipes=${reply.recipes.length}`);
    return Response.json({ reply: reply.reply, recipes: reply.recipes.slice(0, 3) });
  } catch (e) {
    console.error("recipe chat failed", e);
    return Response.json({ error: "Couldn't answer right now" }, { status: 502 });
  }
}
