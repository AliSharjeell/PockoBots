import {randomUUID, createHash} from "node:crypto";
import {lookup} from "node:dns";
import {BlockList, isIP} from "node:net";
import http from "node:http";
import https from "node:https";
import type {Express} from "express";
import type {Store} from "./storage.js";

// ---------- Public-page fetch guard ----------

const blocked = new BlockList();
for (const [net, bits] of [["0.0.0.0", 8], ["10.0.0.0", 8], ["100.64.0.0", 10], ["127.0.0.0", 8], ["169.254.0.0", 16], ["172.16.0.0", 12], ["192.0.0.0", 24], ["192.168.0.0", 16], ["198.18.0.0", 15], ["224.0.0.0", 3]] as const) blocked.addSubnet(net, bits, "ipv4");
for (const [net, bits] of [["::", 127], ["fc00::", 7], ["fe80::", 10], ["ff00::", 8], ["64:ff9b::", 96], ["2001:db8::", 32]] as const) blocked.addSubnet(net, bits, "ipv6");
/** True for private, loopback, link-local, multicast and other non-public addresses. */
export function isBlockedAddress(address: string): boolean {
  const ip = address.replace(/^\[|\]$/g, "").split("%")[0];
  const mapped = ip.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/i);
  if (mapped) return isBlockedAddress(mapped[1]);
  const family = isIP(ip);
  if (!family) return true;
  return blocked.check(ip, family === 4 ? "ipv4" : "ipv6");
}

export type FetchOptions = {maxBytes?: number; timeoutMs?: number; maxRedirects?: number; isBlocked?: (ip: string) => boolean};
/**
 * GET a public http(s) page as text. Every hop's address is checked at connect time
 * (custom DNS lookup, so a rebinding answer cannot slip past), redirects are followed
 * manually and re-checked, the body is capped and the whole fetch times out.
 */
export async function safeFetchText(href: string, options: FetchOptions = {}): Promise<{url: string; status: number; text: string}> {
  const {maxBytes = 1_000_000, timeoutMs = 15000, maxRedirects = 5, isBlocked = isBlockedAddress} = options;
  const deadline = AbortSignal.timeout(timeoutMs);
  let url = checkUrl(href);
  for (let hop = 0; ; hop++) {
    const host = url.hostname.replace(/^\[|\]$/g, "");
    if (isIP(host) && isBlocked(host)) throw Error("This address is private or local and cannot be checked");
    const response = await new Promise<{status: number; location?: string; text: string}>((resolve, reject) => {
      const client = url.protocol === "https:" ? https : http;
      const request = client.get(url, {
        signal: deadline, headers: {"user-agent": "PockoBots page check", accept: "text/html,text/plain,application/json;q=0.9,*/*;q=0.5", "accept-encoding": "identity"},
        lookup: (hostname: string, opts: any, done: any) => lookup(hostname, {...opts, all: true}, (error, addresses: any) => {
          if (error) return done(error);
          if (!addresses.length || addresses.some((a: any) => isBlocked(a.address))) return done(Object.assign(Error("This address is private or local and cannot be checked"), {code: "EBLOCKED"}));
          opts?.all ? done(null, addresses) : done(null, addresses[0].address, addresses[0].family);
        }),
      }, response => {
        const status = response.statusCode || 0;
        if (status >= 300 && status < 400 && response.headers.location) { response.resume(); resolve({status, location: response.headers.location, text: ""}); return; }
        const chunks: Buffer[] = []; let size = 0;
        response.on("data", (chunk: Buffer) => { size += chunk.length; if (size > maxBytes) { reject(Error("The page is larger than " + maxBytes + " bytes")); request.destroy(); return; } chunks.push(chunk); });
        response.on("end", () => resolve({status, text: Buffer.concat(chunks).toString("utf8")}));
        response.on("error", reject);
      });
      request.on("error", reject);
    }).catch(error => { throw deadline.aborted ? Error("The page did not answer in time") : error; });
    if (response.location === undefined) return {url: url.href, status: response.status, text: response.text};
    if (hop >= maxRedirects) throw Error("Too many redirects");
    url = checkUrl(new URL(response.location, url).href);
  }
}
function checkUrl(href: string) {
  let url: URL;
  try { url = new URL(href); } catch { throw Error("Enter a valid http or https URL"); }
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password) throw Error("Use an http or https URL without credentials");
  return url;
}

