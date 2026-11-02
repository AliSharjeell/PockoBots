import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,readFile} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {Store,defaults} from '../src/storage.js';
import {Previews} from '../src/previews.js';
import {MobileTasks} from '../src/mobile-tasks.js';
import {Bots} from '../src/bots.js';

test('preview requests persist, refresh the same URL, and report the actual Android receipt',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-preview-'));const store=new Store(root);await store.init();const previews=new Previews(store);await previews.init();
 try{
  const first=await previews.request('bot','http://127.0.0.1:3000');assert.equal(first.url,'http://127.0.0.1:3000/');
  const restored=new Previews(store);await restored.init();assert.equal(restored.pending()?.id,first.id);
  await restored.acknowledge(first.id,true);assert.equal(restored.pending(),null);
  const pending=restored.tool('bot').execute('reload',{url:'http://127.0.0.1:3000/',refresh:true});
  while(!restored.pending())await new Promise(r=>setTimeout(r,5));
  assert.equal(restored.pending()?.refresh,true);await restored.acknowledge(restored.pending()!.id,false,'No Android activity');
  const result=await pending;assert.match(JSON.stringify(result),/No Android activity/);assert.match(JSON.stringify(result),/failed/);
  for(const url of ['javascript:alert(1)','file:///etc/passwd','https://name:password@example.com'])await assert.rejects(restored.request('bot',url),/HTTP\/HTTPS/);
  const controller=new AbortController();controller.abort();await assert.rejects(restored.tool('bot').execute('cancel',{url:'https://example.com'},controller.signal),/cancelled/);assert.equal(restored.pending(),null);
 }finally{await rm(root,{recursive:true,force:true});}
});

test('floating follow-ups steer a live phone run without interrupting it; CLI runs keep the update queued',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-floating-'));const store=new Store(root);await store.init();const mobile=new MobileTasks(store,()=>defaults,'token',()=>{});await mobile.init();
 try{
  const task=await mobile.create('Open the webapp','bot');task.status='running';const updates:any[]=[];
  (mobile as any).agents.set(task.id,{steer:(value:any)=>updates.push(value),abort:()=>{throw Error('Steering must not abort');}});
  assert.equal(await mobile.steer(task.id,'Make the title bigger'),true);assert.equal(task.status,'running');assert.match(updates[0].content,/Make the title bigger/);
  assert.match(await readFile(join(root,'mobile-tasks.json'),'utf8'),/Make the title bigger/);
  (mobile as any).cliRuns.set(task.id,new AbortController());assert.equal(await mobile.steer(task.id,'CLI update'),false);(mobile as any).cliRuns.clear();
  const bots=new Bots(store,{} as any,{progress:()=>undefined} as any,mobile,{} as any,()=>{},text=>text);
  (bots as any).records=[{id:'bot'}];(bots as any).jobs=[{id:'job',botId:'bot',status:'running',phoneTaskId:task.id}];
  const delivered=await bots.sendOwnerMessage('bot','Tick the first box');assert.equal(delivered.delivery,'steered');assert.equal((bots as any).jobs.length,1);assert.equal(updates.length,2);
  assert.match((bots as any).messages[0].text,/Tick the first box/);
  (mobile as any).cliRuns.set(task.id,new AbortController());
  const queued=await bots.sendOwnerMessage('bot','Apply this after the current run');assert.equal(queued.delivery,'queued');assert.equal((bots as any).jobs[0].status,'running');assert.equal((bots as any).jobs[1].status,'queued');assert.equal(updates.length,2);
  (mobile as any).cliRuns.clear();
  task.status='completed';assert.equal(await mobile.steer(task.id,'After completion'),false);
 }finally{(mobile as any).agents.clear();await rm(root,{recursive:true,force:true});}
});
