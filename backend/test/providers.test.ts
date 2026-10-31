import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp,rm } from "node:fs/promises";
import { MobileTasks } from "../src/mobile-tasks.js";
import http from "node:http";
import { Workspace } from "../src/workspace.js";
import { Sessions } from "../src/sessions.js";
import { Processes } from "../src/processes.js";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import express from "express";
import { Type } from "typebox";
import { SubscriptionProviders, executeProvider, providerCatalog, providerText } from "../src/providers.js";
import { Store, defaults, validateSettings } from "../src/storage.js";

test("subscription provider uses a real process and MCP bridge without importing credentials",async()=>{
 const root=await mkdtemp(join(tmpdir(),"pocko-provider-test-"));const runtime=new SubscriptionProviders(()=>({command:process.execPath,args:[fileURLToPath(new URL('./fixtures/provider-cli.mjs',import.meta.url))]}));
 const app=express();app.use(express.json());let receipt="",run="",secret="";
 app.post('/api/provider-tools/:run/:operation',async(req,res)=>{try{assert.equal(req.headers.authorization,'Bearer owner-token');run=req.params.run;secret=String(req.headers['x-provider-bridge-token']);res.json(await runtime.bridge(run,secret,req.params.operation,req.body));}catch(error:any){res.status(400).json({error:error.message});}});
 const server=app.listen(0,'127.0.0.1');await new Promise<void>(r=>server.on('listening',r));runtime.configure('http://127.0.0.1:'+(server.address() as any).port,'owner-token');
 try{const output=await runtime.run('codex',{cwd:root,prompt:'Save a note',tools:[{name:'write_note',label:'Write note',description:'Test tool',parameters:Type.Object({text:Type.String()}),execute:async(_id,p:any)=>{receipt=p.text;return {content:[{type:'text',text:'Saved'}],details:{}};}}],signal:new AbortController().signal});assert.equal(output,'The subscription CLI used the PockoBots tool');assert.match(receipt,/real provider MCP bridge/);await assert.rejects(runtime.bridge(run,secret,'list',{}),/expired/);assert.equal(providerText({type:'item.completed',item:{type:'reasoning',text:'secret'}}),undefined);}
 finally{await new Promise<void>(r=>server.close(()=>r()));await rm(root,{recursive:true,force:true});}
});

test("subscription process cancellation stops the child and reports cancellation",async()=>{
 const abort=new AbortController();const running=executeProvider(process.execPath,['-e','setInterval(()=>{},1000)'],{cwd:tmpdir(),env:process.env,signal:abort.signal});setTimeout(()=>abort.abort(),100);await assert.rejects(running,/cancelled/);
});

test("provider settings keep BYOK and distinguish unsupported subscriptions",()=>{
 assert.equal(validateSettings({},defaults).executionProvider,'byok');const selected=validateSettings({executionProvider:'codex',providerModel:'default'},defaults);assert.equal(selected.executionProvider,'codex');assert.equal(selected.apiKey,'');assert.throws(()=>validateSettings({executionProvider:'made-up'},defaults),/Invalid execution/);assert.equal(providerCatalog.find(p=>p.id==='freebuff')?.supported,false);
});


test("Gemini text deltas assemble and provider-reported failures stay failures",async()=>{
 const script='console.log(JSON.stringify({type:"message",role:"assistant",content:"Hello",delta:true}));console.log(JSON.stringify({type:"message",role:"assistant",content:" world",delta:true}));';
 assert.equal(await executeProvider(process.execPath,['-e',script],{cwd:tmpdir(),env:process.env,signal:new AbortController().signal}),"Hello world");
 await assert.rejects(executeProvider(process.execPath,['-e','console.log(JSON.stringify({type:"result",is_error:true,result:"Account unavailable"}))'],{cwd:tmpdir(),env:process.env,signal:new AbortController().signal}),/successful text/);
});


