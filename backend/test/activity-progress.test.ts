import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {newProgress,updateProgress} from '../src/activity-progress.js';
import {Store,defaults} from '../src/storage.js';
import {Sessions} from '../src/sessions.js';
import {Workspace} from '../src/workspace.js';
import {Processes} from '../src/processes.js';
import {providerActivity} from '../src/providers.js';

test('streamed thinking and tool updates merge without duplicating calls or exposing the known key',()=>{
 const progress=newProgress(),redact=(text:string)=>text.replaceAll('private-key','[redacted]');
 updateProgress(progress,{type:'message_update'},redact,{timestamp:1,content:[{type:'thinking',thinking:'Checking private-key'},{type:'text',text:'Working'}]});
 updateProgress(progress,{type:'message_update'},redact,{timestamp:1,content:[{type:'thinking',thinking:'Checking private-key safely'}]});
 updateProgress(progress,{type:'message_update'},redact,{timestamp:2,content:[{type:'thinking',thinking:'Now checking tests'}]});
 updateProgress(progress,{type:'tool_execution_start',toolCallId:'tool-1',toolName:'bash',args:{command:'npm test'}},redact);
 updateProgress(progress,{type:'tool_execution_update',toolCallId:'tool-1',partialResult:'Tests in progress'},redact);
 updateProgress(progress,{type:'tool_execution_end',toolCallId:'tool-1',result:'Passed',isError:false},redact);
 assert.equal(progress.tools.length,1);assert.equal(progress.tools[0].status,'done');assert.equal(progress.tools[0].output,'Passed');assert.match(progress.thinking,/safely\n\nNow checking tests/);assert.doesNotMatch(JSON.stringify(progress),/private-key/);
 const events=providerActivity({type:'item.completed',item:{id:'cmd',type:'command_execution',command:'npm test',aggregated_output:'Failed',exit_code:1}});assert.equal(events[0].isError,true);assert.equal(events[0].toolName,'bash');
});

test('live progress is checkpointed while running and restored as interrupted after a runtime restart',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-progress-'));const store=new Store(join(root,'state'));await store.init();const workspace=new Workspace(join(root,'work'));await workspace.init();
 const provider:any={run:async(_id:string,options:any)=>{options.onText('Partial response');options.onActivity({type:'thinking',id:'summary',text:'Reviewing the project'});options.onActivity({type:'tool_execution_start',toolCallId:'call',toolName:'read_file',args:{path:'index.html'}});return new Promise((_resolve,reject)=>options.signal.addEventListener('abort',()=>reject(Error('Cancelled')),{once:true}));}};
 const settings={...defaults,executionProvider:'codex' as const};const sessions=new Sessions(store,workspace,new Processes(()=>{}),()=>settings,()=>{},undefined,provider);await sessions.init();const session=await sessions.create();sessions.setTrust(session.id,true);
 try{
  await sessions.prompt(session.id,'Inspect the project');await new Promise(r=>setTimeout(r,650));
  assert.equal(sessions.state(session.id).busy,true);assert.equal(sessions.state(session.id).progress?.text,'Partial response');
  const checkpoint=await store.read<any[]>('sessions',[]);assert.equal(checkpoint[0].progress.thinking,'Reviewing the project');assert.equal(checkpoint[0].progress.tools[0].status,'running');
  sessions.stop(session.id);for(let i=0;sessions.anyBusy()&&i<100;i++)await new Promise(r=>setTimeout(r,10));assert.equal(sessions.anyBusy(),false);assert.equal(sessions.state(session.id).streaming.content[0].text,'Partial response');
  await sessions.prompt(session.id,'Second task');assert.equal(sessions.state(session.id).progressHistory?.length,1);assert.equal(sessions.state(session.id).progressHistory?.[0].text,'Partial response');sessions.stop(session.id);for(let i=0;sessions.anyBusy()&&i<100;i++)await new Promise(r=>setTimeout(r,10));
  const restoredStore=new Store(join(root,'restored'));await restoredStore.init();await restoredStore.write('sessions',checkpoint);const restored=new Sessions(restoredStore,workspace,new Processes(()=>{}),()=>settings,()=>{});await restored.init();
  assert.equal(restored.state(session.id).busy,false);assert.equal(restored.state(session.id).progress?.status,'paused');assert.equal(restored.state(session.id).progress?.tools[0].status,'interrupted');assert.equal(restored.state(session.id).streaming.content[0].text,'Partial response');
 }finally{sessions.stopAll();await new Promise(r=>setTimeout(r,550));await rm(root,{recursive:true,force:true});}
});