/** Page text with scripts, styles and tags removed. */
export function pageText(html: string) {
  return html.replace(/<(script|style|noscript)\b[\s\S]*?<\/\1\s*>/gi, " ").replace(/<[^>]*>/g, " ")
    .replace(/&nbsp;/gi, " ").replace(/&amp;/gi, "&").replace(/&lt;/gi, "<").replace(/&gt;/gi, ">").replace(/&quot;/gi, '"').replace(/&#39;/g, "'")
    .replace(/\s+/g, " ").trim();
}
/** First US-dollar price in the text, after `anchor` when given. ponytail: first match wins; a selector would be needed for busy pages. */
export function findUsdPrice(text: string, anchor = "") {
  const start = anchor ? text.toLowerCase().indexOf(anchor.toLowerCase()) : 0;
  if (start < 0) return undefined;
  const match = text.slice(start).match(/(?:US\$|USD\s?|\$)\s?(\d{1,3}(?:,\d{3})+|\d+)(\.\d{1,2})?/i);
  return match ? Number(match[1].replace(/,/g, "") + (match[2] || "")) : undefined;
}

// ---------- Goals and page watches ----------

export type Goal = {id: string; title: string; status: "active" | "paused" | "done"; milestones: {id: string; title: string; done: boolean}[]; created: number; updated: number};
export type Watch = {
  id: string; botId: string; url: string; kind: "changed" | "text" | "price";
  text?: string; below?: number; above?: number; intervalMinutes: number;
  status: "active" | "paused"; created: number; nextCheck: number; lastCheck?: number;
  lastHash?: string; lastMet?: boolean; lastPrice?: number; failures: number; lastError?: string; failureAlerted?: boolean;
};
type Notify = (botId: string, text: string) => Promise<unknown>;
type Fetcher = (url: string) => Promise<{status: number; text: string}>;

function title(value: unknown, label: string) {
  if (typeof value !== "string" || !value.trim() || value.length > 200) throw Error(`${label} must be 1–200 characters`);
  return value.trim();
}
function milestones(value: unknown) {
  if (value === undefined) return [];
  if (!Array.isArray(value) || value.length > 50) throw Error("Use at most 50 milestones");
  return value.map(m => ({id: randomUUID(), title: title(typeof m === "string" ? m : m?.title, "A milestone"), done: typeof m === "object" && m?.done === true}));
}

/** Goals with milestones, and public-page watches driven by the bots' schedule tick. */
export class Tracking {
  private goals: Goal[] = []; private watches: Watch[] = []; private running = false;
  constructor(private store: Store, private botExists: (id: string) => boolean, private notify: Notify, private fetchText: Fetcher = safeFetchText) {}
  async init() { const data = await this.store.read<any>("tracking", {}); this.goals = data.goals || []; this.watches = data.watches || []; }
  private persist() { return this.store.write("tracking", {goals: this.goals, watches: this.watches}); }

  listGoals() { return structuredClone(this.goals); }
  private goal(id: string) { const goal = this.goals.find(g => g.id === id); if (!goal) throw Error("Goal not found"); return goal; }
  async createGoal(input: any) {
    if (this.goals.length >= 100) throw Error("Goal limit reached (100)");
    const now = Date.now(), goal: Goal = {id: randomUUID(), title: title(input?.title, "A goal title"), status: "active", milestones: milestones(input?.milestones), created: now, updated: now};
    this.goals.push(goal); await this.persist(); return structuredClone(goal);
  }
  async updateGoal(id: string, input: any) {
    const goal = this.goal(id);
    const next = {title: input?.title === undefined ? goal.title : title(input.title, "A goal title"), status: input?.status ?? goal.status, milestones: input?.milestones === undefined ? goal.milestones : milestones(input.milestones)};
    if (!["active", "paused", "done"].includes(next.status)) throw Error("Choose active, paused or done");
    Object.assign(goal, next, {updated: Date.now()}); await this.persist(); return structuredClone(goal);
  }
  async setMilestone(id: string, milestoneId: string, done: unknown) {
    const goal = this.goal(id), milestone = goal.milestones.find(m => m.id === milestoneId);
    if (!milestone) throw Error("Milestone not found in this goal");
    if (typeof done !== "boolean") throw Error("Set done to true or false");
    milestone.done = done; goal.updated = Date.now(); await this.persist(); return structuredClone(goal);
  }
  async removeGoal(id: string) { this.goal(id); this.goals = this.goals.filter(g => g.id !== id); await this.persist(); }

  listWatches() { return structuredClone(this.watches); }
  private watch(id: string) { const watch = this.watches.find(w => w.id === id); if (!watch) throw Error("Page check not found"); return watch; }
  async createWatch(input: any, now = Date.now()) {
    if (this.watches.length >= 50) throw Error("Page check limit reached (50)");
    if (typeof input?.botId !== "string" || !this.botExists(input.botId)) throw Error("Choose a bot to send the alerts");
    const parsed = checkUrl(typeof input.url === "string" && input.url.length <= 2000 ? input.url : ""), url = parsed.href;
    const host = parsed.hostname.replace(/^\[|\]$/g, "");
    if (host === "localhost" || host.endsWith(".localhost") || (isIP(host) && isBlockedAddress(host))) throw Error("This address is private or local and cannot be checked");
    const kind = input.kind ?? "changed";
    if (!["changed", "text", "price"].includes(kind)) throw Error("Choose changed, text or price");
    const intervalMinutes = input.intervalMinutes ?? 60;
    if (!Number.isInteger(intervalMinutes) || intervalMinutes < 15 || intervalMinutes > 10080) throw Error("Check every 15–10080 minutes");
    const text = input.text === undefined ? undefined : title(input.text, "The text");
    if (kind === "text" && !text) throw Error("Enter the text to look for");
    let below: number | undefined, above: number | undefined;
    if (kind === "price") {
      for (const key of ["below", "above"] as const) if (input[key] !== undefined && (typeof input[key] !== "number" || !Number.isFinite(input[key]) || input[key] < 0 || input[key] > 1e9)) throw Error("Use a USD amount for the price threshold");
      ({below, above} = input);
      if ((below === undefined) === (above === undefined)) throw Error("Choose exactly one: below or above (USD)");
    }
    const watch: Watch = {id: randomUUID(), botId: input.botId, url, kind, text, below, above, intervalMinutes, status: "active", created: now, nextCheck: now, failures: 0};
    this.watches.push(watch); await this.persist(); return structuredClone(watch);
  }
  async updateWatch(id: string, input: any) {
    const watch = this.watch(id);
    if (!["active", "paused"].includes(input?.status)) throw Error("Choose active or paused");
    watch.status = input.status;
    if (watch.status === "active") { watch.nextCheck = Date.now(); watch.failures = 0; watch.failureAlerted = false; }
    await this.persist(); return structuredClone(watch);
  }
  async removeWatch(id: string) { this.watch(id); this.watches = this.watches.filter(w => w.id !== id); await this.persist(); }

  /** Check due pages. Called from the bots tick; overlapping calls are skipped. */
  async run(now = Date.now()) {
    if (this.running) return; this.running = true;
    try {
      for (const watch of this.watches.filter(w => w.status === "active" && w.nextCheck <= now)) {
        if (!this.botExists(watch.botId)) { this.watches = this.watches.filter(w => w !== watch); await this.persist(); continue; }
        await this.check(watch, now);
      }
    } finally { this.running = false; }
  }
  private async check(watch: Watch, now: number) {
    let alert = "";
    try {
      const page = await this.fetchText(watch.url);
      if (page.status < 200 || page.status >= 300) throw Error("The page answered HTTP " + page.status);
      // Page text stays here: alerts are fixed wording, so fetched text never reaches a prompt.
      const text = pageText(page.text);
      if (watch.kind === "changed") {
        const hash = createHash("sha256").update(text).digest("hex");
        if (watch.lastHash && watch.lastHash !== hash) alert = `A page you track changed: ${watch.url}`;
        watch.lastHash = hash;
      } else {
        let met: boolean;
        if (watch.kind === "text") met = text.toLowerCase().includes(watch.text!.toLowerCase());
        else {
          const price = findUsdPrice(text, watch.text);
          watch.lastPrice = price;
          met = price !== undefined && (watch.below !== undefined ? price < watch.below : price > watch.above!);
          if (met) alert = `The price on ${watch.url} is $${price!.toFixed(2)}, ${watch.below !== undefined ? "below $" + watch.below : "above $" + watch.above}.`;
        }
        if (watch.kind === "text" && met) alert = `"${watch.text}" now appears on ${watch.url}`;
        // Alert only when the condition becomes true, not on every check while it stays true.
        if (!met || watch.lastMet) alert = "";
        watch.lastMet = met;
      }
      watch.failures = 0; watch.failureAlerted = false; watch.lastError = undefined;
      watch.nextCheck = now + watch.intervalMinutes * 60000;
    } catch (error: any) {
      watch.failures++; watch.lastError = String(error?.message || error).slice(0, 300);
      // Back off: double the wait per failure, at most a day.
      watch.nextCheck = now + Math.min(watch.intervalMinutes * 60000 * 2 ** Math.min(watch.failures, 10), 86400000);
      if (watch.failures >= 3 && !watch.failureAlerted) { watch.failureAlerted = true; alert = `A page check keeps failing (${watch.failures} times): ${watch.url}`; }
    }
    watch.lastCheck = now;
    await this.persist();
    if (alert) await this.notify(watch.botId, alert).catch(error => { watch.lastError = "Alert not delivered: " + String(error?.message || error).slice(0, 200); });
  }
}

/** Register after the bearer-auth middleware. */
export function registerTrackingRoutes(app: Express, tracking: Tracking) {
  app.get("/api/goals", (_req, res) => res.json(tracking.listGoals()));
  app.post("/api/goals", async (req, res) => res.status(201).json(await tracking.createGoal(req.body)));
  app.put("/api/goals/:id", async (req, res) => res.json(await tracking.updateGoal(String(req.params.id), req.body)));
  app.put("/api/goals/:id/milestones/:milestone", async (req, res) => res.json(await tracking.setMilestone(String(req.params.id), String(req.params.milestone), req.body?.done)));
  app.delete("/api/goals/:id", async (req, res) => { await tracking.removeGoal(String(req.params.id)); res.json({ok: true}); });
  app.get("/api/watches", (_req, res) => res.json(tracking.listWatches()));
  app.post("/api/watches", async (req, res) => res.status(201).json(await tracking.createWatch(req.body)));
  app.put("/api/watches/:id", async (req, res) => res.json(await tracking.updateWatch(String(req.params.id), req.body)));
  app.delete("/api/watches/:id", async (req, res) => { await tracking.removeWatch(String(req.params.id)); res.json({ok: true}); });
}
