import {
  createModels,
  createProvider,
  type Api,
  type Model,
} from "@earendil-works/pi-ai";
import { openAICompletionsApi } from "@earendil-works/pi-ai/api/openai-completions.lazy";
import { openAIResponsesApi } from "@earendil-works/pi-ai/api/openai-responses.lazy";
import { anthropicMessagesApi } from "@earendil-works/pi-ai/api/anthropic-messages.lazy";
import { subscriptions } from "./providers.js";
import type { Settings } from "./storage.js";
export function connection(settings: Settings, vision = false) {
  const model: Model<Api> = {
    id: settings.model,
    name: settings.model,
    api: settings.api,
    provider: settings.provider,
    baseUrl: settings.baseUrl,
    reasoning: false,
    input: vision ? ["text", "image"] : ["text"],
    cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
    contextWindow: settings.contextWindow,
    maxTokens: settings.maxTokens,
  };
  const models = createModels();
  models.setProvider(
    createProvider({
      id: settings.provider,
      baseUrl: settings.baseUrl,
      models: [model],
      auth: {
        apiKey: {
          name: "BYOK",
          resolve: async () => ({
            auth: { apiKey: settings.apiKey || undefined },
          }),
        },
      },
      api:
        settings.api === "anthropic-messages"
          ? anthropicMessagesApi()
          : settings.api === "openai-responses"
            ? openAIResponsesApi()
            : openAICompletionsApi(),
    }),
  );
  return { model, models };
}
export async function testConnection(settings: Settings) {
  if (settings.executionProvider && settings.executionProvider !== "byok") {
    const started=Date.now();const status=(await subscriptions.status()).find(p=>p.id===settings.executionProvider);
    if (!status?.supported || !status.installed) throw Error("Install the selected provider CLI before checking its account");
    if (status.authenticated === false) throw Error("Sign in through the selected provider's terminal login first");
    if (status.authenticated === null) throw Error("This provider does not expose a verified login-status command. Complete its terminal login and send a task to check model access.");
    return {ok:true,latencyMs:Date.now()-started,text:"The vendor CLI reports a signed-in account. Model entitlement and inference were not tested."};
  }
  const { models, model } = connection(settings);
  const started = Date.now();
  const result = await models.completeSimple(
    model,
    {
      messages: [
        { role: "user", content: "Reply with OK only.", timestamp: Date.now() },
      ],
    },
    { signal: AbortSignal.timeout(30000), maxTokens: 64 },
  );
  if (result.stopReason === "error" || result.stopReason === "aborted")
    throw Error(result.errorMessage || "Connection test failed");
  return {
    ok: true,
    latencyMs: Date.now() - started,
    text: result.content
      .filter((c) => c.type === "text")
      .map((c) => c.text)
      .join(""),
  };
}
