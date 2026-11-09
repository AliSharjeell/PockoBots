import type { Express } from "express";
import { randomUUID } from "node:crypto";
import type { Store } from "./storage.js";

export type TaskStatus = "queued" | "running" | "waiting_input" | "waiting_approval" | "paused" | "completed" | "failed" | "cancelled";
export type TaskOwner = "phone" | "server";
export type StepStatus = "pending" | "running" | "done" | "failed" | "uncertain";
export type Receipt = { at: number; result: string; reconciled?: boolean };
export type TaskStep = { id: string; title: string; external: boolean; status: StepStatus; progress?: string; receipt?: Receipt; error?: string; approvalId?: string };
export type TaskRequest = { id: string; kind: "input" | "approval"; stepId: string; prompt: string; created: number;
  status: "open" | "answered" | "stale"; answer?: string | boolean; answered?: number; used?: boolean };
export type Task = { id: string; title: string; owner: TaskOwner; status: TaskStatus; steps: TaskStep[]; requests: TaskRequest[];
  lease?: { id: string; until: number }; error?: string; created: number; updated: number };

const moves: Record<TaskStatus, TaskStatus[]> = {
  queued: ["running", "paused", "cancelled"],
  running: ["queued", "waiting_input", "waiting_approval", "paused", "completed", "failed", "cancelled"],
  waiting_input: ["queued", "paused", "cancelled"],
  waiting_approval: ["queued", "paused", "failed", "cancelled"],
  paused: ["queued", "completed", "cancelled"],
  failed: ["queued", "completed", "cancelled"],
  cancelled: ["queued"],
  completed: [],
};
const owners: TaskOwner[] = ["phone", "server"];
const conflict = (message: string, status = 409) => Object.assign(Error(message), { status });
const text = (value: unknown, name: string, max = 4000) => {
  if (typeof value !== "string" || !value.trim() || value.length > max) throw conflict(`Enter ${name} of at most ${max} characters`, 400);
  return value.trim();
};

/**
 * Durable tasks: a plan of steps run by one owner (phone or server) under an expiring lease.
 * External steps run only after a recorded approval; finished steps keep receipts so retry skips them;
 * a step interrupted mid-flight is "uncertain" until the user reconciles it.
 */
// ponytail: every change rewrites one tasks.json (atomic, single backend process). Fine to the
// 200-task cap below; move to one file per task or node:sqlite if tasks must outgrow it or a
// second process ever writes them.
export class Tasks {
  private records = new Map<string, Task>();
  constructor(private store: Store, private publish: (event: unknown) => void = () => {}, private now = Date.now) {}
  async init() {
    for (const task of await this.store.read<Task[]>("tasks", [])) this.records.set(task.id, task);
    await this.recover();
  }
  list() { return [...this.records.values()].sort((a, b) => b.updated - a.updated); }
  get(id: string) { const task = this.records.get(id); if (!task) throw conflict("Task not found", 404); return task; }

  async create(input: any) {
    if (this.records.size >= 200) throw conflict("Delete old tasks before creating another");
    const owner = input?.owner ?? "server";
    if (!owners.includes(owner)) throw conflict("Owner must be phone or server", 400);
    if (!Array.isArray(input?.steps) || !input.steps.length || input.steps.length > 50) throw conflict("Give a plan of 1 to 50 steps", 400);
    const steps: TaskStep[] = input.steps.map((s: any) => ({ id: randomUUID(), title: text(s?.title, "a step title", 500), external: s?.external === true, status: "pending" }));
    const task: Task = { id: randomUUID(), title: text(input.title, "a task title", 500), owner, status: "queued", steps, requests: [], created: this.now(), updated: this.now() };
    this.records.set(task.id, task);
    await this.changed(task);
    return task;
  }
  async remove(id: string) {
    const task = this.get(id);
    if (!["completed", "failed", "cancelled"].includes(task.status)) throw conflict("Cancel the task before deleting it");
    this.records.delete(id); await this.persist(); this.publish({ type: "task-removed", id });
  }

