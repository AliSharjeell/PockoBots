import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { mkdtemp, rm, readFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { createApp } from "../src/server.js";
import { Bots } from "../src/bots.js";
import { botContext } from "../src/sessions.js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { randomUUID } from "node:crypto";
import { z } from "zod";
import { Store } from "../src/storage.js";
import { Connectors } from "../src/connectors.js";
import { fileURLToPath } from "node:url";

async function listen(server: http.Server) { await new Promise<void>(r => server.listen(0,"127.0.0.1",r)); return (server.address() as any).port as number; }
async function close(server: http.Server) { server.closeAllConnections(); await new Promise<void>(r => server.close(() => r())); }
async function until(fn:()=>Promise<any>, check:(v:any)=>boolean) { for(let i=0;i<300;i++){const value=await fn();if(check(value))return value;await new Promise(r=>setTimeout(r,30));}throw Error("Bot did not reach the expected state"); }

test("bots keep FIFO inboxes, isolate roles, run selected MCP tools and restore paused jobs", async () => {
  const root=await mkdtemp(join(tmpdir(),"pocket-bots-"));let calls=0;const prompts:any[]=[];
  const model=http.createServer(async(req,res)=>{
    let raw="";for await(const data of req)raw+=data;const body=JSON.parse(raw);prompts.push(body);
    const tool=body.tools?.find((t:any)=>t.function.name.startsWith("mcp_"));const hasResult=body.messages.some((m:any)=>m.role==="tool");
    res.setHeader("Content-Type","text/event-stream");
    const emit=(delta:any,finish:any=null)=>res.write("data: "+JSON.stringify({id:"bot-fixture",object:"chat.completion.chunk",created:1,model:"fixture",choices:[{index:0,delta,finish_reason:finish}]})+"\n\n");
    if(tool&&!hasResult)emit({role:"assistant",tool_calls:[{index:0,id:"lookup",type:"function",function:{name:tool.function.name,arguments:'{"topic":"local"}'}}]});
    else emit({role:"assistant",content:"Finished on this device"});
    emit({},tool&&!hasResult?"tool_calls":"stop");res.end("data: [DONE]\n\n");
  });const modelPort=await listen(model);
  const mcp=new McpServer({name:"fixture",version:"1"});mcp.registerTool("lookup",{description:"Look up a note",inputSchema:{topic:z.string()}},async({topic})=>{calls++;return {content:[{type:"text",text:"A local note about "+topic}]};});
  const transport=new StreamableHTTPServerTransport({sessionIdGenerator:()=>randomUUID()});await mcp.connect(transport);
  const mcpHttp=http.createServer((req,res)=>{void transport.handleRequest(req,res);});const mcpPort=await listen(mcpHttp);
  const runtime=await createApp({stateDir:join(root,"state"),workspace:join(root,"workspace"),port:18876});const server=http.createServer(runtime.app);await new Promise<void>(r=>server.listen(18876,"127.0.0.1",r));
  const call=async(method:string,path:string,data?:any)=>{const response=await fetch("http://127.0.0.1:18876/api"+path,{method,headers:{Authorization:"Bearer "+runtime.token,"Content-Type":"application/json"},body:data?JSON.stringify(data):undefined});const value:any=await response.json();assert.equal(response.ok,true,JSON.stringify(value));return value;};
  try {
    await call("PUT","/settings",{provider:"openai",api:"openai-completions",baseUrl:`http://127.0.0.1:${modelPort}`,model:"fixture",apiKey:"fixture-key"});
    const connector=await call("POST","/connectors",{name:"Notes",transport:"http",url:`http://127.0.0.1:${mcpPort}/mcp`,token:"connector-private-key"});assert.equal(connector.token,undefined);assert.equal(connector.hasToken,true);
    assert.equal((await call("POST",`/connectors/${connector.id}/test`,{})).tools[0].name,"lookup");
    const alpha=await call("POST","/bots",{name:"Builder",role:"ALPHA_ROLE",kind:"coding",connectors:[connector.id]});
    const beta=await call("POST","/bots",{name:"Writer",role:"BETA_ROLE",kind:"coding",connectors:[]});assert.notEqual(alpha.projectId,beta.projectId);assert.notEqual(alpha.sessionId,beta.sessionId);
    await call("POST",`/bots/${alpha.id}/messages`,{text:"First task"});await call("POST",`/bots/${alpha.id}/messages`,{text:"Second task"});await call("POST",`/bots/${beta.id}/messages`,{text:"Independent task"});
    await call("PUT","/bots/engine",{enabled:true});
    const done=await until(()=>call("GET",`/bots/${alpha.id}`),s=>s.jobs.length===2&&s.jobs.every((j:any)=>j.status==="completed"));assert.equal(done.messages.filter((m:any)=>m.role==="assistant").length,2);assert.ok(calls>=1);
    assert.deepEqual(done.jobs.map((j:any)=>j.text),["First task","Second task"]);
    await until(()=>call("GET",`/bots/${beta.id}`),s=>s.jobs[0]?.status==="completed");
    const betaRequest=prompts.find(p=>p.messages.some((m:any)=>String(m.content).includes("BETA_ROLE")));assert.ok(betaRequest);assert.ok(!betaRequest.tools.some((t:any)=>t.function.name.startsWith("mcp_")));
    await call("POST",`/bots/${alpha.id}/pause`,{});await call("POST",`/bots/${alpha.id}/messages`,{text:"Keep queued during pause"});await runtime.bots.tick();assert.equal((await call("GET",`/bots/${alpha.id}`)).jobs.at(-1).status,"queued");
    assert.ok((await readFile(join(root,"state/bots.json"),"utf8")).includes("Keep queued during pause"));
    await call("PUT","/bots/engine",{enabled:false});assert.equal((await call("GET","/bots")).enabled,false);
    const reopened=await createApp({stateDir:join(root,"state"),workspace:join(root,"workspace"),port:18877});
    assert.equal(reopened.bots.state(alpha.id).jobs.at(-1)?.status,"queued");assert.equal(reopened.bots.enabled,false);await reopened.close();
  } finally { await runtime.close(); await close(server); await mcp.close(); await close(mcpHttp); await close(model); await rm(root,{recursive:true,force:true}); }
});

test("MCP stdio starts a real local tool server and closes it",async()=>{
  const root=await mkdtemp(join(tmpdir(),"pocket-stdio-"));const store=new Store(root);await store.init();const connectors=new Connectors(store);await connectors.init();
  try{const c=await connectors.save({name:"Local process",transport:"stdio",command:process.execPath,args:[fileURLToPath(new URL("./fixtures/mcp-stdio.mjs",import.meta.url))]});assert.equal((await connectors.inspect(c.id)).tools[0].name,"local_note");const tools=await connectors.tools([c.id]);const result=await tools[0].execute("test",{});assert.ok(JSON.stringify(result).includes("stdio transport ran on this machine"));}
  finally{await connectors.close();await rm(root,{recursive:true,force:true});}
});

test("bot context keeps complete tool groups and bounds history",()=>{
  const messages:any[]=[{role:"user",content:"old"},{role:"assistant",content:[{type:"toolCall",id:"call",name:"read",arguments:{}}]},{role:"toolResult",toolCallId:"call",content:[{type:"text",text:"x".repeat(5000)}]},{role:"user",content:"recent"},{role:"assistant",content:[{type:"text",text:"latest"}]}];
  const kept=botContext(messages,1000);assert.equal(kept.length,2);assert.equal(kept[0].role,"user");assert.equal((kept[0] as any).content,"recent");
});


test("proactive checks coalesce missed intervals, honor daily limits and keep NO_UPDATE quiet",async()=>{
  const root=await mkdtemp(join(tmpdir(),"pocket-routines-"));const store=new Store(root);await store.init();let runs=0;
  const sessions:any={create:async()=>({id:randomUUID()}),stop:()=>{},setTrust:()=>{},prompt:async()=>{runs++;},state:()=>({busy:false,messages:[{role:"assistant",content:[{type:"text",text:"NO_UPDATE"}]}]})};
  const bots=new Bots(store,{create:async()=>({id:randomUUID()})} as any,sessions,{} as any,{require:()=>{},tools:async()=>[]} as any,()=>{},s=>s);
  await bots.init();try{
    const bot=await bots.create({name:"Watcher",role:"Check updates",kind:"coding",intervalMinutes:15,heartbeat:"Check now",dailyLimit:1});await bots.engine(true);
    const late=bot.nextWake+4*60*60000;await bots.tick(late);assert.equal(runs,1);assert.equal(bots.state(bot.id).jobs.length,1);assert.equal(bot.nextWake,late+15*60000);
    await bots.tick(late+1000);assert.equal(bots.state(bot.id).jobs[0].status,"completed");assert.equal(bots.state(bot.id).messages.filter(m=>m.role==="assistant").length,0);
    await bots.tick(late+16*60000);assert.equal(runs,1);assert.equal(bots.state(bot.id).jobs.length,1);
    await bots.tick(late+24*60*60000);assert.equal(runs,2);assert.equal(bots.state(bot.id).jobs.length,2);
  }finally{await bots.close();await rm(root,{recursive:true,force:true});}
});

test("ringing alerts require per-bot consent, persist acknowledgements and rate-limit rings",async()=>{
  const root=await mkdtemp(join(tmpdir(),"pocket-alerts-"));const app=await createApp({stateDir:join(root,"state"),workspace:join(root,"workspace"),port:18878});
  try{
    const bot=await app.bots.create({name:"Notifier",role:"Notify me",kind:"coding"});
    await assert.rejects(app.bots.alert(bot.id,"Please check",true),/disabled/);
    const ordinary=await app.bots.alert(bot.id,"An update",false);assert.equal(app.bots.pendingAlerts().length,1);await app.bots.acknowledgeAlert(ordinary.id);assert.equal(app.bots.pendingAlerts().length,0);
    await app.bots.update(bot.id,{allowUrgent:true});const urgent=await app.bots.alert(bot.id,"Time sensitive update",true);assert.equal(urgent.urgent,true);
    await app.bots.acknowledgeAlert(urgent.id);await assert.rejects(app.bots.alert(bot.id,"Ring again",true),/ten minutes/);
    const persisted=JSON.parse(await readFile(join(root,"state/bots.json"),"utf8"));assert.equal(persisted.alerts.find((a:any)=>a.id===urgent.id).acknowledged,true);
    for(let i=0;i<10;i++)await app.bots.alert(bot.id,"Queued update "+i,false);await assert.rejects(app.bots.alert(bot.id,"Too many",false),/unread/);
  }finally{await app.close();await rm(root,{recursive:true,force:true});}
});


test("pausing during an MCP connection prevents the queued agent from starting",async()=>{
  const root=await mkdtemp(join(tmpdir(),"pocket-pause-"));const store=new Store(root);await store.init();let started=0;let release!:(tools:any[])=>void;let connecting!:()=>void;
  const connected=new Promise<void>(r=>connecting=r);const tools=new Promise<any[]>(r=>release=r);
  const bots=new Bots(store,{create:async()=>({id:randomUUID()})} as any,{create:async()=>({id:randomUUID()}),stop:()=>{},setTrust:()=>{},prompt:async()=>{started++;},state:()=>({busy:false,messages:[]})} as any,{} as any,{require:()=>{},tools:()=>{connecting();return tools;}} as any,()=>{},s=>s);
  await bots.init();try{const bot=await bots.create({name:"Slow connector",role:"Wait",kind:"coding"});await bots.enqueue(bot.id,"Do a task");await bots.engine(true);const tick=bots.tick();await connected;await bots.pause(bot.id);release([]);await tick;assert.equal(started,0);assert.equal(bots.state(bot.id).jobs[0].status,"paused");}finally{release([]);await bots.close();await rm(root,{recursive:true,force:true});}
});
