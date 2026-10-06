import { test } from "node:test";
import assert from "node:assert/strict";
import {
  mkdtemp,
  symlink,
  readFile,
  writeFile,
  stat,
  rm,
  mkdir,
} from "node:fs/promises";
import http from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Workspace } from "../src/workspace.js";
import {
  Store,
  defaults,
  validateSettings,
  publicSettings,
} from "../src/storage.js";
import { Processes, tunnelCommand, validPort } from "../src/processes.js";

test("file tools create nested files and reject traversal and escaping symlinks", async () => {
  const temp = await mkdtemp(join(tmpdir(), "pocket-files-"));
  try {
    const ws = new Workspace(join(temp, "workspace"));
    await ws.init();
    await ws.write("src/main.js", 'console.log("hello")');
    assert.equal(await ws.read("src/main.js"), 'console.log("hello")');
    assert.equal((await ws.list("src"))[0].name, "main.js");
    await assert.rejects(ws.write("../outside", "bad"), /outside workspace/);
    await symlink(temp, join(ws.root, "escape"));
    await assert.rejects(
      ws.write("escape/new/file", "bad"),
      /outside workspace/,
    );
    await assert.rejects(ws.read("escape/src"), /outside workspace|ENOENT/);
    await assert.rejects(ws.write("big", "x".repeat(1024 * 1024 + 1)), /limit/);
  } finally {
    await rm(temp, { recursive: true, force: true });
  }
});
test("private settings and pairing survive restart without returning the key", async () => {
  const temp = await mkdtemp(join(tmpdir(), "pocket-store-"));
  try {
    const store = new Store(temp);
    await store.init();
    const token = await store.token();
    assert.match(token, /^[a-f0-9]{64}$/);
    assert.equal(await new Store(temp).token(), token);
    assert.equal((await stat(join(temp, "token.json"))).mode & 0o777, 0o600);
    assert.equal((await stat(temp)).mode & 0o777, 0o700);
    const settings = validateSettings(
      { ...defaults, apiKey: "test-secret" },
      defaults,
    );
    assert.equal("apiKey" in publicSettings(settings), false);
    assert.equal(
      validateSettings({ model: "another", apiKey: "" }, settings).apiKey,
      "test-secret",
    );
    assert.equal(
      validateSettings(
        {
          provider: "anthropic",
          api: "anthropic-messages",
          baseUrl: "https://api.anthropic.com",
        },
        settings,
      ).apiKey,
      "",
    );
    assert.equal(validateSettings({ clearKey: true }, settings).apiKey, "");
    assert.throws(
      () => validateSettings({ baseUrl: "http://example.com" }, settings),
      /HTTPS/,
    );
    assert.throws(
      () =>
        validateSettings(
          { baseUrl: "https://user:pass@example.com" },
          settings,
        ),
      /credentials/,
    );
    assert.throws(
      () => validateSettings({ maxTokens: 3000000 }, settings),
      /token limits/,
    );
  } finally {
    await rm(temp, { recursive: true, force: true });
  }
});
test("shell commands, local git clones, and process cancellation work", async () => {
  const temp = await mkdtemp(join(tmpdir(), "pocket-process-"));
  const processes = new Processes(() => {});
  try {
    const git = await processes.run(
      "git init source && git -C source -c user.name=Test -c user.email=test@example.com commit --allow-empty -m initial && git clone ./source cloned",
      temp,
    );
    assert.equal(git.exitCode, 0);
    assert.ok((await stat(join(temp, "cloned/.git"))).isDirectory());
    const result = await processes.run(
      "printf hello; printf stderr >&2; exit 3",
      temp,
    );
    assert.equal(result.exitCode, 3);
    assert.match(result.output, /hello/);
    assert.match(result.output, /stderr/);
    const abort = new AbortController();
    const running = processes.run("sleep 60", temp, abort.signal);
    setTimeout(() => abort.abort(), 80);
    assert.equal((await running).cancelled, true);
    const timeout = await processes.run("sleep 60", temp, undefined, 50);
    assert.equal(timeout.timedOut, true);
    assert.equal(tunnelCommand(3000, false), "devtunnel host -p 3000");
    assert.equal(
      tunnelCommand(3000, true),
      "devtunnel host -p 3000 --allow-anonymous",
    );
    assert.throws(() => validPort("3000; echo bad"), /Port/);
    assert.throws(() => validPort(8787), /control port/);
  } finally {
    processes.stopAll();
    await rm(temp, { recursive: true, force: true });
  }
});

test("a managed npm development server answers localhost HTTP and stops with its children", async () => {
  const temp = await mkdtemp(join(tmpdir(), "pocket-npm-"));
  const processes = new Processes(() => {});
  const reserve = http.createServer();
  await new Promise<void>((r) => reserve.listen(0, "127.0.0.1", r));
  const port = (reserve.address() as any).port;
  await new Promise<void>((r) => reserve.close(() => r()));
  try {
    await writeFile(
      join(temp, "package.json"),
      JSON.stringify({ scripts: { dev: "node server.cjs" } }),
    );
    await writeFile(
      join(temp, "server.cjs"),
      `require('http').createServer((req,res)=>res.end('local npm server')).listen(${port},'127.0.0.1',()=>console.log('ready'));`,
    );
    const job = processes.start("npm run dev", temp, "server", port);
    const deadline = Date.now() + 10000;
    let response = "";
    while (Date.now() < deadline) {
      try {
        response = await (await fetch("http://127.0.0.1:" + port)).text();
        break;
      } catch {}
      await new Promise((r) => setTimeout(r, 30));
    }
    assert.equal(response, "local npm server");
    assert.match(job.output, /ready/);
    processes.stop(job.id);
    while (job.status === "running" && Date.now() < deadline)
      await new Promise((r) => setTimeout(r, 25));
    assert.equal(job.status, "exited");
    await assert.rejects(fetch("http://127.0.0.1:" + port));
  } finally {
    processes.stopAll();
    await rm(temp, { recursive: true, force: true });
  }
});
