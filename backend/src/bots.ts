import { randomUUID } from "node:crypto";
import { Type } from "typebox";
import type { Store } from "./storage.js";
import type { Projects } from "./projects.js";
import type { Sessions } from "./sessions.js";
import type { MobileTasks } from "./mobile-tasks.js";
import type { Connectors } from "./connectors.js";

export type Bot = { id: string; name: string; role: string; kind: "coding" | "phone" | "browser"; projectId: string; sessionId: string;
  enabled: boolean; allowShell: boolean; connectors: string[]; intervalMinutes: number; heartbeat: string; nextWake: number;
  dailyLimit: number; day: string; runs: number; created: number };
export type BotJob = { id: string; botId: string; text: string; source: "user" | "routine" | "bot"; depth: number; status: "queued" | "arming" | "running" | "waiting" | "paused" | "completed" | "failed" | "cancelled";
  created: number; updated: number; mobileTaskId?: string; error?: string; output?: string };
type Message = { id: string; botId: string; role: "user" | "assistant" | "system"; text: string; time: number; jobId?: string };

/** Durable bot inboxes and queues. One run per bot; at most two bot runs total. */
export class Bots {
  enabled = false;
  private records: Bot[] = []; private jobs: BotJob[] = []; private messages: Message[] = [];
  private timer?: ReturnType<typeof setInterval>; private ticking = false; private closing = false;
  private armed = new Set<string>();
  constructor(private store: Store, private projects: Projects, private sessions: Sessions, private mobile: MobileTasks,
    private connectors: Connectors, private publish: (value: unknown) => void, private redact: (text: string) => string) {}
  async init() {
    const data = await this.store.read<any>("bots", { enabled: false, bots: [], jobs: [], messages: [] });
    this.enabled = data.enabled; this.records = data.bots; this.jobs = data.jobs; this.messages = data.messages;
    for (const job of this.jobs) if (["running", "arming", "waiting"].includes(job.status)) { job.status = "paused"; job.error = "Runtime restarted. Review and resume this task."; }
    await this.persist();
    this.timer = setInterval(() => { void this.tick().catch(e => this.publish({ type: "bot_error", error: this.redact(String(e)) })); }, 1000); this.timer.unref();
  }
  private persist() { return this.store.write("bots", { enabled: this.enabled, bots: this.records, jobs: this.jobs, messages: this.messages }); }
  private async changed() { await this.persist(); this.publish({ type: "bots", bots: this.list() }); }
  private message(botId: string, role: Message["role"], text: string, jobId?: string) {
    this.messages.push({ id: randomUUID(), botId, role, text: this.redact(text).slice(0, 24000), time: Date.now(), jobId });
    const own = this.messages.filter(m => m.botId === botId); if (own.length > 200) { const remove = new Set(own.slice(0, own.length - 200).map(m => m.id)); this.messages = this.messages.filter(m => !remove.has(m.id)); }
  }
  list() { return this.records.map(bot => { const jobs = this.jobs.filter(j => j.botId === bot.id); return { ...bot, lastMessage: this.messages.filter(m => m.botId === bot.id).at(-1), active: jobs.find(j => ["arming", "running", "waiting"].includes(j.status)), queued: jobs.filter(j => j.status === "queued").length }; }); }
  get(id: string) { const bot = this.records.find(b => b.id === id); if (!bot) throw Error("Bot not found"); return bot; }
  state(id: string) {
    const bot = this.get(id); const jobs = this.jobs.filter(j => j.botId === id); const active = jobs.find(j => ["running", "waiting"].includes(j.status));
    return { bot, jobs, messages: this.messages.filter(m => m.botId === id), session: bot.kind === "coding" ? this.sessions.state(bot.sessionId) : undefined,
      mobileTask: active?.mobileTaskId ? this.mobile.state(active.mobileTaskId) : undefined };
  }
  private input(input: any, previous?: Bot) {
    const b = { ...previous, ...input };
    if (typeof b.name !== "string" || !b.name.trim() || b.name.length > 60) throw Error("Enter a bot name (1–60 characters)");
    if (typeof b.role !== "string" || b.role.length > 8000) throw Error("Role instructions must be at most 8000 characters");
    if (!["coding", "phone", "browser"].includes(b.kind)) throw Error("Choose coding, phone, or browser");
    if (previous && b.kind !== previous.kind) throw Error("Create a new bot to change its task type");
    b.connectors ??= []; if (!Array.isArray(b.connectors) || b.connectors.length > 8 || b.connectors.some((id: unknown) => typeof id !== "string")) throw Error("Invalid bot connections"); this.connectors.require(b.connectors);
    b.intervalMinutes ??= 0; if (!Number.isInteger(b.intervalMinutes) || (b.intervalMinutes !== 0 && (b.intervalMinutes < 15 || b.intervalMinutes > 10080))) throw Error("Use 0 to disable routines, or an interval of 15–10080 minutes");
    b.heartbeat ??= ""; if (typeof b.heartbeat !== "string" || b.heartbeat.length > 8000 || (b.intervalMinutes && !b.heartbeat.trim())) throw Error("Enter a routine task");
    b.dailyLimit ??= 24; if (!Number.isInteger(b.dailyLimit) || b.dailyLimit < 1 || b.dailyLimit > 144) throw Error("Daily run limit must be 1–144");
    return b;
  }
  async create(input: any) {
    if (this.records.length >= 12) throw Error("Bot limit reached (12)"); const b = this.input(input); const id = randomUUID();
    const project = await this.projects.create("bot-" + id.slice(0, 8)); const session = await this.sessions.create(project.id);
    const bot: Bot = { id, name: b.name.trim(), role: b.role, kind: b.kind, projectId: project.id, sessionId: session.id, enabled: true,
      allowShell: b.allowShell === true, connectors: [...new Set<string>(b.connectors)], intervalMinutes: b.intervalMinutes, heartbeat: b.heartbeat,
      nextWake: Date.now() + b.intervalMinutes * 60000, dailyLimit: b.dailyLimit, day: "", runs: 0, created: Date.now() };
    this.records.push(bot); this.message(id, "system", `${bot.name} is ready. ${bot.kind === "coding" ? "I have my own coding workspace." : "Phone tasks use the floating island and take turns with other bots."}`); await this.changed(); return bot;
  }
  async update(id: string, input: any) {
    const bot = this.get(id); if (this.jobs.some(j => j.botId === id && ["running", "arming", "waiting"].includes(j.status))) throw Error("Pause this bot before changing its role or connections");
    const b = this.input(input, bot); Object.assign(bot, { name: b.name.trim(), role: b.role, connectors: b.connectors, allowShell: b.allowShell === true, intervalMinutes: b.intervalMinutes, heartbeat: b.heartbeat, dailyLimit: b.dailyLimit, nextWake: Date.now() + b.intervalMinutes * 60000 }); await this.changed(); return bot;
  }
  async remove(id: string) { await this.pause(id); this.records = this.records.filter(b => b.id !== id); this.jobs = this.jobs.filter(j => j.botId !== id); this.messages = this.messages.filter(m => m.botId !== id); await this.changed(); }
  async engine(enabled: boolean) { if (typeof enabled !== "boolean") throw Error("Invalid engine setting"); this.enabled = enabled; if (!enabled) for (const bot of this.records) await this.pause(bot.id, false); await this.changed(); }
  async enqueue(id: string, text: unknown, source: BotJob["source"] = "user", depth = 0) {
    this.get(id); if (typeof text !== "string" || !text.trim() || text.length > 16000) throw Error("Enter a task of at most 16000 characters");
    if (depth > 3) throw Error("Bot delegation chain limit reached");
    if (this.jobs.filter(j => j.botId === id && ["queued", "arming", "running", "waiting", "paused"].includes(j.status)).length >= 20) throw Error("Bot queue is full (20 tasks)");
    const job: BotJob = { id: randomUUID(), botId: id, text: text.trim(), source, depth, status: "queued", created: Date.now(), updated: Date.now() };
    this.jobs.push(job); this.message(id, source === "user" ? "user" : "system", text, job.id);
    // Retain bounded history while never discarding unfinished work.
    const finished = this.jobs.filter(j => j.botId === id && ["completed", "failed", "cancelled"].includes(j.status));
    const trim = new Set(finished.slice(0, Math.max(0, finished.length - 100)).map(j => j.id)); this.jobs = this.jobs.filter(j => !trim.has(j.id));
    await this.changed(); return job;
  }
  async pause(id: string, disable = true) {
    const bot = this.get(id); if (disable) bot.enabled = false;
    for (const job of this.jobs.filter(j => j.botId === id && ["arming", "running", "waiting"].includes(j.status))) {
      job.status = "paused"; job.error = "Paused by you"; this.sessions.stop(bot.sessionId); if (job.mobileTaskId) await this.mobile.interrupt(job.mobileTaskId);
    } await this.changed();
  }
  async resume(id: string) { const bot = this.get(id); bot.enabled = true; for (const j of this.jobs.filter(j => j.botId === id && j.status === "paused")) { j.status = "queued"; j.error = undefined; } await this.changed(); }
  async cancel(id: string, jobId: string) { const bot = this.get(id); const job = this.jobs.find(j => j.id === jobId && j.botId === id); if (!job) throw Error("Task not found"); if (["completed", "failed", "cancelled"].includes(job.status)) return; job.status = "cancelled"; if (job.mobileTaskId) await this.mobile.interrupt(job.mobileTaskId, true); if (this.jobs.every(j => j.botId !== id || !["running", "waiting"].includes(j.status))) this.sessions.stop(bot.sessionId); await this.changed(); }
  phoneRequest() { if (!this.enabled) return null; const j = this.jobs.find(j => j.status === "arming" && this.get(j.botId).enabled); return j ? { taskId: j.mobileTaskId, botName: this.get(j.botId).name } : null; }
  armPhone(taskId: string) { const pending = this.phoneRequest(); if (!pending || pending.taskId !== taskId) throw Error("Phone task is no longer waiting for its controller"); this.armed.add(taskId); }
  private async profile(bot: Bot, job: BotJob) {
    const tools = await this.connectors.tools(bot.connectors);
    tools.push({ name: "message_bot", label: "Message a teammate", description: "Queue a specific task for another local bot. Do not repeat delegation loops.", parameters: Type.Object({ bot_id: Type.String(), task: Type.String({ maxLength: 8000 }) }), execute: async (_id, p: any) => {
      if (p.bot_id === bot.id) throw Error("Do not delegate to yourself"); const queued = await this.enqueue(p.bot_id, p.task, "bot", job.depth + 1); return { content: [{ type: "text", text: "Queued task " + queued.id }], details: {} }; } });
    return { instructions: `Your name is ${bot.name}. ${bot.role}\nOther local bots: ${JSON.stringify(this.records.filter(b => b.id !== bot.id).map(b => ({ id: b.id, name: b.name, kind: b.kind })))}.\nFor routine checks, return NO_UPDATE when nothing meaningful changed. Never claim work succeeded without tool evidence.`, tools };
  }
  private async finish(job: BotJob, status: "completed" | "failed" | "paused", output: string) {
    job.status = status; job.updated = Date.now(); job.output = this.redact(output).slice(0, 24000);
    if (output.trim() !== "NO_UPDATE") this.message(job.botId, "assistant", output || (status === "completed" ? "Task finished." : "Task needs attention."), job.id);
    await this.changed();
  }
  async tick(now = Date.now()) {
    if (this.ticking || this.closing || !this.enabled) return; this.ticking = true;
    try {
      for (const job of this.jobs.filter(j => ["arming", "running", "waiting"].includes(j.status))) {
        const bot = this.get(job.botId);
        if (job.mobileTaskId) {
          const task = this.mobile.state(job.mobileTaskId);
          if (["completed", "failed", "paused", "stopped"].includes(task.status) && !(job.status === "arming" && task.status === "paused")) { await this.finish(job, task.status === "completed" ? "completed" : task.status === "paused" ? "paused" : "failed", task.error || task.output); continue; }
          if (job.status === "arming") {
            try { const health = await this.mobile.health(); if (health.ready && health.task_allowed && this.armed.has(task.id)) { await this.mobile.start(task.id, undefined, await this.profile(bot, job)); this.armed.delete(task.id); job.status = "running"; await this.changed(); } }
            catch (e) { job.error = this.redact(String(e)); }
            if (job.status === "arming" && now - job.updated > 30000) await this.finish(job, "paused", job.error || "Enable Accessibility and floating windows, then resume this bot.");
          } else if (job.status !== task.status && ["running", "waiting"].includes(task.status)) { job.status = task.status as "running" | "waiting"; await this.changed(); }
        } else {
          const state = this.sessions.state(bot.sessionId);
          if (state.busy) { const next = state.approvals.length ? "waiting" : "running"; if (job.status !== next) { job.status = next; await this.changed(); } }
          else { const last: any = [...state.messages].reverse().find(m => m.role === "assistant"); const output = last?.errorMessage || (last?.content || []).filter((p: any) => p.type === "text").map((p: any) => p.text).join("\n"); await this.finish(job, last?.stopReason === "error" || last?.stopReason === "aborted" ? "failed" : "completed", output || "Task ended without a text response. Check the workspace and tool activity."); }
        }
      }
      for (const bot of this.records.filter(b => b.enabled)) {
        const day = new Date(now).toISOString().slice(0, 10); if (bot.day !== day) { bot.day = day; bot.runs = 0; }
        if (bot.intervalMinutes && now >= bot.nextWake && bot.runs < bot.dailyLimit) {
          bot.nextWake = now + bot.intervalMinutes * 60000;
          if (!this.jobs.some(j => j.botId === bot.id && ["queued", "arming", "running", "waiting", "paused"].includes(j.status))) await this.enqueue(bot.id, bot.heartbeat, "routine");
          await this.changed();
        }
        if (bot.runs >= bot.dailyLimit || this.jobs.some(j => j.botId === bot.id && ["arming", "running", "waiting", "paused"].includes(j.status))) continue;
        if (this.jobs.filter(j => ["arming", "running", "waiting"].includes(j.status)).length >= 2) break;
        const job = this.jobs.find(j => j.botId === bot.id && j.status === "queued"); if (!job) continue;
        if (bot.kind !== "coding" && (this.mobile.anyBusy() || this.jobs.some(j => j.mobileTaskId && ["arming", "running", "waiting"].includes(j.status)))) continue;
        job.updated = now; bot.runs++;
        try {
          if (bot.kind === "coding") { job.status = "running"; await this.changed(); this.sessions.setTrust(bot.sessionId, bot.allowShell); await this.sessions.prompt(bot.sessionId, job.text, await this.profile(bot, job)); }
          else { if (!job.mobileTaskId) { const task = await this.mobile.create(bot.role + "\n\n" + (bot.kind === "browser" ? "Use the phone's browser for this task. " : "") + job.text); job.mobileTaskId = task.id; } job.status = "arming"; await this.changed(); }
        } catch (e) { await this.finish(job, "failed", this.redact(String(e))); }
      }
    } finally { this.ticking = false; }
  }
  async close() { this.closing = true; if (this.timer) clearInterval(this.timer); for (const bot of this.records) await this.pause(bot.id, false); }
}
