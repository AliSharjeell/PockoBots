import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, mkdir, rm, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Store, defaults } from "../src/storage.js";
import { Workspace } from "../src/workspace.js";
import { Projects } from "../src/projects.js";
import { Sessions } from "../src/sessions.js";
import { Processes } from "../src/processes.js";
import { Terminals } from "../src/terminals.js";

test("projects persist, adopt existing repos and keep conversation tools inside their project", async () => {
  const temp = await mkdtemp(join(tmpdir(), "pocket-projects-"));
  try {
    const store = new Store(join(temp, "state")); await store.init();
    const root = new Workspace(join(temp, "projects")); await root.init();
    await mkdir(join(root.root, "existing"));
    const projects = new Projects(store, root); await projects.init();
    assert.equal(projects.list()[0].name, "existing");
    const a = await projects.create("first"); const b = await projects.create("second");
    const wa = await projects.workspace(a.id); const wb = await projects.workspace(b.id);
    await wa.write("src/index.js", "first project"); await wb.write("src/index.js", "second project");
    assert.equal(await readFile(join(root.root, "first/src/index.js"), "utf8"), "first project");
    await assert.rejects(wa.read("../second/src/index.js"), /outside workspace/);
    await assert.rejects(projects.create("../escape"));
    await assert.rejects(projects.create("first"), /already exists/);
    const restarted = new Projects(store, root); await restarted.init();
    assert.equal(restarted.get(a.id).directory, "first");
    const processes = new Processes(() => {});
    const sessions = new Sessions(store, root, processes, () => defaults, () => {}, restarted);
    const conversation = await sessions.create(a.id);
    assert.equal(sessions.state(conversation.id).projectId, a.id);
    await assert.rejects(sessions.create("missing"), /not found/);
  } finally { await rm(temp, { recursive: true, force: true }); }
});

test("interactive terminal has a real TTY, accepts input, resizes and interrupts foreground commands", { skip: process.platform === "win32" }, async () => {
  const temp = await mkdtemp(join(tmpdir(), "pocket-pty-"));
  const terminals = new Terminals(() => {});
  const { id } = terminals.start(temp);
  const output = () => Buffer.from(terminals.state(id).output, "base64").toString("utf8");
  const until = async (needle: string) => {
    const end = Date.now() + 10000;
    while (Date.now() < end) {
      if (output().includes(needle)) return;
      await new Promise((r) => setTimeout(r, 30));
    }
    assert.fail("PTY did not produce: " + needle + "\n" + output());
  };
  try {
    terminals.input(id, "test -t 0 && test -t 1 && printf '\\n%s\\n' TTY_WORKS\n", 93, 31);
    await until("\r\nTTY_WORKS\r\n");
    terminals.input(id, "stty size\n"); await until("31 93");
    terminals.input(id, "read -r answer; printf '\\n%s:%s\\n' INPUT \"$answer\"\n");
    terminals.input(id, "from-phone\n"); await until("INPUT:from-phone");
    terminals.input(id, "sleep 60\n");
    await new Promise((r) => setTimeout(r, 150));
    terminals.input(id, "\x03");
    terminals.input(id, "printf '\\n%s\\n' INTERRUPT_WORKS\n"); await until("\r\nINTERRUPT_WORKS\r\n");
    assert.throws(() => terminals.input(id, "", 0, 0), /size/);
  } finally { terminals.stopAll(); await rm(temp, { recursive: true, force: true }); }
});