  // Owner controls.
  async pause(id: string) { const task = this.get(id); this.move(task, "paused"); this.interrupt(task); await this.changed(task); return task; }
  async resume(id: string) {
    const task = this.get(id);
    if (task.status !== "paused") throw conflict("Only a paused task can resume");
    this.settled(task); this.move(task, "queued"); task.error = undefined; await this.changed(task); return task;
  }
  async cancel(id: string) { const task = this.get(id); this.move(task, "cancelled"); this.interrupt(task); await this.changed(task); return task; }
  /** Failed or cancelled back to queued. Steps with receipts stay done and are skipped. */
  async retry(id: string) {
    const task = this.get(id);
    if (!["failed", "cancelled"].includes(task.status)) throw conflict("Only a failed or cancelled task can retry");
    this.settled(task);
    for (const step of task.steps) if (step.status === "failed") { step.status = "pending"; step.error = undefined; }
    this.move(task, "queued"); task.error = undefined; await this.changed(task); return task;
  }
  async handoff(id: string, owner: unknown) {
    const task = this.get(id);
    if (!owners.includes(owner as TaskOwner)) throw conflict("Owner must be phone or server", 400);
    if (task.lease) throw conflict("Pause the task before moving it to another owner");
    task.owner = owner as TaskOwner; await this.changed(task); return task;
  }
  /** Answer the one open request. A stale, repeated or mistyped answer is rejected. */
  async answer(id: string, requestId: string, value: unknown) {
    const task = this.get(id), request = task.requests.find(r => r.id === requestId);
    if (!request) throw conflict("Request not found", 404);
    if (request.status !== "open" || task.status !== (request.kind === "input" ? "waiting_input" : "waiting_approval")) throw conflict("This request was already answered or is no longer current");
    if (request.kind === "approval" ? typeof value !== "boolean" : typeof value !== "string" || !value.trim() || value.length > 16000) throw conflict(request.kind === "approval" ? "Approve with true or decline with false" : "Enter an answer of at most 16000 characters", 400);
    Object.assign(request, { status: "answered", answer: value, answered: this.now() });
    if (value === false) { this.move(task, "failed"); task.error = "Action declined"; } else this.move(task, "queued");
    await this.changed(task); return task;
  }
  /** Settle an uncertain step: it happened (receipt) or it did not (back to pending, approval needed again). */
  async reconcile(id: string, stepId: string, happened: unknown, note?: unknown) {
    const task = this.get(id), step = this.step(task, stepId);
    if (step.status !== "uncertain") throw conflict("Only an uncertain step can be reconciled");
    if (typeof happened !== "boolean") throw conflict("Say whether the action happened", 400);
    const result = note === undefined ? (happened ? "Confirmed by the user" : "") : text(note, "a note");
    if (happened) Object.assign(step, { status: "done", receipt: { at: this.now(), result, reconciled: true }, error: undefined });
    else Object.assign(step, { status: "pending", error: undefined, approvalId: undefined });
    if (task.steps.every(s => s.status === "done") && moves[task.status].includes("completed")) { this.move(task, "completed"); task.error = undefined; }
    await this.changed(task); return task;
  }

