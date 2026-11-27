import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { z } from "zod";
import type { VideoDetails } from "./youtube.js";

const client = new Anthropic(); // reads ANTHROPIC_API_KEY
const MODEL = "claude-haiku-5-5"; // imports: Josh's choice, for cost
// The recipe chat writes recipes itself, so it gets the stronger model (Josh, Nov 26: Haiku's recipes were too plain).
// Effort stays low so a reply lands well inside the apps' 60-second timeout.
const CHAT_MODEL = "claude-sonnet-5-5";

/**
 * The recipe card both endpoints return, and the shape the apps save. Same fields as Android's Recipe
 * and iOS's Recipe (minus what the apps add: id, source, timestamps). 0 means "not given" for the numbers.
 */
export const RecipeSchema = z.object({
  title: z.string(),
  description: z.string(),
  servings: z.number().int(),
  totalMinutes: z.number().int(),
  ingredients: z.array(z.object({ name: z.string(), quantity: z.string() })),
  steps: z.array(z.string()),
  notes: z.string(),
});
export type Recipe = z.infer<typeof RecipeSchema>;

const ChatReplySchema = z.object({
  reply: z.string(),
  recipes: z.array(RecipeSchema),
});
export type ChatReply = z.infer<typeof ChatReplySchema>;

export interface ChatTurn {
  role: "user" | "assistant";
  text: string;
  recipes?: Recipe[]; // assistant turns: the cards it gave, so follow-ups can change them
}

export interface Kitchen {
  onHand: string[]; // meat, produce, staples
  pantry: string[]; // seasonings and sauces
}

/** The kitchen is a hint only: suggestions must never be limited to it. */
function describeKitchen({ onHand, pantry }: Kitchen): string {
  if (onHand.length + pantry.length === 0) return "";
  const parts = ["For reference, here's what this household already has at home."];
  if (onHand.length > 0) parts.push(`In their kitchen:\n${onHand.map((i) => `- ${i}`).join("\n")}`);
  if (pantry.length > 0) parts.push(`Seasonings and sauces:\n${pantry.map((i) => `- ${i}`).join("\n")}`);
  parts.push(
    "This is only background. Suggest whatever best fits what they ask for, and never leave out or swap an ingredient because it isn't listed here: the app adds anything they're missing to their shopping list. Only build a recipe around what they have when they ask you to (for example, \"what can I make with what I have?\"). You can briefly mention when a recipe uses something they already have.",
  );
  return parts.join("\n\n");
}

const CARD_RULES = `Each recipe card:
- title: a short plain name for the dish
- description: one or two sentences on what the dish is and what makes it good
- servings: how many people it serves (4 unless they say otherwise)
- totalMinutes: total time including prep
- ingredients: everything the dish needs except water, each with a quantity in US kitchen measures (e.g. "1.5 lb", "2 cups", "1 tbsp", "to taste"). name is what someone would buy at the store ("boneless skinless chicken thighs", "yellow onion"), without the amount or prep; put prep in quantity if it matters ("1 large, diced").
- steps: as many steps as the dish needs (usually 5 to 12) in plain language, starting with any prep. Each step is one to three sentences and says the heat level, how long, and how to tell it's done (e.g. "until golden at the edges, about 4 minutes").
- notes: one short, practical tip: a swap, a make-ahead note, or a common mistake to avoid.`;

const CHAT_SYSTEM = `You help a household decide what to cook, inside their shared grocery list app. They chat with you to get recipe suggestions, like "boneless chicken thighs in the dutch oven" or "a quick vegetarian dinner".

How to answer:
- reply: a short, friendly message, one to three sentences. Don't repeat the recipe's contents here; the app shows the recipe as a card under your message.
- recipes: when they ask for a recipe or ideas, include recipe cards. Give one recipe unless they ask for several (at most 3). When they ask to change a recipe you already gave ("make it spicier", "for 6 people", "no dutch oven"), return the whole updated recipe. When the message doesn't call for a recipe (a substitution, a technique, a question about a step), answer in reply and leave recipes empty.
- Respect the equipment, ingredients, time, and diet they mention.
- Write the best version of the dish, the way a skilled home cook would make it, not the plainest one: build flavor with real technique (a good sear, aromatics, seasoning in layers, a finish of acid, herbs or fat) and say why a step matters when it isn't obvious. Keep it doable in a normal home kitchen with ingredients from a regular grocery store.
- Stay on cooking and food. If they ask about something unrelated, say briefly that you can only help with recipes and cooking.

${CARD_RULES}`;

