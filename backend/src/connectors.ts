import { randomUUID, randomBytes } from "node:crypto";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { auth, type OAuthClientProvider } from "@modelcontextprotocol/sdk/client/auth.js";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import type { Store } from "./storage.js";

export type Connector = { id: string; name: string; transport: "http" | "stdio"; url: string;
  command: string; args: string[]; token: string; enabled: boolean; env?: Record<string,string>; headers?: Record<string,string>; oauth?: boolean; oauthData?: { tokens?: any; client?: any; verifier?: string }; };

/** Private MCP configurations; official SDK transports run on the phone's Node runtime. */
export class Connectors {
  private records = new Map<string, Connector>();
  private clients = new Map<string, Promise<Client>>();
  private pending = new Map<string, { id: string; expires: number; url?: string }>();
  constructor(private store: Store, private oauthRedirect = "http://127.0.0.1:8787/api/oauth/mcp/callback") {}
  async init() { for (const c of await this.store.read<Connector[]>("connectors", [])) this.records.set(c.id, c); }
  list() { return [...this.records.values()].map(({ token, env, headers, oauthData, ...c }) => ({ ...c, hasToken: !!token, envKeys: Object.keys(env || {}), headerKeys: Object.keys(headers || {}), authStatus: c.oauth ? oauthData?.tokens ? "authorized" : "sign_in_required" : "configured" })); }
  redact(text: string) { for (const c of this.records.values()) for (const secret of [c.token, ...Object.values(c.env || {}), ...Object.values(c.headers || {}), c.oauthData?.tokens?.access_token, c.oauthData?.tokens?.refresh_token, c.oauthData?.client?.client_secret, c.oauthData?.verifier]) if (typeof secret === "string" && secret) text = text.split(secret).join("[redacted]"); return text; }
  require(ids: string[]) { for (const id of ids) { const c = this.records.get(id); if (!c?.enabled) throw Error("A selected MCP connection is missing or disabled"); } }
  async save(input: any) {
    const previous = input.id ? this.records.get(input.id) : undefined;
    if (input.id && !previous) throw Error("Connection not found");
    if (!previous && this.records.size >= 64) throw Error("Connection limit reached");
    if (typeof input.name !== "string" || !input.name.trim() || input.name.length > 80) throw Error("Enter a connection name (1–80 characters)");
    if (!["http", "stdio"].includes(input.transport)) throw Error("Choose HTTP or stdio transport");
    const sameTarget = !previous || previous.transport === input.transport && (input.transport === "http" ? previous.url === input.url : previous.command === input.command);
    const c: Connector = { id: previous?.id || randomUUID(), name: input.name.trim(), transport: input.transport,
      url: input.url || "", command: input.command || "", args: input.args || [],
      token: input.clearToken ? "" : input.token || (sameTarget ? previous?.token : "") || "", enabled: input.enabled !== false, env: this.secretMap(input.env, sameTarget ? previous?.env : undefined), headers: this.secretMap(input.headers, sameTarget ? previous?.headers : undefined, true), oauth: input.oauth === undefined ? previous?.oauth : input.oauth === true, oauthData: previous?.url === input.url ? previous?.oauthData : undefined };
    if (typeof c.token !== "string" || c.token.length > 8000 || /[\r\n]/.test(c.token)) throw Error("Invalid bearer token");
    if (c.transport === "http") {
      const url = new URL(c.url);
      if (url.username || url.password || url.search || url.hash || (url.protocol !== "https:" && !(url.protocol === "http:" && ["127.0.0.1", "localhost", "[::1]"].includes(url.hostname)))) throw Error("Use HTTPS, or loopback HTTP, without URL credentials/query parameters");
    } else if (typeof c.command !== "string" || !c.command.trim() || c.command.length > 500 || !Array.isArray(c.args) || c.args.length > 40 || c.args.some(a => typeof a !== "string" || a.length > 2000)) throw Error("Enter an executable and JSON array of arguments");
    await this.disconnect(c.id); this.records.set(c.id, c); await this.persist(); return this.list().find(item => item.id === c.id)!;
  }
  private secretMap(input: unknown, previous?: Record<string,string>, headers=false) {
    if(input === undefined) return previous || {};
    if(!input || typeof input !== "object" || Array.isArray(input) || Object.keys(input).length>32) throw Error("Use an object of at most 32 secret values");
    const result: Record<string,string>={};
    for(const [name,value] of Object.entries(input)) {
      if(!(headers ? /^[A-Za-z][A-Za-z0-9-]{0,79}$/ : /^[A-Za-z_][A-Za-z0-9_]{0,79}$/).test(name) || typeof value!=="string" || value.length>8000 || /[\r\n\0]/.test(value) || headers && /^(host|content-length|connection|cookie)$/i.test(name)) throw Error("Invalid secret variable or header");
      result[name]=value;
    } return result;
  }
  private oauthProvider(c: Connector): OAuthClientProvider {
    c.oauthData ??= {};
    const data=c.oauthData; const save=()=>this.persist();
    return {
      redirectUrl: this.oauthRedirect,
      clientMetadata: {client_name:"PockoBots",redirect_uris:[this.oauthRedirect],grant_types:["authorization_code","refresh_token"],response_types:["code"],token_endpoint_auth_method:"none"},
      state:()=>{const state=randomBytes(32).toString("hex");this.pending.set(state,{id:c.id,expires:Date.now()+600000});return state;},
      clientInformation:()=>data.client, saveClientInformation:async value=>{data.client=value;await save();},
      tokens:()=>data.tokens, saveTokens:async value=>{data.tokens=value;await save();},
      saveCodeVerifier:async value=>{data.verifier=value;await save();},codeVerifier:()=>{if(!data.verifier)throw Error("OAuth login expired");return data.verifier;},
      redirectToAuthorization:async url=>{if(url.protocol!=="https:"&&!(url.protocol==="http:"&&["127.0.0.1","localhost","[::1]"].includes(url.hostname)))throw Error("OAuth requires HTTPS");const state=url.searchParams.get("state"),pending=state?this.pending.get(state):undefined;if(!pending||pending.id!==c.id)throw Error("OAuth state was not issued by this app");pending.url=url.href;},
      invalidateCredentials:async scope=>{if(scope==="all"||scope==="tokens")delete data.tokens;if(scope==="all"||scope==="client")delete data.client;if(scope==="all"||scope==="verifier")delete data.verifier;await save();}
    };
  }
  async startOAuth(id: string) {
    this.require([id]);const c=this.records.get(id)!;if(c.transport!=="http"||!c.oauth)throw Error("This connection does not use MCP OAuth");
    for(const [state,p] of this.pending)if(p.id===id||p.expires<Date.now())this.pending.delete(state);
    await this.disconnect(id); const result=await auth(this.oauthProvider(c),{serverUrl:c.url,fetchFn:(url,options)=>fetch(url,{...options,signal:AbortSignal.timeout(20000)})});
    if(result==="AUTHORIZED")return {status:"authorized"};
    const pending=[...this.pending.values()].find(p=>p.id===id&&p.url);if(!pending)throw Error("OAuth server did not supply an authorization URL");return {status:"authorization_required",authorizationUrl:pending.url};
  }
  async finishOAuth(state: unknown, code: unknown) {
    if(typeof state!=="string"||typeof code!=="string"||!code||code.length>8000)throw Error("Invalid OAuth callback");const pending=this.pending.get(state);this.pending.delete(state);
    if(!pending||pending.expires<Date.now())throw Error("OAuth login expired or state does not match");
    const c=this.records.get(pending.id);if(!c?.oauth)throw Error("OAuth connection was removed");
    const result=await auth(this.oauthProvider(c),{serverUrl:c.url,authorizationCode:code,fetchFn:(url,options)=>fetch(url,{...options,signal:AbortSignal.timeout(20000)})});
    if(result!=="AUTHORIZED")throw Error("OAuth authorization did not finish");if(c.oauthData)delete c.oauthData.verifier;await this.persist();return {id:c.id,status:"authorized"};
  }
  private persist() { return this.store.write("connectors", [...this.records.values()]); }
  async remove(id: string) { await this.disconnect(id); this.records.delete(id); await this.persist(); }
  private async disconnect(id: string) { const client = this.clients.get(id); this.clients.delete(id); if (client) try { await (await client).close(); } catch {} }
  private client(id: string): Promise<Client> {
    this.require([id]); const existing = this.clients.get(id); if (existing) return existing;
    const c = this.records.get(id)!;
    const pending = (async () => {
      const client = new Client({ name: "pocket-agent", version: "0.6.0" }, { capabilities: {} });
      const transport = c.transport === "http"
        ? new StreamableHTTPClientTransport(new URL(c.url), { authProvider: c.oauth ? this.oauthProvider(c) : undefined, requestInit: { headers: { ...c.headers, ...(c.token ? { Authorization: "Bearer " + c.token } : {}) } } })
        : new StdioClientTransport({ command: c.command, args: c.args, env: {...process.env as Record<string,string>, ...c.env}, stderr: "pipe" });
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
