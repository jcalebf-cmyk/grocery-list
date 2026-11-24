import { z } from "zod";
import type { ChatTurn } from "./claude.js";

// What the apps send back for a card Claude gave earlier. Looser than any card Claude writes (its whole reply
// fits in 16,000 tokens), but bounded: the app is trusted with the conversation, so anyone can send their own.
const Card = z.object({
  title: z.string().max(500),
  description: z.string().max(4000),
  servings: z.number().int(),
  totalMinutes: z.number().int(),
  ingredients: z.array(z.object({ name: z.string().max(500), quantity: z.string().max(500) })).max(150),
  steps: z.array(z.string().max(4000)).max(100),
  notes: z.string().max(4000),
});

export const Turn = z.object({
  role: z.enum(["user", "assistant"]),
  text: z.string().trim().max(4000),
  recipes: z.array(Card).max(3).optional(),
});

/**
 * The most conversation one chat message can have Claude read. Counted in bytes because a token is at least a
 * byte in any language, so this also keeps prompts under 100,000 tokens, where Haiku's price goes up.
 */
export const MAX_CONVERSATION_BYTES = 60_000;

/**
 * The newest message, and as many of the question-and-answer pairs before it as fit, newest first. Expects
 * turns that alternate, starting and ending with the user's.
 */
export function recentTurns(turns: ChatTurn[], maxBytes = MAX_CONVERSATION_BYTES): ChatTurn[] {
  let start = turns.length - 1;
  let bytes = turnBytes(turns[start]);
  while (start >= 2) {
    bytes += turnBytes(turns[start - 1]) + turnBytes(turns[start - 2]);
    if (bytes > maxBytes) break;
    start -= 2;
  }
  return turns.slice(start);
}

function turnBytes(turn: ChatTurn): number {
  return Buffer.byteLength(turn.text) + (turn.recipes?.length ? Buffer.byteLength(JSON.stringify(turn.recipes)) : 0);
}
