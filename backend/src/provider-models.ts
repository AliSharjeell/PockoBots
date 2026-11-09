import { spawn } from "node:child_process";

/** One model a provider can run. */
export interface ProviderModel {
  id: string;
  name: string;
  description: string;
}

/**
 * The models a provider ships with.
 *
 * These are a safety net, not the source of truth. A signed-in vendor CLI knows its own models and
 * is asked for them over the protocol that CLI speaks, so the lists below only stand in when that
 * request fails. OpenBot keeps the same rule: a hardcoded list is never a claim that a provider is
 * ready.
 */
export const FALLBACK_MODELS: Record<string, ProviderModel[]> = {
  codex: [
    { id: "gpt-6-luna", name: "GPT-6 Luna", description: "Fast and efficient for everyday agent work." },
    { id: "gpt-5.6-luna", name: "GPT-5.6 Luna", description: "A lighter model for quick edits." },
    { id: "gpt-5.6-terra", name: "GPT-5.6 Terra", description: "A balanced model for most work." },
    { id: "gpt-5.6-sol", name: "GPT-5.6 Sol", description: "The strongest model for hard problems." },
  ],
  claude: [
    { id: "default", name: "Claude default", description: "Let Claude Code choose its own model." },
    { id: "claude-opus-5-5", name: "Claude Opus 5.5", description: "The strongest model for hard problems." },
    { id: "claude-sonnet-5-5", name: "Claude Sonnet 5.5", description: "A balanced model for most work." },
    { id: "claude-haiku-5-5", name: "Claude Haiku 5.5", description: "The fastest model for quick edits." },
    { id: "claude-fable-5-1", name: "Claude Fable 5.1", description: "A long-form model for writing." },
  ],
  opencode: [
    { id: "opencode/big-pickle", name: "Big Pickle", description: "Free OpenCode model." },
    { id: "opencode/flint-40b-free", name: "Flint 40B Free", description: "Free OpenCode model." },
    { id: "opencode/glm-5.3-flash-free", name: "GLM-5.3-Flash Free", description: "Free OpenCode model." },
    { id: "opencode/deepseek-v4.1-flash-free", name: "DeepSeek V4.1 Flash Free", description: "Free OpenCode model." },
    { id: "opencode/nemotron-3.5-lightning-free", name: "Nemotron 3.5 Lightning Free", description: "Free OpenCode model." },
    { id: "opencode/nemotron-3.5-super-vl-free", name: "Nemotron 3.5 Super VL Free", description: "Free OpenCode model." },
    { id: "opencode/mimo-v2.6-flash-free", name: "MiMo-V2.6-Flash Free", description: "Free OpenCode model." },
    { id: "opencode/inkling-2-small-free", name: "Inkling 2 Small Free", description: "Free OpenCode model." },
    { id: "opencode/beam-free", name: "Beam Free", description: "Free OpenCode model." },
    { id: "opencode/crouton-alpha-free", name: "Crouton Alpha Free", description: "Free OpenCode model." },
    { id: "opencode/frantic-ferret-free", name: "Frantic Ferret Free", description: "Free OpenCode model." },
    { id: "opencode/ling-3.0-flash-fin-free", name: "Ling 3.0 Flash Fin Free", description: "Free OpenCode model." },
    { id: "opencode/longcat-2.5-preview-free", name: "LongCat 2.5 Preview Free", description: "Free OpenCode model." },
    { id: "opencode/muse-spark-1.3-contributor-free", name: "Muse Spark 1.3 Free", description: "Free OpenCode model." },
    { id: "opencode/nemotron-3-ultra-free", name: "Nemotron 3 Ultra Free", description: "Free OpenCode model." },
    { id: "opencode/space-bunny-free", name: "Space Bunny Free", description: "Free OpenCode model." },
  ],
  gemini: [
    { id: "gemini-3.8-flash", name: "Gemini 3.8 Flash", description: "A fast model for everyday work." },
    { id: "gemini-4-argon", name: "Gemini 4 Argon", description: "A model for hard problems." },
  ],
  grok: [
    { id: "default", name: "Grok default", description: "Let the Grok CLI choose its own model." },
    { id: "grok-4.6", name: "Grok 4.6", description: "The current Grok model." },
  ],
  cursor: [
    { id: "default", name: "Cursor default", description: "Let Cursor choose its own model." },
  ],
};

/**
 * Claude names its models by role, so a model id has to be turned into the name a person would say.
 * The eight digits in an id are the build date of a release, not part of the model's name.
 */
export function claudeModelName(id: string): string | null {
  const parsed = /^([a-z0-9-]+?)(?:\[([a-z0-9]+)\])?$/u.exec(id.trim().toLowerCase());
  if (!parsed) return null;
  const [, base = "", variant] = parsed;
  const parts = base.split("-");
  if (parts.shift() !== "claude") return null;
  const family = parts.shift();
  if (!family || !/^[a-z]+$/u.test(family)) return null;
  const version = parts.filter((part) => !/^\d{8}$/u.test(part));
  if (!version.length || version.some((part) => !/^\d+$/u.test(part))) return null;
  const name = `Claude ${family[0]?.toUpperCase()}${family.slice(1)} ${version.join(".")}`;
  return variant ? `${name} (${variant.toUpperCase()} context)` : name;
}