test("subscription sessions run without BYOK, require shell consent and persist the same chat",async()=>{
 const root=await mkdtemp(join(tmpdir(),"pocko-subscription-session-"));const store=new Store(join(root,"state"));await store.init();const workspace=new Workspace(join(root,"project"));await workspace.init();let received="";
 const runtime={run:async(_id:string,options:any)=>{received=options.prompt;assert.ok(options.tools.some((t:any)=>t.name==="write_file"));return "Saved with subscription credentials owned by the vendor CLI";}} as unknown as SubscriptionProviders;
 const settings=validateSettings({executionProvider:"codex"},defaults);const sessions=new Sessions(store,workspace,new Processes(()=>{}),()=>settings,()=>{},undefined,runtime);await sessions.init();const session=await sessions.create();
 try{await assert.rejects(sessions.prompt(session.id,"Remember my project"),/shell access/);sessions.setTrust(session.id,true);await sessions.prompt(session.id,"Remember my project");for(let i=0;sessions.state(session.id).busy&&i<100;i++)await new Promise(r=>setTimeout(r,5));assert.equal(sessions.state(session.id).busy,false);assert.match(received,/Remember my project/);assert.equal(settings.apiKey,"");assert.equal(sessions.state(session.id).messages.length,2);await sessions.persist();const reopened=new Sessions(store,workspace,new Processes(()=>{}),()=>settings,()=>{});await reopened.init();assert.equal(reopened.state(session.id).messages.length,2);assert.equal((reopened.state(session.id).messages.at(-1) as any).stopReason,"stop");}
 finally{sessions.stopAll();await rm(root,{recursive:true,force:true});}
});


test("subscription phone task observes and verifies through the same guarded MCP bridge without BYOK",async()=>{
 const root=await mkdtemp(join(tmpdir(),"pocko-subscription-phone-"));const store=new Store(root);await store.init();let observations=0;
 const native=http.createServer((req,res)=>{assert.equal(req.headers["x-artemis-token"],"phone-token");res.setHeader("Content-Type","application/json");if(req.url?.startsWith("/ping"))res.end(JSON.stringify({success:true,authenticated:true,task_allowed:true}));else{observations++;res.end(JSON.stringify({success:true,width:1000,height:2000,package:"com.android.settings",elements:[{text:"Battery",enabled:true,bounds:"[0,0][100,100]"}],has_screenshot:false}));}});
 await new Promise<void>(r=>native.listen(0,"127.0.0.1",r));const runtime=new SubscriptionProviders(()=>({command:process.execPath,args:[fileURLToPath(new URL("./fixtures/provider-cli.mjs",import.meta.url))]}));
 const app=express();app.use(express.json());app.post("/api/provider-tools/:run/:operation",async(req,res)=>{try{assert.equal(req.headers.authorization,"Bearer owner-token");res.json(await runtime.bridge(req.params.run,String(req.headers["x-provider-bridge-token"]),req.params.operation,req.body));}catch(e:any){res.status(400).json({error:e.message});}});const server=app.listen(0,"127.0.0.1");await new Promise<void>(r=>server.on("listening",r));runtime.configure("http://127.0.0.1:"+(server.address() as any).port,"owner-token");
 const mobile=new MobileTasks(store,()=>validateSettings({executionProvider:"codex"},defaults),"phone-token",()=>{},"http://127.0.0.1:"+(native.address() as any).port,runtime);await mobile.init();
 try{const task=await mobile.create("Verify the Battery screen");await mobile.start(task.id);const deadline=Date.now()+15000;while(mobile.anyBusy()&&Date.now()<deadline)await new Promise(r=>setTimeout(r,25));assert.equal(mobile.anyBusy(),false);assert.equal(mobile.state(task.id).status,"completed");assert.equal(observations,1);assert.match(mobile.state(task.id).output,/subscription phone tools/);assert.equal(mobile.state(task.id).steps,2);}
 finally{mobile.stopAll();await new Promise<void>(r=>server.close(()=>r()));await new Promise<void>(r=>native.close(()=>r()));await rm(root,{recursive:true,force:true});}
});
