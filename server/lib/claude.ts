import Anthropic from "@anthropic-ai/sdk";
import { zodOutputFormat } from "@anthropic-ai/sdk/helpers/zod";
import { z } from "zod";

const client = new Anthropic(); // reads ANTHROPIC_API_KEY
const MODEL = "claude-haiku-5-5";

/** Describes the household's ingredients for a prompt. On-hand items come first because using them up matters most. */
function describeIngredients(items: string[], onHand: string[]): string {
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

const RecipesSchema = z.object({
  recipes: z.array(
    z.object({
      title: z.string(),
      totalMinutes: z.number().int(),
      ingredients: z.array(z.object({ name: z.string(), quantity: z.string() })),
      steps: z.array(z.string()),
      usesHouseholdItems: z.array(z.string()),
      videoQuery: z.string(),
    }),
  ),
});
export type WrittenRecipe = z.infer<typeof RecipesSchema>["recipes"][number];

/** Favorites tilt the mix; tonight's picks (the mood) are a hard requirement. */
function describeCuisines(favorites: string[], mood: string[]): string {
  if (mood.length > 0) {
    return `Tonight they're in the mood for ${listOr(mood)} food. Every recipe must be from ${mood.length > 1 ? "one of these cuisines, spread across them" : "that cuisine"}; use their ingredients where they fit, but the cuisine comes first.`;
  }
  if (favorites.length > 0) {
    return `Cuisines they like: ${favorites.join(", ")}. Make about four of the recipes from these, and leave the rest open for variety.`;
  }
  return "Keep the set varied across cuisines.";
}

function listOr(items: string[]): string {
  return items.length <= 2 ? items.join(" or ") : `${items.slice(0, -1).join(", ")}, or ${items.at(-1)}`;
}

export async function writeRecipes(
  items: string[],
  onHand: string[],
  favorites: string[],
  mood: string[],
): Promise<WrittenRecipe[]> {
  const response = await client.messages.parse({
    model: MODEL,
    max_tokens: 16000,
    output_config: { effort: "low", format: zodOutputFormat(RecipesSchema) },
    system: `You write short, simple recipe cards for a grocery list app.`,
    messages: [
      {
        role: "user",
        content: `${describeIngredients(items, onHand)}

Write 6 simple weeknight recipes for this household. Each should be built around 2-3 of their ingredients and be a well-known, reliable dish rather than an experiment. Keep every recipe simple:
- at most 10 ingredients
- at most about 45 minutes total
- nothing beyond a normal home kitchen
- no desserts or drinks

Build at least four recipes around what's already in their kitchen, if there is anything, then use their regular groceries. Skip non-food items. Keep the set varied: different main ingredients, not six chicken dishes.

${describeCuisines(favorites, mood)}

For each card:
- title: a short plain name for the dish
- ingredients: everything the dish needs except salt, pepper, oil, and water (ingredients get added to a shopping list, and every kitchen already has these), each with a quantity in US kitchen measures for 2-4 servings (e.g. "1 lb", "2 cups", "1 tbsp")
- steps: at most 6 short steps in plain language, with times and temperatures where they matter; mention salt, pepper, oil, or water here where the dish uses them
- usesHouseholdItems: which of the household's ingredients (from either list) the recipe uses, spelled as in their list
- videoQuery: a short YouTube search that would find a video of this same dish, like "easy chicken broccoli stir fry"`,
      },
    ],
  });
  if (response.stop_reason === "refusal") return [];
  return response.parsed_output?.recipes ?? [];
}
