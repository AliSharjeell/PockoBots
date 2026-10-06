import { Agent, type AgentMessage } from "@earendil-works/pi-agent-core";
import { randomUUID } from "node:crypto";
import type { Workspace } from "./workspace.js";
import type { Processes } from "./processes.js";
import type { Store, Settings } from "./storage.js";
import { connection } from "./models.js";
import { agentTools } from "./tools.js";
type Session = {
  id: string;
  title: string;
  messages: AgentMessage[];
  created: number;
};
type Approval = {
  id: string;
  sessionId: string;
  tool: string;
  args: unknown;
  resolve: (allowed: boolean) => void;
};
export class Sessions {
  private records = new Map<string, Session>();
  private agents = new Map<string, Agent>();
  private busy = new Set<string>();
  approvals = new Map<string, Approval>();
  trusted = new Set<string>();
  constructor(
    private store: Store,
    private workspace: Workspace,
    private processes: Processes,
    private settings: () => Settings,
    private publish: (e: unknown) => void,
  ) {}
  async init() {
    const records = await this.store.read<Session[]>("sessions", []);
    for (const s of records) this.records.set(s.id, s);
  }
  async persist() {
    await this.store.write("sessions", [...this.records.values()]);
  }
  list() {
    return [...this.records.values()].map(({ messages, ...s }) => ({
      ...s,
      busy: this.busy.has(s.id),
      trusted: this.trusted.has(s.id),
    }));
  }
  state(id: string) {
    const s = this.records.get(id);
    if (!s) throw Error("Session not found");
    return {
      ...s,
      busy: this.busy.has(id),
      trusted: this.trusted.has(id),
      streaming: this.agents.get(id)?.state.streamingMessage,
      approvals: [...this.approvals.values()]
        .filter((a) => a.sessionId === id)
        .map(({ resolve, ...a }) => a),
    };
  }
  async create() {
    if (this.records.size >= 50)
      throw Error("Session limit reached; delete an old session");
    const s = {
      id: randomUUID(),
      title: "New conversation",
      messages: [],
      created: Date.now(),
    };
    this.records.set(s.id, s);
    await this.persist();
    return s;
  }
  async remove(id: string) {
    if (this.busy.has(id)) throw Error("Stop the active agent first");
    this.records.delete(id);
    this.agents.delete(id);
    this.trusted.delete(id);
    await this.persist();
  }
  setTrust(id: string, trust: boolean) {
    this.state(id);
    if (trust) this.trusted.add(id);
    else this.trusted.delete(id);
  }
  approve(id: string, allow: boolean) {
    const a = this.approvals.get(id);
    if (!a) throw Error("Approval expired");
    a.resolve(allow);
  }
  private permission(
    sessionId: string,
    tool: string,
    args: unknown,
    signal?: AbortSignal,
  ): Promise<boolean> {
    if (this.trusted.has(sessionId)) return Promise.resolve(true);
    if (signal?.aborted) return Promise.resolve(false);
    return new Promise((resolve) => {
      const id = randomUUID();
      const finish = (allow: boolean) => {
        clearTimeout(timer);
        signal?.removeEventListener("abort", abort);
        this.approvals.delete(id);
        this.publish({ type: "approval_resolved", sessionId, id });
        resolve(allow);
      };
      const abort = () => finish(false);
      const timer = setTimeout(abort, 300000);
      this.approvals.set(id, { id, sessionId, tool, args, resolve: finish });
      signal?.addEventListener("abort", abort, { once: true });
      this.publish({ type: "approval", sessionId, id, tool, args });
    });
  }
  async prompt(id: string, text: string) {
    const record = this.records.get(id);
    if (!record) throw Error("Session not found");
    if (this.busy.has(id)) throw Error("Agent is already running");
    if (typeof text !== "string" || !text.trim() || text.length > 32000)
      throw Error("Enter a prompt of at most 32000 characters");
    const settings = { ...this.settings() };
    const { model, models } = connection(settings);
    if (record.title === "New conversation") record.title = text.slice(0, 50);
    const agent = new Agent({
      streamFn: models.streamSimple.bind(models),
      getApiKey: () => settings.apiKey || undefined,
      toolExecution: "sequential",
      initialState: {
        systemPrompt: `You are Pocket Agent, a coding agent running locally in Termux on Android. Workspace: ${this.workspace.root}. Use file tools with relative paths. Use bash for Git, npm, tests and searches. Shell has full Termux access. Never read or disclose API keys or agent state files. Ask before destructive work. Use start_server for persistent npm servers, not bash. Report actual tool results and errors honestly. Do not claim a server or tunnel works until verified.`,
        model,
        tools: agentTools(this.workspace, this.processes),
      },
      beforeToolCall: async (context, signal) => {
        if (
          ["bash", "start_server", "stop_process"].includes(
            context.toolCall.name,
          )
        ) {
          const allow = await this.permission(
            id,
            context.toolCall.name,
            context.args,
            signal,
          );
          if (!allow)
            return { block: true, reason: "Command not approved or cancelled" };
        }
      },
      finishTurn: () => {
        if (++turns >= 40) return { action: "end" };
      },
    });
    let turns = 0;
    // Replay conversation without the prior baseline so current tool declarations take effect.
    if (record.messages.length)
      agent.state.messages = [
        ...agent.state.messages,
        ...record.messages.filter((m) => m.role !== "system"),
      ];
    this.agents.set(id, agent);
    this.busy.add(id);
    agent.subscribe(async (event) => {
      this.publish({ type: "agent", sessionId: id, event });
      if (event.type === "message_end" || event.type === "agent_end") {
        record.messages = [...agent.state.messages];
        await this.persist();
      }
    });
    // Prompt executes in the background; HTTP immediately returns and SSE carries progress.
    void agent
      .prompt(text)
      .catch((e) =>
        this.publish({
          type: "error",
          sessionId: id,
          error: String(e.message || e),
        }),
      )
      .finally(async () => {
        this.busy.delete(id);
        record.messages = [...agent.state.messages];
        try {
          await this.persist();
        } catch (e) {
          this.publish({
            type: "error",
            sessionId: id,
            error: "Failed to persist chat",
          });
        }
        this.publish({ type: "idle", sessionId: id });
      });
  }
  stop(id: string) {
    this.agents.get(id)?.abort();
  }
  stopAll() {
    for (const id of this.agents.keys()) this.stop(id);
  }
  anyBusy() {
    return this.busy.size > 0;
  }
}
