import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { z } from "zod";

const client = new Anthropic(); // reads ANTHROPIC_API_KEY
const MODEL = "claude-haiku-5-5";

export interface RecipeRequest {
  items: string[]; // bought often
  onHand: string[]; // in the kitchen: meat, produce, staples
  pantry: string[]; // seasonings and sauces: flavor, not something to build a dish around
  favorites: string[];
  mood: string[];
  surprise: boolean; // ignore what they have and pick dishes at random from their cuisines
}

/** Describes the household's ingredients for a prompt. On-hand items come first because using them up matters most. */
function describeIngredients({ items, onHand, surprise }: RecipeRequest): string {
  if (surprise) return "";
  const parts: string[] = [];
  if (onHand.length > 0) {
    parts.push(`Already in their kitchen (stocked up, or delivered by a subscription like a meat box):
${onHand.map((i) => `- ${i}`).join("\n")}`);
  }
  if (items.length > 0) {
    parts.push(`Groceries they buy often:
${items.map((i) => `- ${i}`).join("\n")}`);
  }
  return parts.join("\n\n");
}

function describePantry(pantry: string[]): string {
  if (pantry.length === 0) return "";
  return `Seasonings and sauces in their pantry (use these freely for flavor, but don't build a dish around them):
${pantry.map((i) => `- ${i}`).join("\n")}`;
}

// Without ingredients to build on, the model tends to write the same few dishes every time.
// A random handful of mains to aim for makes each request come out different.
const MAINS = [
  "chicken thighs", "chicken breast", "ground beef", "steak", "ground pork", "pork chops", "pork belly",
  "sausage", "shrimp", "salmon", "white fish", "tofu", "eggs", "chickpeas", "lentils", "black beans",
  "noodles", "rice", "mushrooms", "eggplant", "potatoes", "lamb", "ground turkey", "cabbage",
];

function randomMains(count: number): string[] {
  const pool = [...MAINS];
  for (let i = pool.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [pool[i], pool[j]] = [pool[j], pool[i]];
  }
  return pool.slice(0, count);
}

function describeTask(request: RecipeRequest): string {
  const hasIngredients = request.items.length + request.onHand.length > 0;
  if (!request.surprise && hasIngredients) {
    return `Write 6 simple weeknight recipes for this household. Each should be built around 2-3 of their ingredients and be a well-known, reliable dish rather than an experiment.

Build at least four recipes around what's already in their kitchen, if there is anything, then use their regular groceries. Skip non-food items. Keep the set varied: different main ingredients, not six chicken dishes.`;
  }
  return `Write 6 simple weeknight recipes for this household. They want to try something new, so don't plan around what they already have; they'll shop for it. Pick well-known, reliable dishes, but not just the most famous ones, so each set feels like a surprise. For variety, aim for mains like ${randomMains(6).join(", ")} (swap any that don't suit the cuisine).`;
}

const RecipesSchema = z.object({
  recipes: z.array(
    z.object({
      title: z.string(),
      description: z.string(),
      servings: z.number().int(),
      totalMinutes: z.number().int(),
      ingredients: z.array(z.object({ name: z.string(), quantity: z.string() })),
      steps: z.array(z.string()),
      tip: z.string(),
      usesHouseholdItems: z.array(z.string()),
      videoQuery: z.string(),
    }),
  ),
});
export type WrittenRecipe = z.infer<typeof RecipesSchema>["recipes"][number];

/** Favorites tilt the mix; tonight's picks (the mood) are a hard requirement. */
function describeCuisines({ favorites, mood, surprise }: RecipeRequest): string {
  if (mood.length > 0) {
    return `Tonight they're in the mood for ${listOr(mood)} food. Every recipe must be from ${mood.length > 1 ? "one of these cuisines, spread across them" : "that cuisine"}${surprise ? "" : "; use their ingredients where they fit, but the cuisine comes first"}.`;
  }
  if (favorites.length > 0) {
    return surprise
      ? `Cuisines they like: ${favorites.join(", ")}. Every recipe must be from one of these, spread across them.`
      : `Cuisines they like: ${favorites.join(", ")}. Make about four of the recipes from these, and leave the rest open for variety.`;
  }
  return "Keep the set varied across cuisines.";
}

function listOr(items: string[]): string {
  return items.length <= 2 ? items.join(" or ") : `${items.slice(0, -1).join(", ")}, or ${items.at(-1)}`;
}

export async function writeRecipes(request: RecipeRequest): Promise<WrittenRecipe[]> {
  const context = [describeIngredients(request), describePantry(request.pantry)].filter(Boolean).join("\n\n");
  const response = await client.messages.parse({
    model: MODEL,
    max_tokens: 16000,
    output_config: { effort: "low", format: zodOutputFormat(RecipesSchema) },
    system: `You write clear, simple recipe cards for a grocery list app. Someone should be able to cook the dish from the card alone, without watching a video.`,
    messages: [
      {
        role: "user",
        content: `${context ? `${context}\n\n` : ""}${describeTask(request)}

Keep every recipe simple:
- at most 10 ingredients
- at most about 45 minutes total
- nothing beyond a normal home kitchen
- no desserts or drinks

${describeCuisines(request)}

For each card:
- title: a short plain name for the dish
- description: one or two sentences on what the dish is and what makes it good
- servings: how many people it serves (2-4)
- ingredients: everything the dish needs except salt, pepper, oil, and water (ingredients get added to a shopping list, and every kitchen already has these), each with a quantity in US kitchen measures (e.g. "1 lb", "2 cups", "1 tbsp")
- steps: 5 to 10 steps in plain language, starting with any prep (chopping, marinating). Each step is one to three sentences and says the heat level, how long, and how to tell it's done (e.g. "until golden at the edges, about 4 minutes"). Mention salt, pepper, oil, or water here where the dish uses them
- tip: one short, practical tip: a swap, a make-ahead note, or a common mistake to avoid
- usesHouseholdItems: which of the household's kitchen items or regular groceries the recipe uses, spelled as in their list (not pantry seasonings; empty if none were listed)
- videoQuery: a short YouTube search that would find a video of this same dish, like "easy chicken broccoli stir fry"`,
      },
    ],
  });
  if (response.stop_reason === "refusal") return [];
  return response.parsed_output?.recipes ?? [];
}
