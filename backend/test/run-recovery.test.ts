import {test} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {mkdtemp,rm,readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {Store,defaults} from '../src/storage.js';
import {Workspace} from '../src/workspace.js';
import {Sessions} from '../src/sessions.js';
import {Processes} from '../src/processes.js';
import {Bots} from '../src/bots.js';

async function until(check:()=>boolean){const deadline=Date.now()+10000;while(!check()&&Date.now()<deadline)await new Promise(r=>setTimeout(r,15));assert.ok(check(),'Condition timed out');}
async function fixture(reply:(number:number,body:any)=>Promise<string>|string){
 const root=await mkdtemp(join(tmpdir(),'pocko-recovery-'));let requests=0;const bodies:any[]=[];
 const server=http.createServer((req,res)=>{let body='';req.on('data',chunk=>body+=chunk);req.on('end',async()=>{const parsed=JSON.parse(body);bodies.push(parsed);const text=await reply(++requests,parsed);if(res.destroyed)return;res.setHeader('Content-Type','text/event-stream');const emit=(delta:any,finish:any=null)=>res.write('data: '+JSON.stringify({id:'fixture',object:'chat.completion.chunk',created:1,model:'fixture',choices:[{index:0,delta,finish_reason:finish}]})+'\n\n');emit({role:'assistant',...(text?{content:text}:{reasoning_content:'Checking the task'})});emit({},'stop');res.end('data: [DONE]\n\n');});});
 await new Promise<void>(r=>server.listen(0,'127.0.0.1',r));const store=new Store(join(root,'state'));await store.init();const workspace=new Workspace(join(root,'work'));await workspace.init();
 const settings={...defaults,provider:'openai' as const,api:'openai-completions' as const,baseUrl:'http://127.0.0.1:'+(server.address() as any).port,apiKey:'fixture-key',model:'fixture'};
 const sessions=new Sessions(store,workspace,new Processes(()=>{}),()=>settings,()=>{});await sessions.init();const session=await sessions.create();
 return {root,store,sessions,id:session.id,bodies,requests:()=>requests,close:async()=>{sessions.stopAll();server.closeAllConnections();await new Promise<void>(r=>server.close(()=>r()));await new Promise(r=>setTimeout(r,550));await rm(root,{recursive:true,force:true});}};
}
test('a thinking-only response continues to a real final answer',async()=>{
 const f=await fixture(n=>n===1?'':'Finished and verified');try{await f.sessions.prompt(f.id,'Build the page');await until(()=>!f.sessions.anyBusy());assert.equal(f.requests(),2);const state=f.sessions.state(f.id);assert.equal(state.runError,undefined);assert.equal(state.progress?.status,'completed');assert.match(JSON.stringify(state.messages.at(-1)),/Finished and verified/);}finally{await f.close();}
});
test('repeated empty turns fail explicitly with progress retained',async()=>{
 const f=await fixture(()=>'');try{await f.sessions.prompt(f.id,'Build the page');await until(()=>!f.sessions.anyBusy());assert.equal(f.requests(),4);assert.match(f.sessions.state(f.id).runError||'',/repeatedly ended/);assert.equal(f.sessions.state(f.id).progress?.status,'failed');}finally{await f.close();}
});
test('owner steering reaches the same running agent without aborting or replacing its run',async()=>{
 let release!:()=>void;const held=new Promise<void>(r=>release=r);const f=await fixture(async n=>{if(n===1){await held;return 'Initial progress';}return 'Applied the owner update';});
 try{await f.sessions.prompt(f.id,'Build the page');await until(()=>f.requests()===1);const run=f.sessions.progress(f.id)!.id;assert.equal(await f.sessions.steer(f.id,'Make it blue'),true);assert.equal(f.sessions.state(f.id).busy,true);release();await until(()=>!f.sessions.anyBusy());assert.equal(f.requests(),2);assert.equal(f.sessions.progress(f.id)!.id,run);assert.match(JSON.stringify(f.bodies[1]),/Make it blue/);assert.match(JSON.stringify(f.sessions.state(f.id).messages.at(-1)),/Applied the owner update/);}finally{release();await f.close();}
});
test('reading teammate progress does not alter its job, queue or running session',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-status-'));const store=new Store(root);await store.init();const states=new Map<string,any>(),profiles=new Map<string,any>();let stops=0;
 const sessions:any={create:async()=>{const id=randomUUID();states.set(id,{busy:false,approvals:[],messages:[]});return {id};},setTrust:()=>{},state:(id:string)=>states.get(id),progress:()=>({status:'working',started:Date.now(),updated:Date.now(),tools:[{name:'bash',status:'running'}],text:'Building the app',thinking:''}),prompt:async(id:string,_text:string,profile:any)=>{states.get(id).busy=true;profiles.set(id,profile);},stop:(id:string)=>{stops++;states.get(id).busy=false;}};
 const bots=new Bots(store,{create:async()=>({id:randomUUID()})} as any,sessions,{} as any,{require:()=>{},tools:async()=>[]} as any,()=>{},text=>text);await bots.init();
 try{const builder=await bots.create({name:'Ali'}),observer=await bots.create({name:'Assistant'});await bots.enqueue(builder.id,'Build a page');await bots.tick();await bots.enqueue(observer.id,'Check Ali progress');await bots.tick();const before=await readFile(join(root,'bots.json'),'utf8');const status=profiles.get(observer.sessionId).tools.find((tool:any)=>tool.name==='get_bot_status');const result=await status.execute('inspect',{bot_id:builder.id});assert.match(JSON.stringify(result),/Building the app/);assert.equal(await readFile(join(root,'bots.json'),'utf8'),before);assert.equal(states.get(builder.sessionId).busy,true);assert.equal(stops,0);}finally{await bots.close();await rm(root,{recursive:true,force:true});}
});