/** Where a model list came from, so the app can say so rather than implying the CLI answered. */
export type ModelSource = "cli" | "fallback";

export interface ModelCatalog {
  models: ProviderModel[];
  source: ModelSource;
}

/**
 * A vendor CLI is asked for its models over the protocol that CLI speaks, not over a flag that
 * every CLI would have to grow. Codex answers a `model/list` request; the Agent Client Protocol
 * agents answer a `session/new` request with the models they can run.
 *
 * A provider lists more than one spelling when a command has changed shape. The first spelling
 * that answers wins.
 */
export const DISCOVERY_ARGS: Record<string, string[][]> = {
  codex: [["app-server", "--listen", "stdio://"], ["app-server"]],
  opencode: [["acp"]],
  cursor: [["acp"]],
  grok: [["--no-auto-update", "agent", "stdio"]],
  gemini: [["--acp"]],
};

/**
 * A CLI that lists its models in plain text.
 *
 * The Gemini CLI answers `--list-models` with the models its own account can run, which is how its
 * list stays current without anyone updating a table here.
 */
const LIST_COMMANDS: Record<string, { args: string[]; parse: (text: string) => ProviderModel[] }> = {
  gemini: { args: ["--list-models"], parse: parseGeminiModels },
};

/**
 * The models of a Gemini CLI listing.
 *
 * The listing puts each model at the left margin and its detail under it:
 *
 *   gemini-3.8-flash (gemini-3.8-flash)
 *     Context limit: 1,048,576 input tokens / 8,192 output tokens
 */
export function parseGeminiModels(text: string): ProviderModel[] {
  const models: ProviderModel[] = [];
  const seen = new Set<string>();
  for (const line of text.split("\n")) {
    if (!line || /^\s/.test(line)) continue;
    const match = /^(\S+)(?:\s+\(([^)]*)\))?\s*$/.exec(line.trim());
    if (!match) continue;
    const id = match[1]!;
    if (seen.has(id)) continue;
    seen.add(id);
    models.push({ id, name: match[2]?.trim() || id, description: "" });
  }
  return models;
}

interface Rpc {
  request(method: string, params: unknown): Promise<any>;
  close(): void;
  exited(): boolean;
}

/**
 * Opens a CLI that speaks JSON-RPC over newline-delimited JSON on stdio.
 *
 * Codex's app server uses the method and id fields without a `jsonrpc` field; the Agent Client
 * Protocol uses full JSON-RPC. Both are the same line protocol, so only that one difference is
 * carried here.
 */
function openRpc(command: string, args: string[], options: { cwd: string; env: NodeJS.ProcessEnv; jsonrpc: boolean }): Rpc {
  const child = spawn(command, args, { cwd: options.cwd, env: options.env, stdio: ["pipe", "pipe", "pipe"] });
  const pending = new Map<number, { resolve: (value: any) => void; reject: (error: Error) => void }>();
  let buffer = "";
  let nextId = 1;
  child.stdout.on("data", (chunk: Buffer) => {
    buffer += chunk.toString();
    let newline: number;
    while ((newline = buffer.indexOf("\n")) >= 0) {
      const line = buffer.slice(0, newline).trim();
      buffer = buffer.slice(newline + 1);
      if (!line) continue;
      let message: any;
      try {
        message = JSON.parse(line);
      } catch {
        continue;
      }
      const waiter = pending.get(message?.id);
      if (!waiter) continue;
      pending.delete(message.id);
      if (message.error) waiter.reject(new Error(String(message.error?.message ?? "The vendor CLI refused the request.")));
      else waiter.resolve(message.result);
    }
  });
  child.on("error", (error) => {
    for (const waiter of pending.values()) waiter.reject(error);
    pending.clear();
  });
  return {
    request(method: string, params: unknown) {
      const id = nextId++;
      const payload = options.jsonrpc ? { jsonrpc: "2.0", id, method, params } : { id, method, params };
      return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        try {
          child.stdin.write(JSON.stringify(payload) + "\n");
        } catch (error) {
          pending.delete(id);
          reject(error as Error);
        }
      });
    },
    close() {
      try {
        child.stdin.end();
      } catch {}
      try {
        child.kill();
      } catch {}
    },
    exited() {
      return child.exitCode !== null;
    },
  };
}

function withTimeout<T>(work: Promise<T>, timeoutMs: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("The vendor CLI did not answer in time.")), timeoutMs);
    work.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      }
    );
  });
}

