import { test } from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { discoverModels, catalogFor, claudeModelName, parseGeminiModels, FALLBACK_MODELS, DISCOVERY_ARGS } from "../src/provider-models.js";

const fixture = (name: string) => fileURLToPath(new URL("./fixtures/" + name, import.meta.url));

test("a model list is asked of the CLI that owns it, not hardcoded", async () => {
  const acp = await discoverModels("opencode", {
    command: process.execPath,
    args: [fixture("acp-cli.mjs")],
    cwd: process.cwd(),
    env: process.env,
    timeoutMs: 5000,
  });
  assert.deepEqual(
    acp?.map((model) => model.id),
    ["vendor/model-one", "vendor/model-two"]
  );
  assert.equal(acp?.[0]?.name, "Vendor Model One");
  assert.equal(acp?.[1]?.description, "");
});

test("each provider is asked with the arguments its own CLI needs", () => {
  assert.deepEqual(DISCOVERY_ARGS.opencode, [["acp"]]);
  assert.deepEqual(DISCOVERY_ARGS.cursor, [["acp"]]);
  assert.deepEqual(DISCOVERY_ARGS.grok, [["--no-auto-update", "agent", "stdio"]]);
  assert.deepEqual(DISCOVERY_ARGS.codex[0], ["app-server", "--listen", "stdio://"]);
});

test("Codex is asked for its model list over the app server", async () => {
  const codex = await discoverModels("codex", {
    command: process.execPath,
    args: [fixture("codex-app-server.mjs")],
    cwd: process.cwd(),
    env: process.env,
    timeoutMs: 5000,
  });
  assert.deepEqual(
    codex?.map((model) => model.id),
    ["gpt-6-luna", "gpt-5.6-sol"]
  );
  assert.equal(codex?.[0]?.name, "GPT-6 Luna");
});

test("a CLI that cannot be asked leaves the shipped list in place", async () => {
  const discovered = await discoverModels("gemini", { command: process.execPath, cwd: process.cwd(), env: process.env, timeoutMs: 2000 });
  assert.equal(discovered, null);
  const catalog = catalogFor("gemini", discovered);
  assert.equal(catalog.source, "fallback");
  assert.equal(catalog.models, FALLBACK_MODELS.gemini);
  assert.ok(catalog.models.length > 0);
});

test("a CLI that answers owns the list the app shows", () => {
  const catalog = catalogFor("codex", [{ id: "from-cli", name: "From the CLI", description: "" }]);
  assert.equal(catalog.source, "cli");
  assert.equal(catalog.models[0]?.id, "from-cli");
});

test("Gemini shows the names the app ships, not the older ones its CLI lists", () => {
  const catalog = catalogFor("gemini", [
    { id: "auto", name: "Auto", description: "" },
    { id: "gemini-2.5-pro", name: "gemini-2.5-pro", description: "" },
  ]);
  assert.equal(catalog.source, "fallback");
  assert.deepEqual(catalog.models.map((m) => m.id), ["gemini-3.8-flash", "gemini-4-argon"]);
});

test("a Claude model id becomes the name a person would say", () => {
  assert.equal(claudeModelName("claude-opus-4-6"), "Claude Opus 4.6");
  assert.equal(claudeModelName("claude-sonnet-4-6-20260101"), "Claude Sonnet 4.6");
  assert.equal(claudeModelName("claude-haiku-4-5[1m]"), "Claude Haiku 4.5 (1M context)");
  assert.equal(claudeModelName("gpt-6-luna"), null);
  assert.equal(claudeModelName("claude-opus"), null);
});

test("a CLI that lists its models in plain text is read", () => {
  const listing = [
    "Available Gemini Models:",
    "",
    "auto (Auto)",
    "  Description:   Let Gemini CLI decide the best model for the task",
    "  Context limit: 1,048,576 input tokens / 8,192 output tokens",
    "",
    "gemini-3.5-flash (gemini-3.5-flash)",
    "  Context limit: 1,048,576 input tokens / 8,192 output tokens",
    "",
    "gemini-2.5-pro (gemini-2.5-pro)",
    "  Context limit: 1,048,576 input tokens / 8,192 output tokens",
  ].join("\n");
  const models = parseGeminiModels(listing);
  assert.deepEqual(models.map((m) => m.id), ["auto", "gemini-3.5-flash", "gemini-2.5-pro"]);
  assert.equal(models[0].name, "Auto");
  assert.equal(models[1].name, "gemini-3.5-flash");
});

test("a Gemini model list is asked of the Gemini CLI", async () => {
  const listing = "Available Gemini Models:\n\ngemini-3.5-flash (gemini-3.5-flash)\n";
  const models = await discoverModels("gemini", {
    command: process.execPath,
    args: ["-e", `console.log(${JSON.stringify(listing)})`],
    cwd: process.cwd(),
    env: process.env,
  });
  assert.deepEqual(models?.map((m) => m.id), ["gemini-3.5-flash"]);
});
