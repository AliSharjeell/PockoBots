import express, { type Response } from "express";
import { createHash, timingSafeEqual } from "node:crypto";
import { homedir } from "node:os";
import { resolve, dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  Store,
  defaults,
  publicSettings,
  validateSettings,
  type Settings,
} from "./storage.js";
import { Workspace } from "./workspace.js";
import { Processes, validPort, tunnelCommand } from "./processes.js";
import { Sessions } from "./sessions.js";
import { Projects } from "./projects.js";
import { Terminals } from "./terminals.js";
import { MobileTasks } from "./mobile-tasks.js";
import { Connectors } from "./connectors.js";
import { Groups } from "./groups.js";
import { registerProviderRoutes, subscriptions } from "./providers.js";
import { registerMarketplaceApi, registerMarketplaceCallback } from "./marketplace-routes.js";
import { Bots } from "./bots.js";
import { BrowserBridge } from "./browser-bridge.js";
import {Previews} from './previews.js';
import {Webhooks} from './webhooks.js';
import {BackupModel} from './backup-model.js';
import {Memory,registerMemoryRoutes} from "./memory.js";
import {Tracking,registerTrackingRoutes} from "./tracking.js";
import { testConnection } from "./models.js";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
const here = dirname(fileURLToPath(import.meta.url));
export async function createApp(options: {
  stateDir: string;
  workspace: string;
  port?: number;
  webDir?: string;
  mobileBridgeBase?: string;
  browserBridgeBase?: string;
}) {
  const port = options.port || 8787;
  const store = new Store(options.stateDir);
  await store.init();
  const token = await store.token();
  const backup=new BackupModel(store);await backup.init();
  let settings = validateSettings(
    await store.read<Settings>("settings", defaults),
    defaults,
  );
  const workspace = new Workspace(options.workspace);
  await workspace.init();
  const projects = new Projects(store, workspace);
  await projects.init();
  const clients = new Set<Response>();
  const connectors = new Connectors(store, `http://127.0.0.1:${port}/api/oauth/mcp/callback`); await connectors.init();
  const redact = (value: unknown) => {
    const json = JSON.stringify(value);
    return backup.redact(connectors.redact(settings.apiKey
      ? json.split(settings.apiKey).join("[redacted]")
      : json));
  };
  const publish = (e: unknown) => {
    const data = `data: ${redact(e)}\n\n`;
    for (const client of clients) client.write(data);
  };
  const processes = new Processes(publish);
  const terminals = new Terminals(publish);
  const sessions = new Sessions(
    store,
    workspace,
    processes,
    () => backup.current(settings),
    publish,
    projects,
  );
  await sessions.init();
  const mobile = new MobileTasks(store, () => backup.current(settings), token, publish, options.mobileBridgeBase);
  await mobile.init();
  const previews=new Previews(store);await previews.init();
  const browserBridge = new BrowserBridge(token, options.browserBridgeBase);
  const bots = new Bots(store, projects, sessions, mobile, connectors, publish, text => backup.redact(connectors.redact(settings.apiKey ? text.split(settings.apiKey).join("[redacted]") : text)),previews, browserBridge);
  await bots.init();
  const memory=new Memory(store);await memory.init();bots.memory=memory;
  const tracking=new Tracking(store,id=>bots.find(id)?.id===id,(id,text)=>bots.alert(id,text,false));await tracking.init();bots.checks=now=>tracking.run(now);
  const webhooks = new Webhooks(store, bots); await webhooks.init();
  const groups = new Groups(store,bots,publish,text=>backup.redact(connectors.redact(settings.apiKey ? text.split(settings.apiKey).join("[redacted]") : text))); await groups.init();
  const app = express();
  app.disable("x-powered-by");
  app.use((req, res, next) => {
    const isWebhook = req.path === "/api/webhooks/bot" || req.path.startsWith("/api/webhooks/bot/");
    const host = req.headers.host;
    if (!isWebhook && host !== `127.0.0.1:${port}` && host !== `localhost:${port}`) {
      res.status(403).json({ error: "Invalid host" });
      return;
    }
    if (
      !isWebhook &&
      req.headers.origin &&
      ![`http://127.0.0.1:${port}`, `http://localhost:${port}`].includes(
        req.headers.origin,
      )
    ) {
      res.status(403).json({ error: "Invalid origin" });
      return;
    }
    res.set({
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
      "Content-Security-Policy":
        "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
    });
    next();
  });
  app.use(express.json({ limit: "2mb" }));
  registerMarketplaceCallback(app,connectors,port);
  app.use("/api", (req, res, next) => {
    if (req.path === "/webhooks/bot" || req.path.startsWith("/webhooks/bot/")) {
      return next();
    }
    const candidate = req.headers.authorization?.replace(/^Bearer /, "") || "";
    const digest = (x: string) => createHash("sha256").update(x).digest();
    if (!timingSafeEqual(digest(candidate), digest(token))) {
      res.status(401).json({ error: "Pairing token required" });
      return;
    }
    res.set("Cache-Control", "no-store");
    next();
  });
  app.get("/api/state", (_req, res) => {
    const view = backup.view(settings);
    // Your own key runs the model of its own settings, not the model of the provider that was
    // chosen before it.
    const byok = (view.executionProvider ?? "byok") === "byok";
    res.json({
      workspace: workspace.root,
      settings: view,
      // The names the provider and the model are shown by, so a menu can name them without waiting.
      selection: subscriptions.describe(view.executionProvider ?? "", byok ? (view.model ?? "") : (view.providerModel ?? "")),
      sessions: sessions.list(),
      jobs: [...processes.jobs.values()],
      projects: projects.list(),
      mobileTasks: mobile.list(),
      groups: groups.list(), bots: bots.list(), botEngineEnabled: bots.enabled, connectors: connectors.list(),
      webhooks: webhooks.getConfig(port),
    });
  });
  app.get("/api/events", (req, res) => {
    if (clients.size >= 8) {
      res.status(429).json({ error: "Too many connections" });
      return;
    }
    res.set({
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-store",
      Connection: "keep-alive",
    });
    res.flushHeaders();
    clients.add(res);
    res.write(`data: ${JSON.stringify({ type: "connected" })}\n\n`);
    const timer = setInterval(() => res.write(": heartbeat\n\n"), 15000);
    req.on("close", () => {
      clearInterval(timer);
      clients.delete(res);
    });
  });
  app.put("/api/settings", async (req, res) => {
    if (sessions.anyBusy() || mobile.anyBusy()) {
      res
        .status(409)
        .json({ error: "Stop the agent before changing provider" });
      return;
    }
    const next = validateSettings(req.body, settings);
    await store.write("settings", next);
    settings = next;
    res.json(backup.view(settings));
  });
  // The owner settings form reads this explicitly; shared state remains redacted.
  app.get("/api/settings/key", (_req,res)=>{res.setHeader("Cache-Control","no-store");res.json({apiKey:settings.apiKey});});
  app.post('/api/settings/backup',async(req,res)=>{if(sessions.anyBusy()||mobile.anyBusy())throw Error('Wait for the active task before changing models');await backup.configure(req.body);res.json(backup.view(settings));});
  app.put('/api/settings/backup',async(req,res)=>{if(sessions.anyBusy()||mobile.anyBusy())throw Error('Wait for the active task before changing models');await backup.toggle(req.body.enabled);res.json(backup.view(settings));});
  app.post("/api/settings/test", async (req, res) =>
    res.json(await testConnection(validateSettings(req.body, settings))),
  );
  app.get("/api/projects", (_req, res) => res.json(projects.list()));
  registerProviderRoutes(app,{port,root:workspace.root,processes,terminals,token});
  registerMarketplaceApi(app,connectors,()=>sessions.anyBusy()||mobile.anyBusy());
  registerMemoryRoutes(app,memory);registerTrackingRoutes(app,tracking);
  app.get("/api/groups",(_req,res)=>res.type("json").send(redact(groups.list())));
  app.post("/api/groups",async(req,res)=>res.status(201).json(await groups.create(req.body.name,req.body.botIds)));
  app.get("/api/groups/:id",(req,res)=>res.type("json").send(redact(groups.state(req.params.id))));
  app.put("/api/groups/:id",async(req,res)=>res.json(await groups.update(req.params.id,req.body.name,req.body.botIds)));
  app.delete("/api/groups/:id",async(req,res)=>{await groups.remove(req.params.id);res.json({ok:true});});
  app.post("/api/groups/:id/messages",async(req,res)=>res.status(202).json(await groups.send(req.params.id,req.body.text,req.body.botIds)));
  app.post("/api/groups/:id/jobs/:job/cancel",async(req,res)=>{await groups.cancel(req.params.id,req.params.job);res.json({ok:true});});
  app.get("/api/bots", (_req, res) => res.type("json").send(redact({ enabled: bots.enabled, bots: bots.list() })));
  app.put("/api/bots/engine", async (req, res) => { await bots.engine(req.body.enabled); res.json({ enabled: bots.enabled }); });
  app.get("/api/bots/phone", (_req, res) => res.json(bots.phoneRequest()));
  app.get('/api/previews/pending',(_req,res)=>res.json({action:previews.pending()}));
  app.post('/api/previews/:id/ack',async(req,res)=>res.json(await previews.acknowledge(req.params.id,req.body.opened===true,req.body.error)));
  app.get("/api/bots/:id/schedules", (req,res)=>res.type("json").send(redact(bots.listSchedules(req.params.id))));
  app.post("/api/bots/:id/schedules", async(req,res)=>res.status(201).type("json").send(redact(await bots.schedule(req.params.id,req.body))));
  app.delete("/api/bots/:id/schedules/:schedule", async(req,res)=>res.type("json").send(redact(await bots.cancelSchedule(req.params.id,req.params.schedule))));
  app.get("/api/bots/alerts", (_req, res) => res.type("json").send(redact(bots.pendingAlerts())));
  app.post("/api/bots/alerts/:id/ack", async (req, res) => { await bots.acknowledgeAlert(req.params.id); res.json({ ok: true }); });
  app.post("/api/bots/:id/alerts", async (req, res) => res.status(201).type("json").send(redact(await bots.alert(req.params.id, req.body.text, req.body.urgent === true))));
  app.get("/api/webhooks/config", (_req, res) => res.json(webhooks.getConfig(port)));
  app.put("/api/webhooks/config", async (req, res) => res.json(await webhooks.updateConfig(req.body)));
  app.post("/api/webhooks/config/regenerate", async (_req, res) => res.json(await webhooks.regenerateSecret()));
  const extractWebhookSecret = (req: express.Request) => {
    const auth = req.headers.authorization?.replace(/^Bearer /, "") || "";
    const custom = (req.headers["x-webhook-secret"] as string) || "";
    const query = typeof req.query.secret === "string" ? req.query.secret : "";
    const bodySecret = typeof req.body?.secret === "string" ? req.body.secret : "";
    return auth || custom || query || bodySecret || undefined;
  };
  const isLoopbackReq = (req: express.Request) => {
    const ip = req.socket?.remoteAddress || req.ip || "";
    return ip === "127.0.0.1" || ip === "::1" || ip === "::ffff:127.0.0.1";
  };
  app.get("/api/webhooks/bot", (req, res) => {
    if (!webhooks.isAuthorized(extractWebhookSecret(req), isLoopbackReq(req))) {
      const cfg = webhooks.getConfig(port);
      if (!cfg.enabled) return res.status(403).json({ error: "External webhook mode is disabled" });
      return res.status(401).json({ error: "Invalid webhook secret" });
    }
    res.json({
      ok: true,
      enabled: webhooks.getConfig(port).enabled,
      bots: bots.list().map(b => ({ id: b.id, name: b.name, allowUrgent: b.allowUrgent })),
    });
  });
  const handleWebhookRoute = async (req: express.Request, res: express.Response) => {
    const isLoopback = isLoopbackReq(req);
    const secret = extractWebhookSecret(req);
    if (!webhooks.isAuthorized(secret, isLoopback)) {
      const cfg = webhooks.getConfig(port);
      if (!cfg.enabled) {
        return res.status(403).json({ error: "External webhook mode is disabled. Enable it in Settings > Connections > External Webhooks." });
      }
      return res.status(401).json({ error: "Unauthorized: Invalid webhook secret. Pass it via X-Webhook-Secret header, Authorization: Bearer, or query ?secret=" });
    }
    const targetBot = req.params.bot || req.body?.bot || (typeof req.query.bot === "string" ? req.query.bot : "");
    if (!targetBot) {
      return res.status(400).json({ error: "Specify a bot by name or id in URL /api/webhooks/bot/:nameOrId or body field 'bot'" });
    }
    const payload = {
      ...req.body,
      ...(req.query.call === "true" ? { call: true } : {}),
      ...(req.query.urgent === "true" ? { urgent: true } : {}),
      ...(typeof req.query.message === "string" ? { message: req.query.message } : {}),
      ...(typeof req.query.text === "string" ? { text: req.query.text } : {}),
      ...(typeof req.query.action === "string" ? { action: req.query.action as any } : {}),
    };
    try {
      const result = await webhooks.handleWebhook(targetBot, payload);
      res.status(200).json(result);
    } catch (err: any) {
      res.status(400).json({ error: err.message || "Webhook handling failed" });
    }
  };
  app.post("/api/webhooks/bot", handleWebhookRoute);
  app.post("/api/webhooks/bot/:bot", handleWebhookRoute);
  app.post("/api/bots/phone/ready", (req, res) => { bots.armPhone(req.body.taskId); res.json({ ok: true }); });
  app.post("/api/bots", async (req, res) => res.status(201).json(await bots.create(req.body)));
  app.get("/api/bots/:id", (req, res) => res.type("json").send(redact(bots.state(req.params.id))));
  app.put("/api/bots/:id", async (req, res) => res.json(await bots.update(req.params.id, req.body)));
  app.delete("/api/bots/:id", async (req, res) => { await bots.remove(req.params.id); res.json({ ok: true }); });
  app.post("/api/bots/:id/read", async (req,res)=>res.json(await bots.markRead(req.params.id,req.body.messageId)));
  app.post("/api/bots/:id/messages", async (req, res) => res.status(202).json(await bots.sendOwnerMessage(req.params.id, req.body.text)));
  app.delete("/api/bots/:id/messages", async (req, res) => { await bots.clearMessages(req.params.id); res.json({ ok: true }); });
  app.post("/api/bots/:id/pause", async (req, res) => { await bots.pause(req.params.id); res.json({ ok: true }); });
  app.post("/api/bots/:id/resume", async (req, res) => { await bots.resume(req.params.id); res.json({ ok: true }); });
  app.post("/api/bots/:id/routine/run", async (req, res) => res.status(202).json(await bots.runRoutine(req.params.id)));
  app.post("/api/bots/:id/jobs/:job/cancel", async (req, res) => { await bots.cancel(req.params.id, req.params.job); res.json({ ok: true }); });
  app.get("/api/connectors", (_req, res) => res.json(connectors.list()));
  app.post("/api/connectors", async (req, res) => { if (sessions.anyBusy() || mobile.anyBusy()) throw Error("Pause agents before changing connections"); res.status(201).json(await connectors.save(req.body)); });
  app.post("/api/connectors/:id/test", async (req, res) => res.json(await connectors.inspect(req.params.id)));
  app.delete("/api/connectors/:id", async (req, res) => { if (sessions.anyBusy() || mobile.anyBusy()) throw Error("Pause agents before changing connections"); await connectors.remove(req.params.id); res.json({ ok: true }); });
  app.get("/api/mobile/health", async (_req, res) => res.json(await mobile.health()));
  app.get("/api/mobile/tasks", (_req, res) => res.type("json").send(redact(mobile.list())));
  app.post("/api/mobile/tasks", async (req, res) => res.status(201).json(await mobile.create(req.body.prompt)));
  app.get("/api/mobile/tasks/:id", (req, res) => { const { history, ...task } = mobile.state(req.params.id); res.type("json").send(redact(task)); });
  app.post("/api/mobile/tasks/:id/resume", async (req, res) => res.status(202).json(await mobile.start(req.params.id)));
  app.post("/api/mobile/tasks/:id/pause", async (req, res) => { await mobile.interrupt(req.params.id); res.json({ ok: true }); });
  app.post("/api/mobile/tasks/:id/stop", async (req, res) => { await mobile.interrupt(req.params.id, true); res.json({ ok: true }); });
  app.post("/api/mobile/tasks/:id/answer", async (req, res) => { await mobile.answer(req.params.id, req.body.questionId, req.body.text); res.json({ ok: true }); });
  app.post("/api/mobile/tasks/:id/steer", async (req,res)=>{if(typeof req.body.text!=="string"||!await mobile.steer(req.params.id,req.body.text))throw Error("This Mobile Use task cannot accept live updates. Send the update to its bot chat to queue it.");res.json({delivery:"steered"});});
  app.delete("/api/mobile/tasks/:id", async (req, res) => { await mobile.remove(req.params.id); res.json({ ok: true }); });
  app.post("/api/projects", async (req, res) =>
    res.status(201).json(await projects.create(req.body.name)),
  );
  app.get("/api/git/identity", async (_req, res) => {
    const read = async (key: string) => {
      try { return (await promisify(execFile)("git", ["config", "--global", "--get", key])).stdout.trim(); }
      catch (e: any) { if (e.code === 1) return ""; throw e; }
    };
    res.json({ name: await read("user.name"), email: await read("user.email") });
  });
  app.put("/api/git/identity", async (req, res) => {
    for (const key of ["name", "email"]) {
      const value = req.body[key];
      if (typeof value !== "string" || !value.trim() || value.length > 200 || /[\r\n\0]/.test(value))
        throw Error("Enter a valid Git name and email");
    }
    for (const key of ["name", "email"])
      await promisify(execFile)("git", ["config", "--global", "user." + key, req.body[key].trim()]);
    res.json({ ok: true });
  });
  app.get("/api/files", async (req, res) =>
    res.json(await (await projects.workspace(req.query.projectId as string)).list(String(req.query.path || "."))),
  );
  app.get("/api/file", async (req, res) =>
    res.json({ content: await (await projects.workspace(req.query.projectId as string)).read(String(req.query.path || "")) }),
  );
  app.put("/api/file", async (req, res) => {
    await (await projects.workspace(req.body.projectId)).write(req.body.path, req.body.content);
    res.json({ ok: true });
  });
  app.post("/api/sessions", async (req, res) =>
    res.status(201).json(await sessions.create(req.body.projectId)),
  );
  app.get("/api/sessions/:id", (req, res) =>
    res.type("json").send(redact(sessions.state(req.params.id))),
  );
  app.delete("/api/sessions/:id", async (req, res) => {
    await sessions.remove(req.params.id);
    res.json({ ok: true });
  });
  app.post("/api/sessions/:id/chat", async (req, res) => {
    await sessions.prompt(req.params.id, req.body.text);
    res.status(202).json({ ok: true });
  });
  app.post("/api/sessions/:id/stop", (req, res) => {
    sessions.stop(req.params.id);
    res.json({ ok: true });
  });
  app.put("/api/sessions/:id/trust", (req, res) => {
    sessions.setTrust(req.params.id, req.body.trust === true);
    res.json({ ok: true });
  });
  app.post("/api/approvals/:id", (req, res) => {
    sessions.approve(req.params.id, req.body.allow === true);
    res.json({ ok: true });
  });
  app.post("/api/commands", async (req, res) =>
    res
      .status(201)
      .json(
        processes.start(
          String(req.body.command || ""),
          await (await projects.workspace(req.body.projectId)).path(req.body.cwd),
        ),
      ),
  );
  app.post("/api/terminals", async (req, res) => res.status(201).json(terminals.start(
    req.body.root === true ? workspace.root : await (await projects.workspace(req.body.projectId)).path(req.body.cwd),
    req.body.cols, req.body.rows,
  )));
  app.get("/api/terminals/:id", (req, res) => res.json(terminals.state(req.params.id)));
  app.post("/api/terminals/:id/input", (req, res) => {
    terminals.input(req.params.id, req.body.data, req.body.cols, req.body.rows);
    res.json({ ok: true });
  });
  app.delete("/api/terminals/:id", (req, res) => {
    terminals.stop(req.params.id);
    res.json({ ok: true });
  });
  app.post("/api/servers", async (req, res) =>
    res
      .status(201)
      .json(
        processes.start(
          String(req.body.command || ""),
          await (await projects.workspace(req.body.projectId)).path(req.body.cwd),
          "server",
          validPort(req.body.port),
        ),
      ),
  );
  app.post("/api/tunnels/login", (_req, res) =>
    res
      .status(201)
      .json(
        processes.start(
          "devtunnel user login -g -d",
          workspace.root,
          "command",
          undefined,
          "Microsoft Dev Tunnels login",
        ),
      ),
  );
  app.post("/api/tunnels", (req, res) => {
    const p = validPort(req.body.port);
    if (p === port) throw Error("Do not tunnel the agent control port");
    res
      .status(201)
      .json(
        processes.start(
          tunnelCommand(p, req.body.anonymous === true),
          workspace.root,
          "tunnel",
          p,
          "Dev Tunnel :" + p,
        ),
      );
  });
  app.post("/api/processes/:id/stop", (req, res) => {
    processes.stop(req.params.id);
    res.json({ ok: true });
  });
  app.use(
    express.static(
      options.webDir ||
        resolve(here, here.endsWith("/dist") ? "../web" : "../../web"),
    ),
  );
  app.use(
    (
      error: any,
      _req: express.Request,
      res: Response,
      _next: express.NextFunction,
    ) => {
      if (res.headersSent) return;
      const message = String(error.message || "Request failed");
      res
        .status(error.status || 400)
        .type("json")
        .send(redact({ error: message }));
    },
  );
  return {
    app,
    token,
    sessions,
    processes,
    mobile,
    bots, groups, connectors, webhooks,
    close: async () => {
      await groups.close(); await bots.close(); await connectors.close();
      sessions.stopAll();
      processes.stopAll();
      terminals.stopAll();
      mobile.stopAll();
      for (const c of clients) c.end();
    },
  };
}
if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const port = Number(process.env.POCKET_PORT || 8787);
  if (!Number.isInteger(port) || port < 1024 || port > 65535)
    throw Error("Invalid POCKET_PORT");
  const stateDir =
    process.env.POCKET_STATE || join(homedir(), ".local/share/pocket-agent");
  const runtime = await createApp({
    stateDir,
    workspace:
      process.env.POCKET_WORKSPACE || join(homedir(), "pocket-workspaces"),
    port,
  });
  const server = runtime.app.listen(port, "127.0.0.1", () => {
    console.log(`Pocket Agent listening at http://127.0.0.1:${port}`);
    console.log(
      `Pair in the Android app using the token printed by scripts/pair.sh. State: ${stateDir}`,
    );
  });
  const stop = () => {
    runtime.close();
    server.close(() => process.exit());
    setTimeout(() => process.exit(), 3000).unref();
  };
  process.once("SIGINT", stop);
  process.once("SIGTERM", stop);
}
