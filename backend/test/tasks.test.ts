import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {Store} from '../src/storage.js';
import {Tasks} from '../src/tasks.js';
import {createApp} from '../src/server.js';

async function fixture(){
 const root=await mkdtemp(join(tmpdir(),'pocko-tasks-'));const store=new Store(root);await store.init();
 const clock={t:1000};const open=async()=>{const t=new Tasks(store,()=>{},()=>clock.t);await t.init();return t;};
 return {root,clock,open,tasks:await open(),close:()=>rm(root,{recursive:true,force:true})};
}
const plan={title:'Send the report',owner:'server',steps:[{title:'Draft'},{title:'Email it',external:true},{title:'Log it'}]};

test('a plan runs step by step, the external step only after a recorded approval',async()=>{
 const f=await fixture();try{
  const task=await f.tasks.create(plan);const [draft,email,log]=task.steps;
  await assert.rejects(f.tasks.claim(task.id,'phone'),/belongs to the server/);
  let {leaseId}=await f.tasks.claim(task.id,'server');
  await assert.rejects(f.tasks.startStep(task.id,leaseId,email.id),/next pending step/);
  await f.tasks.startStep(task.id,leaseId,draft.id);await f.tasks.progress(task.id,leaseId,draft.id,'Half way');await f.tasks.finishStep(task.id,leaseId,draft.id,'draft.md written');
  await assert.rejects(f.tasks.startStep(task.id,leaseId,email.id),/recorded approval/);
  const input=await f.tasks.ask(task.id,leaseId,email.id,'input','Who gets it?');
  await assert.rejects(f.tasks.answer(task.id,input.id,true),/answer of at most/);
  await f.tasks.answer(task.id,input.id,'Sam');
  await assert.rejects(f.tasks.answer(task.id,input.id,'Sam again'),/already answered/);
  ({leaseId}=await f.tasks.claim(task.id,'server'));
  const approval=await f.tasks.ask(task.id,leaseId,email.id,'approval','Email Sam?');
  await assert.rejects(f.tasks.startStep(task.id,leaseId,email.id),/no longer holds/);
  await f.tasks.answer(task.id,approval.id,true);
  ({leaseId}=await f.tasks.claim(task.id,'server'));
  await f.tasks.startStep(task.id,leaseId,email.id);await f.tasks.finishStep(task.id,leaseId,email.id,'message 42');
  await f.tasks.startStep(task.id,leaseId,log.id);const done=await f.tasks.finishStep(task.id,leaseId,log.id,'logged');
  assert.equal(done.status,'completed');assert.equal(done.lease,undefined);assert.deepEqual(done.steps.map(s=>s.receipt?.result),['draft.md written','message 42','logged']);
  await assert.rejects(f.tasks.retry(task.id),/failed or cancelled/);await assert.rejects(f.tasks.pause(task.id),/completed task cannot become paused/);
 }finally{await f.close();}
});

test('pausing makes an open request stale; a declined approval fails the task',async()=>{
 const f=await fixture();try{
  const task=await f.tasks.create(plan);let {leaseId}=await f.tasks.claim(task.id,'server');
  await f.tasks.startStep(task.id,leaseId,task.steps[0].id);await f.tasks.finishStep(task.id,leaseId,task.steps[0].id,'ok');
  const first=await f.tasks.ask(task.id,leaseId,task.steps[1].id,'approval','Email?');
  await f.tasks.pause(task.id);await f.tasks.resume(task.id);
  await assert.rejects(f.tasks.answer(task.id,first.id,true),/no longer current/);
  ({leaseId}=await f.tasks.claim(task.id,'server'));
  const second=await f.tasks.ask(task.id,leaseId,task.steps[1].id,'approval','Email?');
  const declined=await f.tasks.answer(task.id,second.id,false);assert.equal(declined.status,'failed');
  await assert.rejects(f.tasks.resume(task.id),/Only a paused task/);
  const retried=await f.tasks.retry(task.id);assert.equal(retried.status,'queued');assert.equal(retried.steps[0].status,'done');
 }finally{await f.close();}
});

