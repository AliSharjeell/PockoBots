import { spawn } from "node:child_process";

/**
 * The account limits a provider reports.
 *
 * A vendor CLI knows its own plan, so it is asked about it over the protocol that CLI speaks. Codex
 * answers over its app server and the Agent Client Protocol agents answer over the same request. A
 * provider with no reader here reports nothing, and the app says that rather than showing a
 * reading of zero.
 */

/** One limit window, such as a five-hour window or a weekly one. */
export interface UsageWindow {
  /** What a person would call the window. */
  label: string;
  /** How much of the window is spent, from 0 to 100. */
  usedPercent: number;
  /** How long the window runs, in minutes. Null when the provider did not say. */
  windowDurationMins: number | null;
  /** When the window fills up again, in unix seconds. Null when the provider did not say. */
  resetsAt: number | null;
}

export interface ProviderUsage {
  providerId: string;
  providerName: string;
  /** True when the provider told us about a limit. */
  reported: boolean;
  windows: UsageWindow[];
  /** Why there is no reading, when there is none. */
  note: string;
}

/**
 * The commands that answer a request about the account's limits.
 *
 * The first spelling that answers wins, the same rule the model lists follow.
 */
export const USAGE_ARGS: Record<string, string[][]> = {
  codex: [["app-server", "--listen", "stdio://"], ["app-server"]],
  opencode: [["acp"]],
  cursor: [["acp"]],
};

export interface UsageOptions {
  command: string;
  cwd: string;
  env: NodeJS.ProcessEnv;
  timeoutMs?: number;
  /** The arguments to run instead of the provider's own, for a CLI that is already known. */
  args?: string[];
}

/** What a person would call a window of a known length. */
export function usageWindowLabel(minutes: number | null): string {
  if (minutes === null || !Number.isFinite(minutes) || minutes <= 0) return "Plan limit";
  // The windows the plans are sold in are named the way a person would say them.
  if (minutes === 10080) return "Weekly limit";
  if (minutes === 43200) return "Monthly limit";
  if (minutes === 1440) return "Daily limit";
  if (minutes >= 10080 && minutes % 10080 === 0) {
    const weeks = minutes / 10080;
    return `${weeks}-week limit`;
  }
  if (minutes >= 1440 && minutes % 1440 === 0) {
    const days = minutes / 1440;
    return `${days}-day limit`;
  }
  if (minutes % 60 === 0) {
    const hours = minutes / 60;
    return hours === 1 ? "Hourly limit" : `${hours}-hour limit`;
  }
  return minutes === 1 ? "Minute limit" : `${minutes}-minute limit`;
}

