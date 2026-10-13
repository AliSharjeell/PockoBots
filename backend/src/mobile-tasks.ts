import { Agent } from "@earendil-works/pi-agent-core";
import { randomUUID } from "node:crypto";
import { ArtemisBridge } from "./artemis.js";
import { artemisPrompt } from "./artemis-prompt.js";
import { mobileTools } from "./mobile-tools.js";
import { connection } from "./models.js";
import type { Settings, Store } from "./storage.js";

export type MobileStatus = "queued" | "running" | "waiting" | "paused" | "completed" | "failed" | "stopped";
export type MobileTask = { id: string; prompt: string; status: MobileStatus; created: number; updated: number;
  steps: number; output: string; error?: string; question?: { id: string; text: string }; history: { role: string; text: string }[];
  trace: { time: number; text: string }[] };

/** One device, one task. Model requests and actions continue independently of Activity/SSE. */
export class MobileTasks {
  private records = new Map<string, MobileTask>();
  private agents = new Map<string, Agent>();
  private questions = new Map<string, { resolve: (value: string) => void; reject: (e: Error) => void }>();
  private launching = false;
  constructor(private store: Store, private settings: () => Settings, private token: string,
    private publish: (event: unknown) => void, private bridgeBase?: string) {}
  async init() {
    for (const task of await this.store.read<MobileTask[]>("mobile-tasks", [])) {
      if (["running", "waiting"].includes(task.status)) { task.status = "paused"; task.error = "Runtime restarted. Review the current app, then resume."; }
      this.records.set(task.id, task);
    }
    await this.persist();
  }
  private persist() { return this.store.write("mobile-tasks", [...this.records.values()]); }
  private async changed(task: MobileTask) { task.updated = Date.now(); await this.persist(); this.publish({ type: "mobile", task }); }
  list() { return [...this.records.values()].map(({ history, ...task }) => task); }
  state(id: string) { const task = this.records.get(id); if (!task) throw Error("Mobile task not found"); return task; }
  health() { return new ArtemisBridge(this.token, this.bridgeBase).health(); }
  anyBusy() { return this.agents.size > 0 || this.launching; }
  async create(prompt: unknown) {
    if (typeof prompt !== "string" || !prompt.trim() || prompt.length > 16000) throw Error("Enter a mobile task of at most 16000 characters");
    if (this.records.size >= 30) throw Error("Delete an old mobile task before creating another");
    const task: MobileTask = { id: randomUUID(), prompt: prompt.trim(), status: "queued", created: Date.now(), updated: Date.now(), steps: 0, output: "", history: [], trace: [] };
    this.records.set(task.id, task); await this.changed(task); return task;
  }
  async remove(id: string) { if (this.agents.has(id)) throw Error("Stop the task first"); this.records.delete(id); await this.persist(); }
  async start(id: string, reply?: string) {
    const task = this.state(id);
    if (!["queued", "paused"].includes(task.status)) throw Error("Only queued or paused tasks can start");
    if (this.anyBusy()) throw Error("Another mobile task is using this device");
    this.launching = true;
    try {
    const bridge = new ArtemisBridge(this.token, this.bridgeBase);
    const health = await bridge.health();
    if (!health.ready || !health.authenticated || !health.task_allowed) throw Error("Enable Accessibility and start the Mobile Use foreground controller before resuming");
    const settings = { ...this.settings() };
    if (!settings.apiKey && !["127.0.0.1", "localhost", "[::1]"].includes(new URL(settings.baseUrl).hostname)) throw Error("Configure your BYOK model connection first");
    const { model, models } = connection(settings, true);
    task.status = "running"; task.error = undefined; task.question = undefined;
    if (reply) task.history.push({ role: "user", text: reply });
    let turns = 0; let currentTool = ""; let lastPreview = 0;
    const safe = (text: string) => settings.apiKey ? text.split(settings.apiKey).join("[redacted]") : text;
    const ask = async (question: string, signal?: AbortSignal) => {
      task.status = "waiting"; task.question = { id: randomUUID(), text: safe(question) };
      await this.changed(task);
      return new Promise<string>((resolve, reject) => {
        const abort = () => { this.questions.delete(id); reject(Error("Task interrupted")); };
        this.questions.set(id, { resolve: (value) => { signal?.removeEventListener("abort", abort); resolve(value); }, reject });
        if (signal?.aborted) abort(); else signal?.addEventListener("abort", abort, { once: true });
      });
    };
    const report = async (status: "completed" | "failed", summary: string) => {
      if (status === "completed" && (!bridge.last || Date.now() - bridge.last.captured > 30000)) throw Error("Observe the current screen and verify the outcome before reporting completion");
      task.status = status; task.output = safe(summary); task.question = undefined;
      await this.changed(task);
    };
    const agent = new Agent({ streamFn: models.streamSimple.bind(models), getApiKey: () => settings.apiKey || undefined,
      toolExecution: "sequential", initialState: { model, systemPrompt: artemisPrompt, tools: mobileTools(bridge, ask, report) },
      beforeToolCall: async (context) => {
        if (task.status !== "running") return { block: true, reason: "Task is paused or already finished" };
        currentTool = context.toolCall.name;
        task.trace.push({ time: Date.now(), text: currentTool.replaceAll("_", " ") }); task.trace = task.trace.slice(-200);
        task.steps++; await this.changed(task);
      },
      finishTurn: async () => {
        // Keep complete assistant/tool groups and two recent screenshots. Persistent
        // history is text only; no full-screen image archive is written to disk.
        let images = 0;
        for (const message of [...agent.state.messages].reverse()) if (Array.isArray((message as any).content))
          (message as any).content = (message as any).content.filter((part: any) => part.type !== "image" || ++images <= 2);
        if (agent.state.messages.length > 48) {
          let cut = agent.state.messages.length - 40;
          while (cut < agent.state.messages.length && agent.state.messages[cut].role !== "assistant") cut++;
          agent.state.messages = [agent.state.messages[0], ...agent.state.messages.slice(cut)];
        }
        if (task.status !== "running") return { action: "end" };
        if (++turns >= 120) { task.status = "paused"; task.error = "Reached 120 turns. Review progress and resume to continue."; await this.changed(task); return { action: "end" }; }
      },
    });
    agent.subscribe((event: any) => {
      if (event.type === "message_update" && task.status === "running" && Date.now() - lastPreview > 400) {
        const streaming: any = agent.state.streamingMessage;
        const preview = (streaming?.content || []).filter((p: any) => p.type === "text").map((p: any) => p.text).join("\n");
        if (preview) { lastPreview = Date.now(); task.output = safe(preview).slice(0, 6000); void this.changed(task).catch(() => {}); }
      }
      if (event.type === "message_end") {
        const message = event.message;
        if (message.role === "assistant" && message.stopReason === "error" && task.status === "running") {
          task.status = "paused"; task.error = safe(message.errorMessage || "The model request failed. Review your connection and resume.");
        }
        const value = safe(typeof message.content === "string" ? message.content : (message.content || []).filter((p: any) => p.type === "text").map((p: any) => p.text).join("\n"));
        if (value) task.history.push({ role: message.role, text: value.slice(0, 10000) });
        task.history = task.history.slice(-50);
        if (message.role === "assistant" && value && task.status === "running") task.output = value.slice(0, 6000);
        void this.changed(task).catch(() => {});
      }
      if (event.type === "tool_execution_end" && event.isError) task.trace.push({ time: Date.now(), text: currentTool + ": failed; agent must re-observe" });
    });
    this.agents.set(id, agent); await this.changed(task);
    const context = task.history.slice(-12).map((h) => h.role + ": " + h.text.slice(0, 1000)).join("\n");
    const instruction = task.prompt + (context ? "\n\nPrior task progress (verify against the live device):\n" + context : "") + "\nObserve the current screen first. Continue until you report_task_status or ask_user.";
    void (async () => {
      try {
        await agent.prompt(instruction);
        // A provider may end a text-only turn without using the termination tool.
        for (let retry = 0; task.status === "running" && retry < 2; retry++)
          await agent.prompt("Continue the original task. Observe and act, ask_user if blocked, or explicitly report_task_status after verification.");
        if (task.status === "running") { task.status = "paused"; task.error = "The model ended without a verified result. Review and resume."; }
      } catch (e: any) {
        if (task.status === "running" || task.status === "waiting") { task.status = "paused"; task.error = safe(String(e.message || e)); }
      } finally {
        this.agents.delete(id); this.questions.delete(id);
        try { await this.changed(task); } catch { task.status = "paused"; task.error = "Task progress could not be saved. Check free storage."; this.publish({ type: "mobile", task }); }
      }
    })();
    return task;
    } finally { this.launching = false; }
  }
  async answer(id: string, questionId: string, text: unknown) {
    const task = this.state(id);
    if (!task.question || task.question.id !== questionId || typeof text !== "string" || !text.trim() || text.length > 4000) throw Error("Question expired or reply is invalid");
    const waiter = this.questions.get(id);
    if (!waiter) { await this.start(id, task.question.text + "\nUser reply: " + text); return; }
    const health = await this.health();
    if (!health.task_allowed) throw Error("Resume the floating controller before replying");
    if (task.status !== "waiting" || task.question?.id !== questionId || this.questions.get(id) !== waiter) throw Error("Question expired while the task was interrupted");
    task.history.push({ role: "user", text: text.trim() }); task.question = undefined; task.status = "running";
    await this.changed(task); waiter.resolve(text.trim()); this.questions.delete(id);
  }
  async interrupt(id: string, stop = false) {
    const task = this.state(id);
    if (["completed", "failed", "stopped"].includes(task.status)) return;
    task.status = stop ? "stopped" : "paused";
    if (stop) task.question = undefined;
    task.error = stop ? undefined : "Paused by you";
    this.agents.get(id)?.abort(); this.questions.get(id)?.reject(Error("Task interrupted")); this.questions.delete(id);
    await this.changed(task);
  }
  stopAll() { for (const id of this.agents.keys()) void this.interrupt(id).catch(() => {}); }
}
