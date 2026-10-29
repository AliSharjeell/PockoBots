import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp,rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import express from "express";
import { Type } from "typebox";
import { SubscriptionProviders, executeProvider, providerCatalog, providerText } from "../src/providers.js";
import { defaults, validateSettings } from "../src/storage.js";

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
