import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import { Store } from "../src/storage.js";
import { Bots } from "../src/bots.js";
import { Groups } from "../src/groups.js";

async function fixture() {
  const root = await mkdtemp(join(tmpdir(), "pocko-groups-"));
  const store = new Store(root); await store.init();
  const sessions = new Map<string, any>(); const profiles = new Map<string, any>();
  let onPrompt: ((id: string, text: string, profile: any) => Promise<void>) | undefined;
  const adapter: any = {
    progress: () => undefined,
    create: async () => { const id = randomUUID(); sessions.set(id, { busy: false, approvals: [], messages: [] }); return { id }; },
    state: (id: string) => sessions.get(id), stop: () => {}, setTrust: () => {},
    prompt: async (id: string, text: string, profile: any) => { profiles.set(id, profile); if (onPrompt) await onPrompt(id, text, profile); sessions.set(id, { busy: false, approvals: [], messages: [{ role: "assistant", content: [{ type: "text", text: "Verified reply for " + id }] }] }); },
  };
  const bots = new Bots(store, { create: async () => ({ id: randomUUID() }) } as any, adapter, {} as any, { list:()=>[], require: () => {}, tools: async () => [] } as any, () => {}, text => text.replaceAll("private-token", "[redacted]"));
  await bots.init(); await bots.engine(false);
  const groups = new Groups(store, bots, () => {}, text => text.replaceAll("private-token", "[redacted]")); await groups.init();
  return { store, bots, groups, profiles, adapter, setPrompt: (handler: typeof onPrompt) => { onPrompt = handler; }, close: async () => { await groups.close(); await bots.close(); await rm(root, { recursive: true, force: true }); } };
}

test("group replies retain speakers and survive reload without automatically echoing", async () => {
  const f = await fixture();
  try {
    const alpha = await f.bots.create({ name: "Alpha", avatar: "A", accent: "#44AAFF", personality: "Concise", role: "Research" });
    const beta = await f.bots.create({ name: "Beta", role: "Review" });
    const group = await f.groups.create("Project room", [alpha.id, beta.id]);
    await assert.rejects(f.groups.send(group.id, "No", ["outside"]), /group members/);
    const queued = await f.groups.send(group.id, "Check private-token"); assert.equal(queued.jobIds.length, 2);
    assert.equal(f.groups.state(group.id).messages.length, 1); assert.ok(!f.groups.state(group.id).messages[0].text.includes("private-token"));
    await assert.rejects(f.groups.update(group.id, "Changed", [alpha.id]), /unfinished/);
    await f.bots.engine(true); await f.bots.tick(); await f.bots.tick(); await f.groups.reconcile();
    const replies = f.groups.state(group.id).messages.filter(message => message.role === "bot");
    assert.deepEqual(replies.map(message => message.botName).sort(), ["Alpha", "Beta"]);
    assert.equal(f.bots.jobsForGroup(group.id).length, 2);
    for (let i = 0; i < 6; i++) await f.groups.reconcile();
    assert.equal(f.groups.state(group.id).messages.length, 3);
    assert.match(f.profiles.get(alpha.sessionId).instructions, /Personality and response style: Concise/);
    await f.groups.close(); const reopened = new Groups(f.store, f.bots, () => {}); await reopened.init();
    try { assert.equal(reopened.state(group.id).messages.length, 3); await reopened.reconcile(); assert.equal(reopened.state(group.id).messages.length, 3); }
    finally { await reopened.close(); }
    await f.groups.update(group.id, "Renamed", [alpha.id]); assert.equal(f.groups.state(group.id).group.name, "Renamed");
  } finally { await f.close(); }
});

test("group delegation stays within membership and exposes the teammate result", async () => {
  const f = await fixture();
  try {
    const alpha = await f.bots.create({ name: "Alpha", role: "Delegate" }); const beta = await f.bots.create({ name: "Beta", role: "Review" }); const outsider = await f.bots.create({ name: "Outside", role: "Private" });
    const group = await f.groups.create("Team", [alpha.id, beta.id]); let delegated = "";
    f.setPrompt(async (id, _text, profile) => {
      if (id !== alpha.sessionId) return;
      const send = profile.tools.find((tool: any) => tool.name === "message_bot");
      await assert.rejects(send.execute("outside", { bot_id: outsider.id, task: "Do not send" }), /members/);
      const result = await send.execute("teammate", { bot_id: beta.id, task: "Review this result" }); delegated = JSON.parse(result.content[0].text).jobId;
    });
    await f.groups.send(group.id, "Investigate", [alpha.id]); await f.bots.engine(true); await f.bots.tick(); await f.bots.tick(); await f.groups.reconcile();
    assert.ok(delegated); const job = f.bots.getJob(delegated)!; assert.equal(job.groupId, group.id); assert.equal(job.sourceBotId, alpha.id); assert.equal(job.depth, 1);
    const query = f.profiles.get(alpha.sessionId).tools.find((tool: any) => tool.name === "bot_job_result");
    assert.equal(JSON.parse((await query.execute("result", { job_id: delegated })).content[0].text).status, "completed");
    assert.equal(f.bots.state(alpha.id).messages.some(message => message.role === "system" && message.text.includes("Beta replied")), true);
    assert.equal(f.groups.state(group.id).messages.filter(message => message.role === "bot").length, 2);
    assert.equal(f.bots.state(outsider.id).jobs.length, 0);
  } finally { await f.close(); }
});

test("group cancel and deletion stop queued work while keeping bot workspaces", async () => {
  const f = await fixture();
  try {
    const alpha = await f.bots.create({ name: "Alpha", role: "Help" }); const group = await f.groups.create("Team", [alpha.id]);
    const first = await f.groups.send(group.id, "Queued"); await f.groups.cancel(group.id, first.jobIds[0]);
    assert.equal(f.bots.getJob(first.jobIds[0])?.status, "cancelled");
    assert.equal(f.groups.state(group.id).messages.at(-1)?.status, "cancelled");
    const next = await f.groups.send(group.id, "Another queued task"); await f.groups.remove(group.id);
    assert.equal(f.bots.getJob(next.jobIds[0])?.status, "cancelled"); assert.equal(f.bots.get(alpha.id).projectId, alpha.projectId); assert.equal(f.groups.list().length, 0);
    await assert.rejects(f.groups.send(group.id, "Missing"), /not found/);
  } finally { await f.close(); }
});