/** One chat turn: the reply to the last user message, given the conversation so far. */
export async function chat(turns: ChatTurn[], kitchen: Kitchen): Promise<ChatReply | null> {
  const kitchenText = describeKitchen(kitchen);
  const response = await client.messages.parse({
    model: CHAT_MODEL,
    max_tokens: 16000,
    output_config: { effort: "low", format: zodOutputFormat(ChatReplySchema) },
    system: kitchenText ? `${CHAT_SYSTEM}\n\n${kitchenText}` : CHAT_SYSTEM,
    // Earlier replies go back as the JSON they were written as, so the model sees its own recipes.
    messages: turns.map((t) => ({
      role: t.role,
      content: t.role === "user" ? t.text : JSON.stringify({ reply: t.text, recipes: t.recipes ?? [] }),
    })),
  });
  if (response.stop_reason === "refusal") return null;
  return response.parsed_output ?? null;
}

const VERBATIM_RULES = `- title: the recipe's name as given; if it has none, a short plain name for the dish.
- description: the author's own one- or two-sentence summary if there is one; otherwise empty. Skip life stories and ads.
- servings: as stated, or 0 if not stated.
- totalMinutes: the total time as stated (add prep and cook time if they're given separately), or 0 if not stated.
- ingredients: every ingredient, in order. name is what someone would buy at the store, without the amount or prep ("yellow onion" from "1 large yellow onion, diced"). quantity is the amount and any prep exactly as written ("1 large, diced"), or empty if none is given. For a heading like "For the sauce:", keep the ingredients and drop the heading.
- steps: the method, word for word, split into the author's steps. Remove step numbers and bullets, but don't change the wording. If the method is one paragraph, split it at sentence boundaries into sensible steps without rewording.
- notes: any notes, tips, or variations from the source, word for word; otherwise empty.`;

const ImportSchema = z.object({
  isRecipe: z.boolean(),
  recipe: RecipeSchema,
});

const IMPORT_SYSTEM = `You turn recipes that people paste into a grocery list app (from a website, a message, a note, a social media caption) into a structured recipe card. Keep the recipe as its author wrote it: this may be a family recipe, so don't reword, improve, or add to it.

- isRecipe: false if the text isn't a recipe at all (then fill the recipe fields with empty values).
${VERBATIM_RULES}`;

/** Structures a pasted recipe. Null if the text isn't a recipe. */
export async function importRecipe(text: string): Promise<Recipe | null> {
  const response = await client.messages.parse({
    model: MODEL,
    max_tokens: 16000,
    output_config: { effort: "low", format: zodOutputFormat(ImportSchema) },
    system: IMPORT_SYSTEM,
    messages: [{ role: "user", content: `<pasted_recipe>\n${text}\n</pasted_recipe>` }],
  });
  if (response.stop_reason === "refusal") return null;
  const parsed = response.parsed_output;
  if (!parsed?.isRecipe || parsed.recipe.ingredients.length + parsed.recipe.steps.length === 0) return null;
  return parsed.recipe;
}

const VideoSchema = z.object({
  isCookingVideo: z.boolean(),
  recipeFromCreator: z.boolean(),
  recipe: RecipeSchema,
});

const VIDEO_SYSTEM = `People paste YouTube links into a grocery list app to save the recipe from a cooking video. You get the video's title, channel, description, and any comments the creator posted on it (creators often pin a comment with the recipe). You can't watch the video.

- isCookingVideo: false if the video clearly isn't about cooking or a dish (then fill the recipe fields with empty values).
- recipeFromCreator: true if the description or one of the creator's comments includes the recipe's ingredient list. Then copy the recipe from there as the creator wrote it, following the rules below. If the creator lists ingredients but not the method, leave steps empty: they'll watch the video for those.
- Otherwise (no ingredient list from the creator, or only a link to a website), set recipeFromCreator to false and write a recipe for the dish the video makes, using the title and anything the creator says about it. Follow their lead where they give hints (main ingredients, equipment, style). The app tells the user this recipe was written for them and may differ from the video.

When copying from the creator:
${VERBATIM_RULES}
When writing the recipe yourself:
${CARD_RULES}`;

/**
 * The recipe from a cooking video: copied from its description or the creator's comments when they have it,
 * otherwise written for the dish in the title. Null if it isn't a cooking video.
 */
export async function recipeFromVideo(video: VideoDetails): Promise<{ recipe: Recipe; fromCreator: boolean } | null> {
  const response = await client.messages.parse({
    model: MODEL,
    max_tokens: 16000,
    output_config: { effort: "low", format: zodOutputFormat(VideoSchema) },
    system: VIDEO_SYSTEM,
    messages: [
      {
        role: "user",
        content: [
          `<video_title>${video.title}</video_title>`,
          `<channel>${video.channel}</channel>`,
          `<description>\n${video.description.slice(0, 10_000)}\n</description>`,
          ...video.creatorComments.map((c) => `<creator_comment>\n${c.slice(0, 10_000)}\n</creator_comment>`),
        ].join("\n"),
      },
    ],
  });
  if (response.stop_reason === "refusal") return null;
  const parsed = response.parsed_output;
  if (!parsed?.isCookingVideo || parsed.recipe.ingredients.length === 0) return null;
  return { recipe: parsed.recipe, fromCreator: parsed.recipeFromCreator };
}