/** The models an Agent Client Protocol agent offered in its session. */
function modelsFromAcpSession(session: any): ProviderModel[] {
  const offered = session?.models?.availableModels;
  if (!Array.isArray(offered)) return [];
  const seen = new Set<string>();
  const models: ProviderModel[] = [];
  for (const entry of offered) {
    const id = String(entry?.modelId ?? "").trim();
    if (!id || seen.has(id)) continue;
    seen.add(id);
    models.push({
      id,
      name: String(entry?.name ?? id).trim() || id,
      description: String(entry?.description ?? "").trim(),
    });
  }
  return models;
}

/** Codex's app server answers a paged `model/list`. */
function modelsFromCodexList(result: any): ProviderModel[] {
  const data = result?.data;
  if (!Array.isArray(data)) return [];
  const seen = new Set<string>();
  const models: ProviderModel[] = [];
  for (const entry of data) {
    const id = String(entry?.model ?? "").trim();
    if (!id || seen.has(id)) continue;
    seen.add(id);
    models.push({
      id,
      name: String(entry?.displayName ?? id).trim() || id,
      description: String(entry?.description ?? "").trim(),
    });
  }
  return models;
}

export interface DiscoveryOptions {
  command: string;
  cwd: string;
  env: NodeJS.ProcessEnv;
  timeoutMs?: number;
  /** The arguments to run instead of the provider's own, for a CLI that is already known. */
  args?: string[];
}

/**
 * Asks a provider's CLI which models it can run.
 *
 * Returns null when the provider has no way to be asked, or when the request failed. A caller
 * falls back to the shipped list in that case rather than showing an empty picker.
 */
export async function discoverModels(provider: string, options: DiscoveryOptions): Promise<ProviderModel[] | null> {
  const timeoutMs = options.timeoutMs ?? 10000;
  const spelling = options.args ? [options.args] : DISCOVERY_ARGS[provider];
  const listing = LIST_COMMANDS[provider];
  // A CLI that lists its models in plain text answers faster than a protocol handshake, but it
  // still has to start, and a vendor CLI starts slowly on a phone.
  if (listing) {
    const models = await modelsFromListing(options.command, options.args ?? listing.args, listing.parse, Math.max(timeoutMs, 40000), options);
    if (models.length) return models;
  }
  if (!spelling) return null;
  const jsonrpc = provider !== "codex";
  for (const args of spelling) {
    const rpc = openRpc(options.command, args, { cwd: options.cwd, env: options.env, jsonrpc });
    try {
      if (provider === "codex") {
        const models = modelsFromCodexList(await withTimeout(rpc.request("model/list", { limit: 100, includeHidden: true }), timeoutMs));
        if (models.length) return models;
        continue;
      }
      await withTimeout(
        rpc.request("initialize", {
          protocolVersion: 1,
          clientCapabilities: { fs: { readTextFile: false, writeTextFile: false } },
          clientInfo: { name: "PockoBots", title: "PockoBots", version: "1" },
        }),
        timeoutMs
      );
      const session = await withTimeout(rpc.request("session/new", { cwd: options.cwd, mcpServers: [] }), timeoutMs);
      const models = modelsFromAcpSession(session);
      if (models.length) return models;
    } catch {
      // This spelling of the command did not answer. Try the next one.
    } finally {
      rpc.close();
    }
  }
  return null;
}

/** The models a CLI listed in plain text. */
async function modelsFromListing(
  command: string,
  args: string[],
  parse: (text: string) => ProviderModel[],
  timeoutMs: number,
  options: DiscoveryOptions
): Promise<ProviderModel[]> {
  try {
    const text = await runToText(command, args, timeoutMs, options);
    return parse(text);
  } catch {
    return [];
  }
}

/** Runs a command and keeps what it printed. */
function runToText(command: string, args: string[], timeoutMs: number, options: DiscoveryOptions): Promise<string> {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd: options.cwd, env: options.env, stdio: ["ignore", "pipe", "pipe"] });
    let text = "";
    const append = (chunk: Buffer) => (text = (text + chunk.toString()).slice(-20000));
    child.stdout.on("data", append);
    child.stderr.on("data", append);
    const timer = setTimeout(() => {
      child.kill();
      reject(new Error("The vendor CLI did not answer in time."));
    }, timeoutMs);
    child.once("error", (error) => {
      clearTimeout(timer);
      reject(error);
    });
    child.once("close", (code) => {
      clearTimeout(timer);
      if (code === 0) resolve(text);
      else reject(new Error("The vendor CLI did not answer."));
    });
  });
}

/**
 * The model list to show for a provider.
 *
 * A CLI that answered owns the list, with Gemini as the exception: its CLI answers with an older
 * set of names than the app ships, so the shipped list is the one shown for it. The app never
 * claims a provider said something it did not, so the source says which list is on the screen.
 */
export function catalogFor(provider: string, discovered: ProviderModel[] | null): ModelCatalog {
  if (provider === "gemini") return { models: FALLBACK_MODELS.gemini, source: "fallback" };
  if (discovered && discovered.length) return { models: discovered, source: "cli" };
  return { models: FALLBACK_MODELS[provider] ?? [], source: "fallback" };
}