test('retry skips steps that have receipts',async()=>{
 const f=await fixture();try{
  const task=await f.tasks.create({title:'Build',steps:[{title:'One'},{title:'Two'}]});let {leaseId}=await f.tasks.claim(task.id,'server');
  await f.tasks.startStep(task.id,leaseId,task.steps[0].id);await f.tasks.finishStep(task.id,leaseId,task.steps[0].id,'one done');
  await f.tasks.startStep(task.id,leaseId,task.steps[1].id);const failed=await f.tasks.failStep(task.id,leaseId,task.steps[1].id,'disk full');
  assert.equal(failed.status,'failed');await f.tasks.retry(task.id);({leaseId}=await f.tasks.claim(task.id,'server'));
  await assert.rejects(f.tasks.startStep(task.id,leaseId,task.steps[0].id),/next pending step/);
  await f.tasks.startStep(task.id,leaseId,task.steps[1].id);const done=await f.tasks.finishStep(task.id,leaseId,task.steps[1].id,'two done');
  assert.equal(done.status,'completed');assert.equal(done.steps[0].receipt?.result,'one done');
 }finally{await f.close();}
});

test('an expired lease survives restart as recoverable work; an external step in flight becomes uncertain',async()=>{
 const f=await fixture();try{
  const quiet=await f.tasks.create({title:'Quiet',owner:'phone',steps:[{title:'Read'}]});
  const loud=await f.tasks.create(plan);
  const a=await f.tasks.claim(quiet.id,'phone',5000);await f.tasks.startStep(quiet.id,a.leaseId,quiet.steps[0].id);
  let {leaseId}=await f.tasks.claim(loud.id,'server',5000);await f.tasks.startStep(loud.id,leaseId,loud.steps[0].id);await f.tasks.finishStep(loud.id,leaseId,loud.steps[0].id,'drafted');
  const approval=await f.tasks.ask(loud.id,leaseId,loud.steps[1].id,'approval','Email?');await f.tasks.answer(loud.id,approval.id,true);
  ({leaseId}=await f.tasks.claim(loud.id,'server',5000));await f.tasks.startStep(loud.id,leaseId,loud.steps[1].id);
  // Crash: a new process loads the file after the leases expired.
  f.clock.t+=6000;const after=await f.open();
  assert.equal(after.get(quiet.id).status,'queued');assert.equal(after.get(quiet.id).steps[0].status,'pending');
  const stuck=after.get(loud.id);assert.equal(stuck.status,'paused');assert.equal(stuck.steps[1].status,'uncertain');assert.equal(stuck.steps[0].receipt?.result,'drafted');
  await assert.rejects(after.finishStep(loud.id,leaseId,loud.steps[1].id,'late'),/no longer holds/);
  await assert.rejects(after.resume(loud.id),/Reconcile/);
  // Not done: back to pending, and the used approval does not count again.
  await after.reconcile(loud.id,loud.steps[1].id,false);await after.resume(loud.id);({leaseId}=await after.claim(loud.id,'server'));
  await assert.rejects(after.startStep(loud.id,leaseId,loud.steps[1].id),/recorded approval/);
  await assert.rejects(after.failStep(loud.id,leaseId,loud.steps[1].id,'x'),/Start the step first/);
 }finally{await f.close();}
});