  // Worker calls. Every call after claim names the lease; a lost or expired lease is rejected.
  async claim(id: string, owner: unknown, leaseMs: unknown = 60000) {
    await this.recover();
    const task = this.get(id);
    if (task.owner !== owner) throw conflict(`This task belongs to the ${task.owner}`);
    if (task.status !== "queued") throw conflict("Only a queued task can be claimed");
    this.settled(task);
    this.move(task, "running"); task.lease = { id: randomUUID(), until: this.now() + this.duration(leaseMs) };
    await this.changed(task); return { leaseId: task.lease.id, task };
  }
  async renew(id: string, leaseId: unknown, leaseMs: unknown = 60000) {
    const task = this.held(id, leaseId); task.lease!.until = this.now() + this.duration(leaseMs); await this.changed(task); return task;
  }
  /** Marks the next step in flight and saves that before the caller acts, so a crash leaves a trace. */
  async startStep(id: string, leaseId: unknown, stepId: string) {
    const task = this.held(id, leaseId), step = this.step(task, stepId);
    if (task.steps.find(s => s.status !== "done") !== step || step.status !== "pending") throw conflict("Start the next pending step");
    if (step.external) {
      const approval = task.requests.find(r => r.kind === "approval" && r.stepId === step.id && r.answer === true && !r.used);
      if (!approval) throw conflict("This external action needs a recorded approval first");
      approval.used = true; step.approvalId = approval.id;
    }
    step.status = "running"; await this.changed(task); return task;
  }
  async progress(id: string, leaseId: unknown, stepId: string, note: unknown) {
    const task = this.held(id, leaseId), step = this.running(task, stepId);
    step.progress = text(note, "a progress note", 1000); await this.changed(task); return task;
  }
  async finishStep(id: string, leaseId: unknown, stepId: string, result: unknown) {
    const task = this.held(id, leaseId), step = this.running(task, stepId);
    const receipt = { at: this.now(), result: text(result, "a receipt") };
    step.status = "done"; step.receipt = receipt;
    if (task.steps.every(s => s.status === "done")) { this.move(task, "completed"); task.lease = undefined; }
    await this.changed(task); return task;
  }
  /** uncertain=true when the action may have happened; the task pauses until the user reconciles it. */
  async failStep(id: string, leaseId: unknown, stepId: string, error: unknown, uncertain = false) {
    const task = this.held(id, leaseId), step = this.running(task, stepId);
    const message = text(error, "an error");
    step.status = uncertain ? "uncertain" : "failed"; step.error = message;
    this.move(task, uncertain ? "paused" : "failed"); task.lease = undefined;
    task.error = uncertain ? `Check whether "${step.title}" happened, then reconcile it` : step.error;
    await this.changed(task); return task;
  }
  /** Ask the user for input or for approval of an external step. Releases the lease until answered. */
  async ask(id: string, leaseId: unknown, stepId: string, kind: unknown, prompt: unknown) {
    const task = this.held(id, leaseId), step = this.step(task, stepId);
    if (kind !== "input" && kind !== "approval") throw conflict("Ask for input or approval", 400);
    if (step.status !== "pending") throw conflict("Ask before starting the step");
    if (kind === "approval" && !step.external) throw conflict("Only external steps need approval", 400);
    if (task.requests.length >= 100) throw conflict("This task has asked too many questions");
    const request: TaskRequest = { id: randomUUID(), kind, stepId, prompt: text(prompt, "a question", 4000), created: this.now(), status: "open" };
    task.requests.push(request); this.move(task, kind === "input" ? "waiting_input" : "waiting_approval"); task.lease = undefined;
    await this.changed(task); return request;
  }

  /** Expired leases return their task to the queue; an external step caught in flight becomes uncertain. */
  async recover() {
    for (const task of this.records.values())
      if (task.status === "running" && (!task.lease || task.lease.until <= this.now())) {
        this.interrupt(task);
        if (task.steps.some(s => s.status === "uncertain")) { this.move(task, "paused"); task.error = "Interrupted during an external action. Reconcile it before resuming."; }
        else this.move(task, "queued");
        await this.changed(task);
      }
  }