function numberOf(record: any, keys: string[]): number | null {
  for (const key of keys) {
    const value = record?.[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim() !== "") {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return null;
}

/**
 * When a window fills up again.
 *
 * The providers disagree on the field: some give the moment it resets, others give the seconds
 * left. Both are read, and a moment in milliseconds is scaled down.
 */
function resetsAtOf(record: any): number | null {
  const absolute = numberOf(record, ["resetsAt", "resets_at", "resetAt"]);
  if (absolute !== null) return absolute > 1e12 ? Math.round(absolute / 1000) : Math.round(absolute);
  const relative = numberOf(record, ["resetsInSeconds", "resets_in_seconds", "resetInSeconds"]);
  if (relative !== null) return Math.floor(Date.now() / 1000) + Math.round(relative);
  return null;
}

function parseWindow(value: any): UsageWindow | null {
  if (!value || typeof value !== "object") return null;
  const used = numberOf(value, ["usedPercent", "used_percent", "percent", "utilization"]);
  if (used === null) return null;
  const minutes = numberOf(value, [
    "windowDurationMins",
    "windowMinutes",
    "window_minutes",
    "window_duration_mins",
    "durationMins",
  ]);
  return {
    label: usageWindowLabel(minutes),
    usedPercent: Math.max(0, Math.min(100, used)),
    windowDurationMins: minutes,
    resetsAt: resetsAtOf(value),
  };
}

/**
 * The windows of a reading.
 *
 * A provider reports one limit, or one limit per model, and each of those holds a short window and
 * a long one. Every window that carries an amount is kept, the short one first.
 */
export function parseUsageWindows(result: any): UsageWindow[] {
  const limits: any[] = [];
  if (result && typeof result === "object") {
    if (result.rateLimits) limits.push(result.rateLimits);
    if (result.primary || result.secondary) limits.push(result);
    const byId = result.rateLimitsByLimitId;
    if (byId && typeof byId === "object") for (const value of Object.values(byId)) if (value) limits.push(value);
  }
  const windows: UsageWindow[] = [];
  for (const limit of limits) {
    for (const key of ["primary", "secondary"]) {
      const window = parseWindow(limit?.[key]);
      if (window) windows.push(window);
    }
  }
  windows.sort((left, right) => (left.windowDurationMins ?? 0) - (right.windowDurationMins ?? 0));
  return windows;
}

/** The reading of one provider, or null when the provider cannot be asked. */
export async function readUsage(
  providerId: string,
  providerName: string,
  options: UsageOptions
): Promise<ProviderUsage> {
  const spellings = options.args ? [options.args] : USAGE_ARGS[providerId];
  if (!spellings) {
    return {
      providerId,
      providerName,
      reported: false,
      windows: [],
      note: "This provider does not report usage limits.",
    };
  }
  const timeoutMs = options.timeoutMs ?? 12000;
  let lastError = "The provider's CLI did not answer with its usage.";
  for (const args of spellings) {
    const rpc = openAccountRpc(options.command, args, options);
    try {
      const result = await withTimeout(requestUsage(rpc, providerId, options.cwd, timeoutMs), timeoutMs);
      const windows = parseUsageWindows(result);
      if (windows.length) {
        return { providerId, providerName, reported: true, windows, note: "" };
      }
      lastError = "This provider reported no usage limits for this account.";
    } catch (error) {
      lastError = messageOf(error, lastError);
    } finally {
      rpc.close();
    }
  }
  return { providerId, providerName, reported: false, windows: [], note: lastError };
}

function messageOf(error: unknown, fallback: string): string {
  const message = error instanceof Error ? error.message : String(error ?? "");
  if (!message) return fallback;
  if (/ENOENT|not found|unavailable/i.test(message)) return "Install the provider's CLI to see its usage.";
  if (/timed out|timeout/i.test(message)) return "The provider's CLI took too long to answer with its usage.";
  return fallback;
}

interface Rpc {
  request(method: string, params: unknown): Promise<any>;
  close(): void;
}

/**
 * Opens a CLI that answers over newline-delimited JSON.
 *
 * Codex's app server leaves the `jsonrpc` field out and the Agent Client Protocol agents include
 * it. Only that one difference is carried here.
 */
function openAccountRpc(command: string, args: string[], options: UsageOptions): Rpc {
  const jsonrpc = args[0] !== "app-server";
  const child = spawn(command, args, {
    cwd: options.cwd,
    env: options.env,
    stdio: ["pipe", "pipe", "pipe"],
  });
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
  child.on("error", (error: Error) => {
    for (const waiter of pending.values()) waiter.reject(error);
    pending.clear();
  });
  return {
    request(method: string, params: unknown) {
      const id = nextId++;
      const payload = jsonrpc ? { jsonrpc: "2.0", id, method, params } : { id, method, params };
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
  };
}

async function requestUsage(rpc: Rpc, providerId: string, cwd: string, timeoutMs: number): Promise<any> {
  if (providerId !== "codex") {
    await rpc.request("initialize", {
      protocolVersion: 1,
      clientCapabilities: { fs: { readTextFile: false, writeTextFile: false } },
      clientInfo: { name: "PockoBots", title: "PockoBots", version: "1" },
    });
  }
  return rpc.request("account/rateLimits/read", {});
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
