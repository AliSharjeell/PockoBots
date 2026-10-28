import { randomUUID } from "node:crypto";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import type { Store } from "./storage.js";

type Connector = { id: string; name: string; transport: "http" | "stdio"; url: string;
  command: string; args: string[]; token: string; enabled: boolean };

/** Private MCP configurations; official SDK transports run on the phone's Node runtime. */
export class Connectors {
  private records = new Map<string, Connector>();
  private clients = new Map<string, Promise<Client>>();
  constructor(private store: Store) {}
  async init() { for (const c of await this.store.read<Connector[]>("connectors", [])) this.records.set(c.id, c); }
  list() { return [...this.records.values()].map(({ token, ...c }) => ({ ...c, hasToken: !!token })); }
  redact(text: string) { for (const c of this.records.values()) if (c.token) text = text.split(c.token).join("[redacted]"); return text; }
  require(ids: string[]) { for (const id of ids) { const c = this.records.get(id); if (!c?.enabled) throw Error("A selected MCP connection is missing or disabled"); } }
  async save(input: any) {
    const previous = input.id ? this.records.get(input.id) : undefined;
    if (input.id && !previous) throw Error("Connection not found");
    if (!previous && this.records.size >= 12) throw Error("Connection limit reached");
    if (typeof input.name !== "string" || !input.name.trim() || input.name.length > 80) throw Error("Enter a connection name (1–80 characters)");
    if (!["http", "stdio"].includes(input.transport)) throw Error("Choose HTTP or stdio transport");
    const c: Connector = { id: previous?.id || randomUUID(), name: input.name.trim(), transport: input.transport,
      url: input.url || "", command: input.command || "", args: input.args || [],
      token: input.clearToken ? "" : input.token || previous?.token || "", enabled: input.enabled !== false };
    if (typeof c.token !== "string" || c.token.length > 8000 || /[\r\n]/.test(c.token)) throw Error("Invalid bearer token");
    if (c.transport === "http") {
      const url = new URL(c.url);
      if (url.username || url.password || url.search || url.hash || (url.protocol !== "https:" && !(url.protocol === "http:" && ["127.0.0.1", "localhost", "[::1]"].includes(url.hostname)))) throw Error("Use HTTPS, or loopback HTTP, without URL credentials/query parameters");
    } else if (typeof c.command !== "string" || !c.command.trim() || c.command.length > 500 || !Array.isArray(c.args) || c.args.length > 40 || c.args.some(a => typeof a !== "string" || a.length > 2000)) throw Error("Enter an executable and JSON array of arguments");
    await this.disconnect(c.id); this.records.set(c.id, c); await this.persist(); return this.list().find(item => item.id === c.id)!;
  }
  private persist() { return this.store.write("connectors", [...this.records.values()]); }
  async remove(id: string) { await this.disconnect(id); this.records.delete(id); await this.persist(); }
  private async disconnect(id: string) { const client = this.clients.get(id); this.clients.delete(id); if (client) try { await (await client).close(); } catch {} }
  private client(id: string): Promise<Client> {
    this.require([id]); const existing = this.clients.get(id); if (existing) return existing;
    const c = this.records.get(id)!;
    const pending = (async () => {
      const client = new Client({ name: "pocket-agent", version: "0.4.0" }, { capabilities: {} });
      const transport = c.transport === "http"
        ? new StreamableHTTPClientTransport(new URL(c.url), { requestInit: { headers: c.token ? { Authorization: "Bearer " + c.token } : {} } })
        : new StdioClientTransport({ command: c.command, args: c.args, stderr: "pipe" });
      if (transport instanceof StdioClientTransport) transport.stderr?.on("data", () => {});
      try { await client.connect(transport, { timeout: 20000 }); return client; }
      catch (e) { await client.close().catch(() => {}); this.clients.delete(id); throw Error("MCP connection failed: " + this.redact(String(e))); }
    })(); this.clients.set(id, pending); return pending;
  }
  async inspect(id: string) { const client = await this.client(id); const result = await client.listTools({}, { timeout: 20000 }); return { tools: result.tools.map(t => ({ name: t.name, description: t.description })), connected: true }; }
  async tools(ids: string[]): Promise<AgentTool<any>[]> {
    this.require(ids); const tools: AgentTool<any>[] = [];
    for (const id of ids) {
      const client = await this.client(id); let cursor: string | undefined;
      for (let page = 0; page < 5; page++) {
        const result = await client.listTools({ cursor }, { timeout: 20000 });
        for (const t of result.tools) {
          if (tools.length >= 64) throw Error("Selected connections expose too many tools (maximum 64)");
          const name = "mcp_" + id.replaceAll("-", "").slice(0, 8) + "_" + t.name.replace(/[^a-zA-Z0-9_]/g, "_").slice(0, 42);
          if (tools.some(other => other.name === name)) throw Error("MCP tool names conflict");
          tools.push({ name, label: t.name, description: (this.records.get(id)!.name + ": " + (t.description || t.name)).slice(0, 3000), parameters: t.inputSchema as any,
            execute: async (_call, args, signal) => {
              const result = await client.callTool({ name: t.name, arguments: args as Record<string, unknown> }, undefined, { signal, timeout: 30000 });
              return { content: [{ type: "text" as const, text: this.redact(JSON.stringify(result)).slice(0, 30000) }], details: {}, isError: !!result.isError };
            } });
        }
        cursor = result.nextCursor; if (!cursor) break;
      }
    }
    return tools;
  }
  async close() { await Promise.all([...this.clients.keys()].map(id => this.disconnect(id))); }
}