test('a worker-reported unknown outcome is reconciled by the user, never re-run',async()=>{
 const f=await fixture();try{
  const task=await f.tasks.create({title:'Pay',steps:[{title:'Pay invoice',external:true}]});let {leaseId}=await f.tasks.claim(task.id,'server');
  const approval=await f.tasks.ask(task.id,leaseId,task.steps[0].id,'approval','Pay 10?');await f.tasks.answer(task.id,approval.id,true);
  ({leaseId}=await f.tasks.claim(task.id,'server'));await f.tasks.startStep(task.id,leaseId,task.steps[0].id);
  const paused=await f.tasks.failStep(task.id,leaseId,task.steps[0].id,'connection reset',true);
  assert.equal(paused.status,'paused');assert.equal(paused.steps[0].status,'uncertain');
  await assert.rejects(f.tasks.resume(task.id),/Reconcile/);
  const done=await f.tasks.reconcile(task.id,task.steps[0].id,true,'Bank shows payment');
  assert.equal(done.status,'completed');assert.deepEqual(done.steps[0].receipt,{at:1000,result:'Bank shows payment',reconciled:true});
 }finally{await f.close();}
});

test('handoff, cancel and lease renewal follow the rules',async()=>{
 const f=await fixture();try{
  const task=await f.tasks.create({title:'T',steps:[{title:'S'}]});const {leaseId}=await f.tasks.claim(task.id,'server',2000);
  await assert.rejects(f.tasks.handoff(task.id,'phone'),/Pause the task/);
  f.clock.t+=1500;await f.tasks.renew(task.id,leaseId,2000);f.clock.t+=1500;await f.tasks.startStep(task.id,leaseId,task.steps[0].id);
  await f.tasks.cancel(task.id);await assert.rejects(f.tasks.cancel(task.id),/cancelled task cannot become cancelled/);
  assert.equal(f.tasks.get(task.id).steps[0].status,'pending');
  assert.equal((await f.tasks.handoff(task.id,'phone')).owner,'phone');
  await f.tasks.retry(task.id);await f.tasks.claim(task.id,'phone');
  await assert.rejects(f.tasks.create({title:'x',owner:'cloud',steps:[{title:'a'}]}),/phone or server/);
  await assert.rejects(f.tasks.create({title:'x',steps:[]}),/1 to 50 steps/);
 }finally{await f.close();}
});

test('task routes require the pairing token and validate input',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'pocko-task-api-'));
 const runtime=await createApp({stateDir:join(directory,'state'),workspace:join(directory,'workspace'),port:19127});const server=runtime.app.listen(19127,'127.0.0.1');await new Promise<void>(r=>server.on('listening',r));
 const base='http://127.0.0.1:19127/api/tasks',headers={Host:'127.0.0.1:19127',Authorization:'Bearer '+runtime.token,'Content-Type':'application/json'};
 const call=(path:string,body?:unknown)=>fetch(base+path,{method:body===undefined?'GET':'POST',headers,body:body===undefined?undefined:JSON.stringify(body)});
 try{
  assert.equal((await fetch(base,{headers:{Host:headers.Host}})).status,401);
  assert.equal((await call('',{title:'',steps:[]})).status,400);
  const created=await call('',{title:'Check',owner:'phone',steps:[{title:'Look',external:true}]});assert.equal(created.status,201);const task=await created.json();
  assert.equal((await call('/nope')).status,404);
  assert.equal((await call('/'+task.id+'/claim',{owner:'server'})).status,409);
  const {leaseId}=await (await call('/'+task.id+'/claim',{owner:'phone',leaseMs:30000})).json();
  assert.equal((await call('/'+task.id+'/steps/'+task.steps[0].id+'/start',{leaseId})).status,409);
  const asked=await call('/'+task.id+'/steps/'+task.steps[0].id+'/ask',{leaseId,kind:'approval',prompt:'Open the bank app?'});assert.equal(asked.status,201);const request=await asked.json();
  assert.equal((await call('/'+task.id+'/requests/'+request.id,{value:true})).status,200);
  assert.equal((await call('/'+task.id+'/requests/'+request.id,{value:true})).status,409);
  assert.equal((await (await call('')).json())[0].status,'queued');
 }finally{await runtime.close();await new Promise<void>(r=>server.close(()=>r()));await rm(directory,{recursive:true,force:true});}
});
