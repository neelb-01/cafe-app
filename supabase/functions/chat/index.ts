// The Supabase Edge Function behind the app's "Ask" sheet. Deploying it is
// described in README.md under "AI assistant".
//
// It exists so the Gemini API key stays a function secret and never ships in
// the APK. It reads the menu from menu_items itself, so the model can only
// suggest items that exist, and it returns item ids, sizes and quantities, never
// prices: the app adds suggestions to the cart, and place_order still prices
// the order from the server's menu.

import { ApiError, FinishReason, GoogleGenAI, ThinkingLevel } from "npm:@google/genai@2.30.0";
import { createClient } from "npm:@supabase/supabase-js@2.118.0";

// On the Gemini API's free tier. Free-tier prompts may be used by Google to
// improve its products, which is fine for a menu but not for anything private.
// Flash-Lite with minimal thinking: gemini-3.8-flash (low thinking at best)
// took 40-100 s per reply on the free tier, past the app's 60 s timeout.
const MODEL = "gemini-3.5-flash-lite";

// Anyone holding the app's publishable key can call this, so bound what one
// request can make the model read and write.
const MAX_TURNS = 20;
const MAX_TEXT_CHARS = 1000;
const MAX_TOKENS = 4096;
const MAX_SUGGESTIONS = 4;
const MAX_QUANTITY = 10;

/** DrinkSize names in the app; it decodes these by name. */
const SIZES = ["SMALL", "MEDIUM", "LARGE"] as const;
type Size = (typeof SIZES)[number];

type Turn = { role: "user" | "assistant"; text: string };
type Suggestion = { item_id: number; size: Size; quantity: number };
type Reply = { reply: string; suggestions: Suggestion[] };

type MenuRow = {
  id: number;
  name: string;
  description: string;
  category: string;
  price_cents: number;
  sizable: boolean;
  is_available: boolean;
};

/** What the model must return; enforced by structured outputs. */
const REPLY_SCHEMA = {
  type: "object",
  properties: {
    reply: { type: "string" },
    suggestions: {
      type: "array",
      items: {
        type: "object",
        properties: {
          item_id: { type: "integer" },
          size: { type: "string", enum: [...SIZES] },
          quantity: { type: "integer" },
        },
        required: ["item_id", "size", "quantity"],
        additionalProperties: false,
      },
    },
  },
  required: ["reply", "suggestions"],
  additionalProperties: false,
};

const REFUSAL_REPLY =
  "Sorry, I can't help with that. Ask me about the menu and I'll help you pick something.";

const gemini = new GoogleGenAI({ apiKey: Deno.env.get("GEMINI_API_KEY") });

// Supabase sets these in every function. menu_items is readable with the anon
// key; the service role key is only a fallback for projects without one.
const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "POST only" }, 405);

  let turns: Turn[];
  try {
    turns = parseTurns(await request.json());
  } catch (e) {
    return json({ error: (e as Error).message }, 400);
  }

  const { data: menu, error } = await supabase
    .from("menu_items")
    .select("id, name, description, category, price_cents, sizable, is_available")
    .order("id");
  if (error || !menu?.length) {
    console.error("menu read failed", error);
    return json({ error: "menu unavailable" }, 502);
  }

  try {
    const response = await gemini.models.generateContent({
      model: MODEL,
      // Gemini calls the assistant's turns "model".
      contents: turns.map((turn) => ({
        role: turn.role === "assistant" ? "model" : "user",
        parts: [{ text: turn.text }],
      })),
      config: {
        systemInstruction: systemPrompt(menu as MenuRow[]),
        maxOutputTokens: MAX_TOKENS,
        // Menu chat needs little reasoning, and every thinking token adds wait.
        thinkingConfig: { thinkingLevel: ThinkingLevel.MINIMAL },
        responseMimeType: "application/json",
        responseJsonSchema: REPLY_SCHEMA,
      },
    });

    const finishReason = response.candidates?.[0]?.finishReason;
    if (finishReason === FinishReason.MAX_TOKENS) {
      throw new Error("reply was cut off at maxOutputTokens");
    }
    // Anything else but STOP means a safety or policy filter blocked it.
    if (response.promptFeedback?.blockReason || (finishReason && finishReason !== FinishReason.STOP)) {
      return json({ reply: REFUSAL_REPLY, suggestions: [] });
    }
    if (!response.text) throw new Error("no text in the reply");
    const reply = JSON.parse(response.text) as Reply;

    return json({
      reply: reply.reply.trim(),
      suggestions: checkSuggestions(reply.suggestions, menu as MenuRow[]),
    });
  } catch (e) {
    if (e instanceof ApiError) {
      console.error(`Gemini API error ${e.status}`, e.message);
      // The free tier is rate limited per minute and per day.
      if (e.status === 429) return json({ error: "assistant busy" }, 429);
    } else {
      console.error("assistant failed", e);
    }
    return json({ error: "assistant unavailable" }, 502);
  }
});

