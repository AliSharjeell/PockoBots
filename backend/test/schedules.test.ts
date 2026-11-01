import {test} from "node:test";
import assert from "node:assert/strict";
import {mkdtemp,rm,readFile} from "node:fs/promises";
import {join} from "node:path";
import {tmpdir} from "node:os";
import {makeSchedule,nextDaily} from "../src/schedules.js";
import {Bots} from "../src/bots.js";
import {Store} from "../src/storage.js";

test("relative and dated reminders validate their timing",()=>{
  const now=Date.parse("2026-11-01T10:00:00Z");
  assert.equal(makeSchedule("bot",{text:"Call me",delay_seconds:600},now).nextRun,now+600000);
  assert.equal(makeSchedule("bot",{text:"Call me",at:"2026-11-01T17:00:00+05:00"},now).nextRun,Date.parse("2026-11-01T12:00Z"));
  for(const input of [{delay_seconds:0},{delay_seconds:600,at:"2026-11-02T00:00Z"},{at:"2026-11-01T17:00:00"},{at:"2026-10-31T10:00:00Z"},{repeat:"daily",time:"25:00"},{repeat:"daily",time:"17:00",timezone:"Wrong/Zone"}])assert.throws(()=>makeSchedule("bot",{text:"Call",...input},now));
});
test("daily reminders use local calendar time and do not ring twice during DST fallback",()=>{
  assert.equal(new Date(nextDaily("17:00","Asia/Karachi",Date.parse("2026-11-01T10:00Z"))).toISOString(),"2026-11-01T12:00:00.000Z");
  // At 01:30 EDT the clock will repeat 01:30 EST; the same date must not ring twice.
  const fired=Date.parse("2026-11-01T05:30Z");
  assert.equal(new Date(nextDaily("01:30","America/New_York",fired,fired)).toISOString(),"2026-11-02T06:30:00.000Z");
  assert.equal(new Date(nextDaily("02:30","America/New_York",Date.parse("2026-03-08T06:00Z"))).toISOString(),"2026-03-09T06:30:00.000Z");
});
test("saved reminders survive restarts, fire while a bot is paused, cancel and advance without replaying",async()=>{
  const root=await mkdtemp(join(tmpdir(),"pocko-schedules-")),store=new Store(root);await store.init();
  const make=()=>new Bots(store,{} as any,{create:async()=>({id:"session"}),progress:()=>undefined,stop:()=>{}} as any,{} as any,{require:()=>{},list:()=>[],tools:async()=>[]} as any,()=>{},s=>s);
  let bots=make();await bots.init();
  try{
    const bot=await bots.create({name:"Reminder bot",role:"Help"});await bots.pause(bot.id);
    const tool=(await (bots as any).profile(bot,{depth:0})).tools.find((t:any)=>t.name==="schedule_reminder");
    assert.ok(tool);
    const listed=await tool.execute("list",{action:"list"});assert.equal(JSON.parse(listed.content[0].text).schedules.length,0);
    const created=await tool.execute("create",{action:"create",text:"Ten minutes",delay_seconds:600});
    const one=JSON.parse(created.content[0].text);
    const cancelled=await bots.schedule(bot.id,{text:"Cancelled",delay_seconds:600});await bots.cancelSchedule(bot.id,cancelled.id);
    const daily=await bots.schedule(bot.id,{text:"Daily call",repeat:"daily",time:"17:00",timezone:"Asia/Karachi"});
    await bots.close();bots=make();await bots.init();
    assert.equal(bots.listSchedules(bot.id).length,3);
    await bots.tick(one.nextRun);assert.equal(bots.pendingAlerts().filter(a=>a.text==="Ten minutes").length,1);
    assert.equal(bots.pendingAlerts().some(a=>a.text==="Cancelled"),false);
    const due=Math.max(one.nextRun,daily.nextRun);await bots.tick(due);
    const occurrence=bots.listSchedules(bot.id).find(s=>s.id===daily.id)!;
    assert.equal(occurrence.status,"active");assert.ok(occurrence.nextRun>due);
    const count=bots.pendingAlerts().length;await bots.close();bots=make();await bots.init();await bots.tick(due);
    assert.equal(bots.pendingAlerts().length,count);
    const disk=JSON.parse(await readFile(join(root,"bots.json"),"utf8"));assert.equal(disk.schedules.find((s:any)=>s.id===one.id).status,"completed");
    await bots.update(bot.id,{allowUrgent:false});await assert.rejects(bots.schedule(bot.id,{text:"No calls",delay_seconds:60}),/Enable ringing/);
    const ordinary=await bots.schedule(bot.id,{text:"Quiet reminder",delay_seconds:60,urgent:false});await bots.tick(ordinary.nextRun);
    assert.equal(bots.pendingAlerts().find(a=>a.text==="Quiet reminder")?.urgent,false);
    await bots.remove(bot.id);assert.equal(bots.pendingAlerts().length,0);
  }finally{await bots.close();await rm(root,{recursive:true,force:true});}
});
