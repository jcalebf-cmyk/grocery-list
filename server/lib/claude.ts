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

export async function writeRecipes(items: string[], onHand: string[]): Promise<WrittenRecipe[]> {
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
- at most 10 ingredients, not counting salt, pepper, oil, and water
- at most about 45 minutes total
- nothing beyond a normal home kitchen
- no desserts or drinks

Build at least four recipes around what's already in their kitchen, if there is anything, then use their regular groceries. Skip non-food items. Keep the set varied: different main ingredients and cuisines, not six chicken dishes.

For each card:
- title: a short plain name for the dish
- ingredients: everything the dish needs, each with a quantity in US kitchen measures for 2-4 servings (e.g. "1 lb", "2 cups", "1 tbsp")
- steps: at most 6 short steps in plain language, with times and temperatures where they matter
- usesHouseholdItems: which of the household's ingredients (from either list) the recipe uses, spelled as in their list
- videoQuery: a short YouTube search that would find a video of this same dish, like "easy chicken broccoli stir fry"`,
      },
    ],
  });
  if (response.stop_reason === "refusal") return [];
  return response.parsed_output?.recipes ?? [];
}
