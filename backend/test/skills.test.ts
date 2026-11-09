import {test} from "node:test";
import assert from "node:assert/strict";
import {mkdtemp, mkdir, rm, writeFile, symlink, readdir} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {Store} from "../src/storage.js";
import {Skills, parseSkill, safePath, limits} from "../src/skills.js";
import {createApp} from "../src/server.js";

const skillMd = (name: string, extra = "", body = "# Steps\nDo the thing.\n") => `---\nname: ${name}\ndescription: Does ${name} things.\n${extra}---\n${body}`;
async function folder(root: string, name: string, files: Record<string, string>) {
  const dir = join(root, "src-" + name);
  for (const [path, content] of Object.entries(files)) { await mkdir(join(dir, path, ".."), {recursive: true}); await writeFile(join(dir, path), content); }
  return dir;
}
async function setup() {
  const root = await mkdtemp(join(tmpdir(), "pocko-skills-")); const store = new Store(join(root, "state")); await store.init();
  const skills = new Skills(store); await skills.init(); return {root, store, skills};
}

test("frontmatter follows the open SKILL.md format", () => {
  const parsed = parseSkill("---\nname: pdf-tools\ndescription: >\n  Fill and merge\n  PDF forms.\nallowed-tools: [bash, read_file]\nmetadata:\n  author: someone\nverify:\n  - run the sample\n  - \"check output\"\n---\nBody");
  assert.equal(parsed.name, "pdf-tools"); assert.equal(parsed.description, "Fill and merge PDF forms.");
  assert.deepEqual(parsed.requires, ["bash", "read_file"]); assert.deepEqual(parsed.verify, ["run the sample", "check output"]); assert.equal(parsed.body, "Body");
  assert.deepEqual(parseSkill("---\nname: a\ndescription: 'x: y'\nallowed-tools: Bash Read\n---\n").requires, ["Bash", "Read"]);
  assert.throws(() => parseSkill("# no frontmatter"), /frontmatter/);
  assert.throws(() => parseSkill("---\nname: Bad Name\ndescription: x\n---\n"), /name/);
  assert.throws(() => parseSkill("---\nname: ok\n---\n"), /description/);
  for (const bad of ["../x", "a/../b", "/etc/passwd", "a\\b", "C:x", "", "a//b", "./a"]) assert.throws(() => safePath(bad), /Invalid/, bad);
  assert.equal(safePath("scripts/run.py"), "scripts/run.py");
});

test("install copies a folder as inert text and rejects escaping or oversized content", async () => {
  const {root, skills} = await setup();
  try {
    const src = await folder(root, "ok", {"SKILL.md": skillMd("deploy-check", "allowed-tools: bash\n"), "scripts/check.sh": "touch PWNED\n", "references/notes.md": "notes"});
    const skill = await skills.install(src);
    assert.equal(skill.name, "deploy-check"); assert.equal(skill.source, "local:" + src); assert.deepEqual(skill.requires, ["bash"]);
    assert.deepEqual(await skills.files("deploy-check"), ["SKILL.md", "references/notes.md", "scripts/check.sh"]);
    assert.equal((await skills.read("deploy-check", "references/notes.md")).content, "notes");
    await assert.rejects(skills.read("deploy-check", "../../skills.json"), /Invalid/);
    assert.ok(!(await readdir(src)).includes("PWNED"));
    await assert.rejects(skills.install(src), /already installed|is installed/);
    await assert.rejects(skills.install("relative/path"), /absolute/);
    await assert.rejects(skills.install(await folder(root, "nomd", {"README.md": "x"})), /SKILL.md/);
    await assert.rejects(skills.install(await folder(root, "big", {"SKILL.md": skillMd("big"), "blob.txt": "x".repeat(limits.file + 1)})), /exceeds/);
    const linked = await folder(root, "link", {"SKILL.md": skillMd("linked")});
    let canLink = true;
    try { await symlink(join(root, "state", "skills.json"), join(linked, "escape.txt"), "file"); } catch { canLink = false; }
    if (canLink) await assert.rejects(skills.install(linked), /escapes/);
    assert.deepEqual(skills.list().skills.map(s => s.name), ["deploy-check"]);
  } finally { await rm(root, {recursive: true, force: true}); }
});

test("agent edits wait for review, keep history and can be reverted", async () => {
  const {root, store, skills} = await setup();
  try {
    const created = await skills.propose("notes", {"SKILL.md": skillMd("notes", "", "v1\n")}, "bot-1");
    assert.equal(skills.list().skills.length, 0, "pending skills are not usable");
    await assert.rejects(skills.read("notes"), /not found/);
    assert.equal((await skills.review(created.id)).changes.length, 1);
    assert.equal((await skills.accept(created.id)).version, 1);
    assert.equal(skills.get("notes").source, "agent:bot-1");

    const edit = await skills.propose("notes", {"SKILL.md": skillMd("notes", "", "v2\n"), "extra.md": "more"}, "bot-1", "add extra");
    const stale = await skills.propose("notes", {"extra.md": "other"}, "bot-2");
    const review = await skills.review(edit.id); assert.deepEqual(review.changes.map((c: any) => c.path), ["SKILL.md", "extra.md"]);
    assert.match(review.changes[0]!.diff, /-v1[\s\S]*\+v2/);
    await skills.accept(edit.id);
    await assert.rejects(skills.accept(stale.id), /changed after/);
    await skills.reject(stale.id); assert.equal(skills.list().pending.length, 0);
    assert.match((await skills.read("notes")).content, /v2/);

    await assert.rejects(skills.propose("notes", {"../evil": "x"}, "bot-1"), /Invalid/);
    await assert.rejects(skills.propose("notes", {"SKILL.md": skillMd("other")}, "bot-1"), /match/);
    await assert.rejects(skills.propose("fresh", {"x.md": "no skill file"}, "bot-1"), /frontmatter/);
    assert.equal(skills.list().pending.length, 0, "failed proposals leave nothing behind");

    const reverted = await skills.revert("notes"); assert.equal(reverted.version, 3);
    assert.match((await skills.read("notes")).content, /v1/); assert.deepEqual(await skills.files("notes"), ["SKILL.md"]);
    await skills.revert("notes", 2); assert.match((await skills.read("notes")).content, /v2/);

    const restarted = new Skills(store); await restarted.init(); assert.equal(restarted.get("notes").version, 4);
    await skills.remove("notes"); assert.equal(skills.list().skills.length, 0); assert.equal(skills.list().history.notes, undefined);
  } finally { await rm(root, {recursive: true, force: true}); }
});

