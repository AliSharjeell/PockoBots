import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,writeFile,copyFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {agentReachTool,runAgentReach} from '../src/agent-reach.js';
import {Bots} from '../src/bots.js';

const python=process.platform==='win32'?'python':'python3';
const launcher=fileURLToPath(new URL('../runtime/agent_reach_launcher.py',import.meta.url));

test('offline bundle discovers readers concurrently, validates arguments, and refuses browser or paid setup',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-reach-'));
 const run=(request:any)=>runAgentReach(request,undefined,{python,launcher,stateDir:root});
 try{
  const results=await Promise.all([run({operation:'channels'}),run({operation:'channels'})]);
  for(const value of results){assert.equal(value.ok,true);assert.equal(value.channels.length,14);assert.equal(value.version,'1.6.1');}
  const channels=results[0].channels;
  assert.equal(channels.find((c:any)=>c.name==='web').actions.find((a:any)=>a.name==='search').available,true);
  assert.equal(channels.find((c:any)=>c.name==='youtube').actions.find((a:any)=>a.name==='transcribe').available,false);
  const blocked=await run({operation:'run',channel:'instagram',action:'search',arguments:{query:'example'}});
  assert.equal(blocked.ok,false);assert.equal(blocked.error.code,'NEED_SETUP');
  const bad=await run({operation:'run',channel:'web',action:'search',arguments:{query:'test',unexpected:'argument'}});
  assert.equal(bad.ok,false);assert.equal(bad.error.code,'BAD_INPUT');
  const large=await run({operation:'run',channel:'youtube',action:'search',arguments:{query:'test',limit:1000}});
  assert.equal(large.ok,false);assert.equal(large.error.code,'BAD_INPUT');
 }finally{await rm(root,{recursive:true,force:true});}
});

test('runtime refuses a modified bundle before importing downloaded code',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-reach-integrity-'));
 try{
  const copy=join(root,'agent_reach_launcher.py');await copyFile(launcher,copy);
  await copyFile(fileURLToPath(new URL('../runtime/agent-reach-lock.json',import.meta.url)),join(root,'agent-reach-lock.json'));
  await writeFile(join(root,'agent-reach-vendor.zip'),'corrupt');
  const result=await runAgentReach({operation:'channels'},undefined,{python,launcher:copy,stateDir:join(root,'state')});
  assert.equal(result.ok,false);assert.match(result.error.message,/integrity check/);
 }finally{await rm(root,{recursive:true,force:true});}
});

test('reader preserves arguments as data, enforces deadlines and cancellation, and reports missing Python',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-reach-process-'));
 try{
  const fake=join(root,'reader.py');await writeFile(fake,'import json,sys,time\nr=json.load(sys.stdin)\nif r.get("action")=="wait": time.sleep(20)\nprint(json.dumps({"ok":True,"request":r}))\n');
  const options={python,launcher:fake,stateDir:root};
  const payload={operation:'run' as const,channel:'web',action:'search',arguments:{query:'$(touch injected); & "hello"\nnext'}};
  const result=await runAgentReach(payload,undefined,options);assert.deepEqual(result.request,payload);
  await assert.rejects(runAgentReach({...payload,action:'wait'},undefined,{...options,timeoutMs:150}),/timed out/);
  const controller=new AbortController();const pending=runAgentReach({...payload,action:'wait'},controller.signal,options);setTimeout(()=>controller.abort(),150);
  await assert.rejects(pending,/cancelled/);
  await assert.rejects(runAgentReach(payload,undefined,{...options,python:join(root,'missing-python')}),/could not start/);
  await assert.rejects(runAgentReach({...payload,arguments:{query:'x'.repeat(65000)}},undefined,options),/too large/);
 }finally{await rm(root,{recursive:true,force:true});}
});

test('existing and newly created bot profiles receive the default reader without a connector selection',async()=>{
 const bots=new Bots({dir:tmpdir()} as any,{} as any,{} as any,{} as any,{tools:async()=>[],list:()=>[]} as any,()=>{},value=>value);
 for(const name of ['Existing bot','New bot']){
  const profile=await (bots as any).profile({id:name,name,role:'Help',connectors:[]},{id:'job'});
  assert.equal(profile.tools.filter((tool:any)=>tool.name==='agent_reach').length,1);
  assert.match(profile.instructions,/Agent-Reach is bundled/);
 }
 let request:any;
 const tool=agentReachTool(text=>text.replace('secret','[redacted]'),async value=>{request=value;return {ok:true,note:'secret'};});
 const output=await tool.execute('test',{operation:'channels'});assert.deepEqual(request,{operation:'channels'});
 assert.match(JSON.stringify(output),/redacted/);assert.doesNotMatch(JSON.stringify(output),/secret/);
});