/** The conversation from the app: alternating turns, oldest first, ending with the customer's. */
function parseTurns(body: unknown): Turn[] {
  const messages = (body as { messages?: unknown })?.messages;
  if (!Array.isArray(messages) || messages.length === 0) {
    throw new Error("messages must be a non-empty array");
  }
  const turns = messages.slice(-MAX_TURNS).map((message): Turn => {
    const { role, text } = message as Record<string, unknown>;
    if ((role !== "user" && role !== "assistant") || typeof text !== "string" || !text.trim()) {
      throw new Error("each message needs a role of user or assistant and some text");
    }
    return { role, text: text.slice(0, MAX_TEXT_CHARS) };
  });
  // Trimming to MAX_TURNS can leave an assistant turn first, which the API rejects.
  while (turns.length > 0 && turns[0].role !== "user") turns.shift();
  if (turns.length === 0 || turns[turns.length - 1].role !== "user") {
    throw new Error("the conversation must end with the customer's message");
  }
  return turns;
}

/**
 * Drops anything the app should not offer: unknown or sold-out items and
 * repeats. Food has one size, and the app always orders it as SMALL.
 */
function checkSuggestions(suggestions: Suggestion[], menu: MenuRow[]): Suggestion[] {
  const byId = new Map(menu.map((item) => [item.id, item]));
  const seen = new Set<string>();
  const checked: Suggestion[] = [];
  for (const suggestion of suggestions) {
    const item = byId.get(suggestion.item_id);
    if (!item?.is_available) continue;
    const size: Size = item.sizable && SIZES.includes(suggestion.size) ? suggestion.size : "SMALL";
    const key = `${item.id}:${size}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const quantity = Math.min(Math.max(Math.trunc(suggestion.quantity) || 1, 1), MAX_QUANTITY);
    checked.push({ item_id: item.id, size, quantity });
    if (checked.length === MAX_SUGGESTIONS) break;
  }
  return checked;
}

function systemPrompt(menu: MenuRow[]): string {
  const lines = menu.map((item) =>
    [
      item.id,
      item.name,
      item.category,
      `$${(item.price_cents / 100).toFixed(2)}`,
      item.sizable ? "small/medium/large" : "one size",
      item.is_available ? "available" : "sold out",
      item.description,
    ].join(" | ")
  );
  return `You are the ordering assistant in the ADL Cafe app. Customers ask about the menu or describe what they feel like, and you help them choose.

How to reply:
- Keep it short and friendly: two or three sentences of plain text, no markdown or lists.
- Only recommend items on the menu below, by their exact names. If nothing fits, say so and offer the closest options.
- Prices below are for a small; larger drinks cost a little more, and the app shows the exact price before checkout.
- The menu has no ingredient or allergen details beyond the descriptions. For allergies or dietary needs, say what the descriptions suggest and ask the customer to check with the barista before ordering.
- You can't place orders or change the cart. The customer taps Add on your suggestions and checks out in the Cart tab.
- If asked about something unrelated to the cafe, steer back to the menu politely.

Suggestions:
- When you recommend items, or the customer asks for specific ones (for example "a large latte and two croissants"), put each in suggestions with its id, a size and a quantity. Otherwise leave suggestions empty.
- Never suggest a sold-out item.
- Use SMALL for food, and for drinks unless the customer asked for a size. Quantity is 1 unless they asked for more.
- At most ${MAX_SUGGESTIONS} suggestions.

Menu (id | name | category | price | sizes | status | description):
${lines.join("\n")}`;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