test("the prompt index is compact, capped, and never grants missing tools", async () => {
  const {root, skills} = await setup();
  try {
    assert.equal(skills.prompt([]), "");
    for (let i = 0; i < 40; i++) await skills.install(await folder(root, "s" + i, {"SKILL.md": `---\nname: skill-${i}\ndescription: ${"word ".repeat(60)}\n${i === 0 ? "allowed-tools: use_phone bash\n" : ""}---\nSECRET BODY`}));
    const index = skills.prompt(["bash"]);
    assert.ok(index.length <= limits.prompt + 80, String(index.length)); assert.match(index, /more; use_skill action list/);
    assert.match(index, /skill-0: .*\(needs unavailable: use_phone\)/); assert.doesNotMatch(index, /SECRET BODY/);
    const tool = skills.tool("bot-1");
    const view = JSON.parse((await tool.execute("t", {action: "view", name: "skill-0"} as any)).content[0].text as string);
    assert.match(view.content, /SECRET BODY/); assert.deepEqual(view.requires, ["use_phone", "bash"]);
    const proposed = JSON.parse((await tool.execute("t", {action: "propose", name: "new-one", files: {"SKILL.md": skillMd("new-one")}} as any)).content[0].text as string);
    assert.equal(proposed.pending.author, "bot-1"); assert.throws(() => skills.get("new-one"));
  } finally { await rm(root, {recursive: true, force: true}); }
});

test("skill routes require the pairing token and bots see the index and loader", async () => {
  const directory = await mkdtemp(join(tmpdir(), "pocko-skill-api-"));
  const runtime = await createApp({stateDir: join(directory, "state"), workspace: join(directory, "workspace"), port: 19131});
  const server = runtime.app.listen(19131, "127.0.0.1"); await new Promise<void>(r => server.on("listening", r));
  const headers = {Authorization: "Bearer " + runtime.token, "Content-Type": "application/json"};
  const call = async (method: string, path: string, body?: unknown) => { const r = await fetch("http://127.0.0.1:19131/api" + path, {method, headers, body: body === undefined ? undefined : JSON.stringify(body)}); return {status: r.status, body: await r.json() as any}; };
  try {
    assert.equal((await fetch("http://127.0.0.1:19131/api/skills")).status, 401);
    const src = await folder(directory, "api", {"SKILL.md": skillMd("api-skill"), "ref.md": "ref"});
    assert.equal((await call("POST", "/skills", {path: 42})).status, 400);
    assert.equal((await call("POST", "/skills", {path: src})).status, 201);
    const read = await call("GET", "/skills/api-skill?path=ref.md"); assert.equal(read.body.content, "ref"); assert.deepEqual(read.body.files, ["SKILL.md", "ref.md"]);
    assert.equal((await call("GET", "/skills/api-skill?path=..%2Fx")).status, 400);
    assert.equal((await call("GET", "/skills/missing")).status, 400);

    const bot = await runtime.bots.create({name: "Skilled"});
    const profile = await (runtime.bots as any).profile(bot, {id: "j", depth: 0});
    assert.match(profile.instructions, /- api-skill: Does api-skill things\./); assert.ok(profile.tools.some((t: any) => t.name === "use_skill"));
    const pending = JSON.parse((await profile.tools.find((t: any) => t.name === "use_skill").execute("t", {action: "propose", name: "api-skill", files: {"ref.md": "ref v2"}})).content[0].text).pending;
    assert.equal((await call("GET", "/skills")).body.pending.length, 1);
    assert.equal((await call("GET", "/skill-changes/" + pending.id)).body.changes[0].path, "ref.md");
    assert.equal((await call("POST", "/skill-changes/" + pending.id + "/accept")).body.version, 2);
    assert.equal((await call("GET", "/skills/api-skill?path=ref.md")).body.content, "ref v2");
    assert.equal((await call("POST", "/skills/api-skill/revert", {})).body.version, 3);
    assert.equal((await call("GET", "/skills/api-skill?path=ref.md")).body.content, "ref");
    const rejected = JSON.parse((await profile.tools.find((t: any) => t.name === "use_skill").execute("t", {action: "propose", name: "api-skill", files: {"ref.md": "nope"}})).content[0].text).pending;
    assert.equal((await call("POST", "/skill-changes/" + rejected.id + "/reject")).status, 200);
    assert.equal((await call("DELETE", "/skills/api-skill")).status, 200);
    assert.deepEqual((await call("GET", "/skills")).body.skills, []);
  } finally { await runtime.close(); await new Promise<void>(r => server.close(() => r())); await rm(directory, {recursive: true, force: true}); }
});
