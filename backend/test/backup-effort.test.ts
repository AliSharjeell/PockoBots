import {test} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {mkdtemp,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {BackupModel} from '../src/backup-model.js';
import {Store,defaults} from '../src/storage.js';
import {connection} from '../src/models.js';
import {createApp} from '../src/server.js';

test('backup switching restores the primary connection, survives restart and never exposes the backup key',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-backup-'));const store=new Store(root);await store.init();let backup=new BackupModel(store);await backup.init();
 const primary={...defaults,executionProvider:'codex' as const,apiKey:'primary-fixture-key'};
 try{
  await assert.rejects(backup.toggle(true),/No backup/);
  await backup.configure({...defaults,provider:'anthropic',api:'anthropic-messages',baseUrl:'https://api.minimax.io/anthropic',model:'MiniMax-M3.1-Flash[1m]',apiKey:'backup-fixture-key'});
  assert.equal(backup.current(primary),primary);await backup.toggle(true);assert.equal(backup.current(primary).executionProvider,'byok');
  assert.equal(backup.current(primary).apiKey,'backup-fixture-key');assert.doesNotMatch(JSON.stringify(backup.view(primary)),/fixture-key/);
  assert.equal(backup.redact('Error backup-fixture-key'),'Error [redacted]');
  backup=new BackupModel(store);await backup.init();assert.equal(backup.view(primary).backupEnabled,true);
  await backup.toggle(false);assert.equal(backup.current(primary),primary);
 }finally{await rm(root,{recursive:true,force:true});}
});

test('MiniMax sends all five effort levels using adaptive thinking and strips the CLI context suffix',async()=>{
 const bodies:any[]=[];const server=http.createServer(async(req,res)=>{let raw='';for await(const part of req)raw+=part;const body=JSON.parse(raw);bodies.push(body);
  res.setHeader('Content-Type','text/event-stream');const emit=(name:string,data:any)=>res.write('event: '+name+'\ndata: '+JSON.stringify({type:name,...data})+'\n\n');
  emit('message_start',{message:{id:'fixture',type:'message',role:'assistant',model:body.model,content:[],usage:{input_tokens:1,output_tokens:0}}});
  emit('content_block_start',{index:0,content_block:{type:'text',text:''}});emit('content_block_delta',{index:0,delta:{type:'text_delta',text:'OK'}});emit('content_block_stop',{index:0});emit('message_delta',{delta:{stop_reason:'end_turn',stop_sequence:null},usage:{output_tokens:1}});emit('message_stop',{});res.end();
 });await new Promise<void>(r=>server.listen(0,'127.0.0.1',r));
 try{for(const effort of ['low','medium','high','xhigh','max'] as const){
  const {model,models}=connection({...defaults,provider:'anthropic',api:'anthropic-messages',baseUrl:'http://127.0.0.1:'+(server.address() as any).port,model:'MiniMax-M3.1-Flash[1m]',apiKey:'fixture-key',contextWindow:1000000});
  const result=await models.completeSimple(model,{messages:[{role:'user',content:'Hello',timestamp:Date.now()}]},{reasoning:effort});
  assert.equal(result.stopReason,'stop',result.errorMessage);assert.equal(bodies.at(-1).model,'MiniMax-M3.1-Flash');assert.equal(bodies.at(-1).output_config.effort,effort);assert.equal(bodies.at(-1).thinking.type,'adaptive');
 }}finally{await new Promise<void>(r=>server.close(()=>r()));}
});

test('backup provisioning requires owner authentication and ordinary state omits the key',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-backup-api-'));const port=19128;const runtime=await createApp({stateDir:join(root,'state'),workspace:join(root,'files'),port});const server=runtime.app.listen(port,'127.0.0.1');await new Promise<void>(r=>server.on('listening',r));
 const base='http://127.0.0.1:'+port,headers={'Content-Type':'application/json',Authorization:'Bearer '+runtime.token};
 try{
  assert.equal((await fetch(base+'/api/settings/backup',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'})).status,401);
  assert.equal((await fetch(base+'/api/settings/backup',{method:'POST',headers,body:JSON.stringify({...defaults,apiKey:'private-backup-key'})})).status,200);
  const enabled=await fetch(base+'/api/settings/backup',{method:'PUT',headers,body:'{"enabled":true}'});assert.equal((await enabled.json() as any).backupEnabled,true);
  const state=await (await fetch(base+'/api/state',{headers})).text();assert.match(state,/"hasActiveKey":true/);assert.doesNotMatch(state,/private-backup-key/);
 }finally{await runtime.close();await new Promise<void>(r=>server.close(()=>r()));await rm(root,{recursive:true,force:true});}
});