  private move(task: Task, to: TaskStatus) {
    if (!moves[task.status].includes(to)) throw conflict(`A ${task.status} task cannot become ${to}`);
    task.status = to;
  }
  /** Drop the lease, close open requests, and settle any step that was in flight. */
  private interrupt(task: Task) {
    task.lease = undefined;
    for (const r of task.requests) if (r.status === "open") r.status = "stale";
    for (const s of task.steps) if (s.status === "running") s.status = s.external ? "uncertain" : "pending";
  }
  private settled(task: Task) { if (task.steps.some(s => s.status === "uncertain")) throw conflict("Reconcile the uncertain step first"); }
  private held(id: string, leaseId: unknown) {
    const task = this.get(id);
    if (task.status !== "running" || !task.lease || task.lease.id !== leaseId || task.lease.until <= this.now()) throw conflict("This worker no longer holds the task lease");
    return task;
  }
  private step(task: Task, stepId: string) { const step = task.steps.find(s => s.id === stepId); if (!step) throw conflict("Step not found", 404); return step; }
  private running(task: Task, stepId: string) { const step = this.step(task, stepId); if (step.status !== "running") throw conflict("Start the step first"); return step; }
  private duration(ms: unknown) { if (!Number.isInteger(ms) || (ms as number) < 1000 || (ms as number) > 600000) throw conflict("Lease must be 1 to 600 seconds", 400); return ms as number; }
  private persist() { return this.store.write("tasks", [...this.records.values()]); }
  private async changed(task: Task) { task.updated = this.now(); await this.persist(); this.publish({ type: "task", task }); }
}

export function registerTaskRoutes(app: Express, tasks: Tasks) {
  const b = (req: any) => req.body ?? {};
  app.get("/api/tasks", async (_req, res) => { await tasks.recover(); res.json(tasks.list()); });
  app.post("/api/tasks", async (req, res) => res.status(201).json(await tasks.create(b(req))));
  app.get("/api/tasks/:id", (req, res) => res.json(tasks.get(req.params.id)));
  app.delete("/api/tasks/:id", async (req, res) => { await tasks.remove(req.params.id); res.json({ ok: true }); });
  app.post("/api/tasks/:id/pause", async (req, res) => res.json(await tasks.pause(req.params.id)));
  app.post("/api/tasks/:id/resume", async (req, res) => res.json(await tasks.resume(req.params.id)));
  app.post("/api/tasks/:id/cancel", async (req, res) => res.json(await tasks.cancel(req.params.id)));
  app.post("/api/tasks/:id/retry", async (req, res) => res.json(await tasks.retry(req.params.id)));
  app.put("/api/tasks/:id/owner", async (req, res) => res.json(await tasks.handoff(req.params.id, b(req).owner)));
  app.post("/api/tasks/:id/requests/:request", async (req, res) => res.json(await tasks.answer(req.params.id, req.params.request, b(req).value)));
  app.post("/api/tasks/:id/steps/:step/reconcile", async (req, res) => res.json(await tasks.reconcile(req.params.id, req.params.step, b(req).happened, b(req).note)));
  app.post("/api/tasks/:id/claim", async (req, res) => res.json(await tasks.claim(req.params.id, b(req).owner, b(req).leaseMs)));
  app.post("/api/tasks/:id/lease", async (req, res) => res.json(await tasks.renew(req.params.id, b(req).leaseId, b(req).leaseMs)));
  app.post("/api/tasks/:id/steps/:step/start", async (req, res) => res.json(await tasks.startStep(req.params.id, b(req).leaseId, req.params.step)));
  app.post("/api/tasks/:id/steps/:step/progress", async (req, res) => res.json(await tasks.progress(req.params.id, b(req).leaseId, req.params.step, b(req).note)));
  app.post("/api/tasks/:id/steps/:step/finish", async (req, res) => res.json(await tasks.finishStep(req.params.id, b(req).leaseId, req.params.step, b(req).result)));
  app.post("/api/tasks/:id/steps/:step/fail", async (req, res) => res.json(await tasks.failStep(req.params.id, b(req).leaseId, req.params.step, b(req).error, b(req).uncertain === true)));
  app.post("/api/tasks/:id/steps/:step/ask", async (req, res) => res.status(201).json(await tasks.ask(req.params.id, b(req).leaseId, req.params.step, b(req).kind, b(req).prompt)));
}
