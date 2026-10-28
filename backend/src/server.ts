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
import { Bots } from "./bots.js";
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
}) {
  const port = options.port || 8787;
  const store = new Store(options.stateDir);
  await store.init();
  const token = await store.token();
  let settings = validateSettings(
    await store.read<Settings>("settings", defaults),
    defaults,
  );
  const workspace = new Workspace(options.workspace);
  await workspace.init();
  const projects = new Projects(store, workspace);
  await projects.init();
  const clients = new Set<Response>();
  const connectors = new Connectors(store); await connectors.init();
  const redact = (value: unknown) => {
    const json = JSON.stringify(value);
    return connectors.redact(settings.apiKey
      ? json.split(settings.apiKey).join("[redacted]")
      : json);
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
    () => settings,
    publish,
    projects,
  );
  await sessions.init();
  const mobile = new MobileTasks(store, () => settings, token, publish, options.mobileBridgeBase);
  await mobile.init();
  const bots = new Bots(store, projects, sessions, mobile, connectors, publish, text => connectors.redact(settings.apiKey ? text.split(settings.apiKey).join("[redacted]") : text));
  await bots.init();
  const app = express();
  app.disable("x-powered-by");
  app.use((req, res, next) => {
    const host = req.headers.host;
    if (host !== `127.0.0.1:${port}` && host !== `localhost:${port}`) {
      res.status(403).json({ error: "Invalid host" });
      return;
    }
    if (
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
  app.use("/api", (req, res, next) => {
    const candidate = req.headers.authorization?.replace(/^Bearer /, "") || "";
    const digest = (x: string) => createHash("sha256").update(x).digest();
    if (!timingSafeEqual(digest(candidate), digest(token))) {
      res.status(401).json({ error: "Pairing token required" });
      return;
    }
    res.set("Cache-Control", "no-store");
    next();
  });
  app.get("/api/state", (_req, res) =>
    res.json({
      workspace: workspace.root,
      settings: publicSettings(settings),
      sessions: sessions.list(),
      jobs: [...processes.jobs.values()],
      projects: projects.list(),
      mobileTasks: mobile.list(),
      bots: bots.list(), botEngineEnabled: bots.enabled, connectors: connectors.list(),
    }),
  );
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
    res.json(publicSettings(settings));
  });
  app.post("/api/settings/test", async (req, res) =>
    res.json(await testConnection(validateSettings(req.body, settings))),
  );
  app.get("/api/projects", (_req, res) => res.json(projects.list()));
  app.get("/api/bots", (_req, res) => res.type("json").send(redact({ enabled: bots.enabled, bots: bots.list() })));
  app.put("/api/bots/engine", async (req, res) => { await bots.engine(req.body.enabled); res.json({ enabled: bots.enabled }); });
  app.get("/api/bots/phone", (_req, res) => res.json(bots.phoneRequest()));
  app.get("/api/bots/alerts", (_req, res) => res.type("json").send(redact(bots.pendingAlerts())));
  app.post("/api/bots/alerts/:id/ack", async (req, res) => { await bots.acknowledgeAlert(req.params.id); res.json({ ok: true }); });
  app.post("/api/bots/:id/alerts", async (req, res) => res.status(201).type("json").send(redact(await bots.alert(req.params.id, req.body.text, req.body.urgent === true))));
  app.post("/api/bots/phone/ready", (req, res) => { bots.armPhone(req.body.taskId); res.json({ ok: true }); });
  app.post("/api/bots", async (req, res) => res.status(201).json(await bots.create(req.body)));
  app.get("/api/bots/:id", (req, res) => res.type("json").send(redact(bots.state(req.params.id))));
  app.put("/api/bots/:id", async (req, res) => res.json(await bots.update(req.params.id, req.body)));
  app.delete("/api/bots/:id", async (req, res) => { await bots.remove(req.params.id); res.json({ ok: true }); });
  app.post("/api/bots/:id/messages", async (req, res) => res.status(202).json(await bots.enqueue(req.params.id, req.body.text)));
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
    await (await projects.workspace(req.body.projectId)).path(req.body.cwd),
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
    bots, connectors,
    close: async () => {
      await bots.close(); await connectors.close();
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
