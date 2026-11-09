import {test} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {mkdtemp,rm,writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {Store,defaults} from '../src/storage.js';
import {Workspace} from '../src/workspace.js';
import {Sessions} from '../src/sessions.js';
import {Processes} from '../src/processes.js';
import {cardTools,type Card} from '../src/cards.js';
import {createApp} from '../src/server.js';
import type {JevOutcome} from '../src/jev.js';

const choices={title:'Where should it run?',control:'clarification',options:[{label:'Phone'},{label:'Cloud'},{label:'Both'}]};
const result=(value:any)=>JSON.parse(value.content[0].text);

async function tools(jev?:(input:any)=>Promise<JevOutcome>){
 const root=await mkdtemp(join(tmpdir(),'pocko-cards-'));const workspace=new Workspace(root);await workspace.init();
 const cards:Card[]=[];const [present,show]=cardTools({workspace,add:async card=>{cards.push(card);},jev:jev&&{decide:jev}});
 return {cards,workspace,present:(input:any)=>present.execute('call',input),show:(input:any)=>show.execute('call',input),close:()=>rm(root,{recursive:true,force:true})};
}

test('a choice card is plain validated data with ids by position',async()=>{
 const t=await tools();
 try{
  const shown=result(await t.present(choices));
  assert.equal(shown.shown,true);assert.deepEqual(shown.options.map((o:any)=>o.id),['opt-0','opt-1','opt-2']);
  assert.equal(t.cards.length,1);const card:any=t.cards[0];
  assert.deepEqual(Object.keys(card).sort(),['created','id','options','status','title','type']);
  assert.equal(card.status,'open');assert.equal(card.type,'clarification');
 }finally{await t.close();}
});

test('the schema refuses executable UI and anything outside the approved types',async()=>{
 const t=await tools();
 try{
  const refused=[
   {...choices,html:'<script>alert(1)</script>'},
   {...choices,control:'form'},
   {...choices,options:[{label:'A',onClick:'run()'}]},
   {...choices,options:[]},
   {...choices,options:Array.from({length:13},(_,i)=>({label:'o'+i}))},
   {...choices,options:[{label:'Same'},{label:'same'}]},
   {...choices,options:[{label:'   '}]},
   {...choices,options:[{label:'A',sources:[{title:'x',url:'javascript:alert(1)'}]}]},
   {...choices,options:[{label:'A',sources:[{title:'x',url:'https://user:pw@example.com'}]}]},
   {...choices,control:'comparison',options:[{label:'A',details:['d']}]},
   {...choices,control:'comparison',options:[{label:'A',sources:[{title:'x',url:'https://example.com'}]}]},
  ];
  for(const input of refused)await assert.rejects(t.present(input),undefined,JSON.stringify(input));
  for(const input of [{type:'widget',title:'x'},{type:'progress',title:'x',percent:101},{type:'progress',title:'x',script:'x'},{type:'approval',title:'Delete files'},{type:'file',title:'x'},{type:'progress',title:'x',path:'a'},{type:'approval',title:'x',body:'y',percent:3}])
   await assert.rejects(t.show(input),undefined,JSON.stringify(input));
  assert.equal(t.cards.length,0);
 }finally{await t.close();}
});

test('progress, file and approval cards are read-only and a file must be in the project',async()=>{
 const t=await tools();
 try{
  await writeFile(join(t.workspace.root,'notes.md'),'hi');
  await t.show({type:'progress',title:'Building',percent:40});
  await t.show({type:'file',title:'Notes',path:'./notes.md'});
  await t.show({type:'approval',title:'Push to main',body:'The agent will ask before pushing.'});
  await assert.rejects(t.show({type:'file',title:'Escape',path:'../outside.txt'}));
  await assert.rejects(t.show({type:'file',title:'Missing',path:'missing.md'}));
  assert.deepEqual(t.cards.map((c:any)=>[c.type,c.path??c.percent??c.body]),[['progress',40],['file','notes.md'],['approval','The agent will ask before pushing.']]);
 }finally{await t.close();}
});

test('Jev only orders a card or hands back to prose, and its failure keeps the card',async()=>{
 const seen:any[]=[];
 const ranked=await tools(async input=>{seen.push(input);return {used:true,source:'live',control:'clarification',order:[2,0,1]};});
 const prose=await tools(async()=>({used:true,source:'sample',control:'agent',order:[0,1,2]}));
 const failed=await tools(async()=>({used:false,reason:'timeout'}));
 try{
  await ranked.present({...choices,context:'The owner wants it offline'});
  assert.deepEqual((ranked.cards[0] as any).options.map((o:any)=>o.label),['Both','Phone','Cloud']);
  assert.deepEqual((ranked.cards[0] as any).options.map((o:any)=>o.id),['opt-2','opt-0','opt-1']);
  assert.equal((ranked.cards[0] as any).rankedBy,'live');
  // Only the agent's context and options are sent, and the allowed controls never include an action.
  assert.deepEqual(Object.keys(seen[0]).sort(),['allowedControls','context','options']);
  assert.deepEqual(seen[0].allowedControls,['clarification','agent']);
  assert.equal(result(await prose.present(choices)).shown,false);assert.equal(prose.cards.length,0);
  assert.equal(result(await failed.present(choices)).shown,true);
  assert.deepEqual((failed.cards[0] as any).options.map((o:any)=>o.id),['opt-0','opt-1','opt-2']);assert.equal((failed.cards[0] as any).rankedBy,undefined);
 }finally{await ranked.close();await prose.close();await failed.close();}
});

async function sessionFixture(){
 const root=await mkdtemp(join(tmpdir(),'pocko-cards-'));const store=new Store(join(root,'state'));await store.init();const workspace=new Workspace(join(root,'work'));await workspace.init();
 // A closed local port, so a prompt in these tests never reaches a real provider.
 const settings={...defaults,api:'openai-completions' as const,baseUrl:'http://127.0.0.1:9',apiKey:'fixture-key'};
 const make=async()=>{const s=new Sessions(store,workspace,new Processes(()=>{}),()=>settings,()=>{});await s.init();return s;};
 const sessions=await make();const {id}=await sessions.create();
 const add=async(s:Sessions,input:any,kind:'present'|'show'='present')=>{const [present,show]=(s as any).cardTools((s as any).records.get(id),workspace);return result(await (kind==='present'?present:show).execute('call',input));};
 return {root,store,sessions,id,add,make,close:()=>rm(root,{recursive:true,force:true})};
}

test('cards survive a restart and a selection is checked, claimed once and handed to the agent',async()=>{
 const f=await sessionFixture();
 try{
  const first=await f.add(f.sessions,choices);
  const approval=await f.add(f.sessions,{type:'approval',title:'Deploy',body:'Needs approval'},'show');
  const restarted=await f.make();
  assert.deepEqual(restarted.cards(f.id).map(c=>c.id),[first.cardId,approval.cardId]);
  const delivered:string[]=[];const deliver=async(text:string)=>{delivered.push(text);return 'started';};
  await assert.rejects(restarted.selectCard(f.id,'no-such-card','opt-0',deliver),(e:any)=>e.status===404);
  await assert.rejects(restarted.selectCard('no-such-session',first.cardId,'opt-0',deliver),(e:any)=>e.status===404);
  await assert.rejects(restarted.selectCard(f.id,first.cardId,'opt-9',deliver),(e:any)=>e.status===400);
  await assert.rejects(restarted.selectCard(f.id,approval.cardId,'opt-0',deliver),(e:any)=>e.status===409);
  const answered=await restarted.selectCard(f.id,first.cardId,'opt-1',deliver);
  assert.equal(answered.delivery,'started');assert.equal((answered.card as any).selectedOptionId,'opt-1');
  assert.equal(delivered.length,1);assert.match(delivered[0],/selected opt-1/);assert.match(delivered[0],/"Cloud"/);assert.match(delivered[0],/approves nothing/);
  await assert.rejects(restarted.selectCard(f.id,first.cardId,'opt-1',deliver),(e:any)=>e.status===409&&/already/.test(e.message));
  await assert.rejects(restarted.selectCard(f.id,first.cardId,'opt-0',deliver),(e:any)=>e.status===409);
  assert.equal(delivered.length,1);
  // A card grants nothing: no trust and no approval appear.
  assert.equal(restarted.state(f.id).trusted,false);assert.deepEqual(restarted.state(f.id).approvals,[]);
  const again=await f.make();assert.equal((again.cards(f.id)[0] as any).status,'selected');
 }finally{await f.close();}
});

test('a newer card or a new turn makes earlier choices stale, and a failed delivery reopens the card',async()=>{
 const f=await sessionFixture();
 try{
  const deliver=async()=>'started';
  const older=await f.add(f.sessions,choices);const newer=await f.add(f.sessions,{...choices,title:'Again'});
  await assert.rejects(f.sessions.selectCard(f.id,older.cardId,'opt-0',deliver),(e:any)=>e.status===409&&/out of date/.test(e.message));
  await assert.rejects(f.sessions.selectCard(f.id,newer.cardId,'opt-0',async()=>{throw Error('Agent is already running');}),/already running/);
  assert.equal((f.sessions.cards(f.id).find(c=>c.id===newer.cardId) as any).status,'open');
  // Two concurrent selections: only the first claim passes.
  const both=await Promise.allSettled([f.sessions.selectCard(f.id,newer.cardId,'opt-0',deliver),f.sessions.selectCard(f.id,newer.cardId,'opt-1',deliver)]);
  assert.deepEqual(both.map(r=>r.status),['fulfilled','rejected']);
  const last=await f.add(f.sessions,{...choices,title:'Third'});
  // A new owner turn retires open choices; the run itself then fails against the closed port.
  await f.sessions.prompt(f.id,'Never mind');
  const end=Date.now()+10000;while(f.sessions.anyBusy()&&Date.now()<end)await new Promise(r=>setTimeout(r,20));
  await assert.rejects(f.sessions.selectCard(f.id,last.cardId,'opt-0',deliver),(e:any)=>e.status===409);
  f.sessions.stopAll();
 }finally{await new Promise(r=>setTimeout(r,600));await f.close();}
});

test('over HTTP, a model-made card replays, takes one selection and the agent receives it',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'pocko-cards-http-'));const bodies:any[]=[];
 const model=http.createServer((req,res)=>{let raw='';req.on('data',c=>raw+=c);req.on('end',()=>{const body=JSON.parse(raw);bodies.push(body);res.setHeader('Content-Type','text/event-stream');
  const emit=(delta:any,finish:any=null)=>res.write('data: '+JSON.stringify({id:'f',object:'chat.completion.chunk',created:1,model:'fixture',choices:[{index:0,delta,finish_reason:finish}]})+'\n\n');
  if(bodies.length===1){emit({role:'assistant',tool_calls:[{index:0,id:'call_1',type:'function',function:{name:'present_choices',arguments:JSON.stringify(choices)}}]});emit({},'tool_calls');}
  else{emit({role:'assistant',content:bodies.length===2?'Pick one above.':'Going with your choice.'});emit({},'stop');}
  res.end('data: [DONE]\n\n');});});
 await new Promise<void>(r=>model.listen(0,'127.0.0.1',r));
 const port=19127;const runtime=await createApp({stateDir:join(directory,'state'),workspace:join(directory,'work'),port});const server=runtime.app.listen(port,'127.0.0.1');await new Promise<void>(r=>server.on('listening',r));
 const base='http://127.0.0.1:'+port,headers={Host:'127.0.0.1:'+port,Authorization:'Bearer '+runtime.token,'Content-Type':'application/json'};
 const call=(path:string,init:any={})=>fetch(base+path,{...init,headers:{...headers,...init.headers}});
 const idle=async()=>{const end=Date.now()+10000;while(runtime.sessions.anyBusy()&&Date.now()<end)await new Promise(r=>setTimeout(r,20));};
 try{
  assert.equal((await call('/api/settings',{method:'PUT',body:JSON.stringify({provider:'openai',api:'openai-completions',baseUrl:'http://127.0.0.1:'+(model.address() as any).port,model:'fixture',apiKey:'fixture-model-key'})})).status,200);
  const {id}=await (await call('/api/sessions',{method:'POST',body:'{}'})).json();
  assert.equal((await fetch(base+'/api/sessions/'+id+'/cards',{headers:{Host:headers.Host}})).status,401);
  assert.equal((await fetch(base+'/api/sessions/'+id+'/cards/x/select',{method:'POST',headers:{Host:headers.Host,'Content-Type':'application/json'},body:'{"optionId":"opt-0"}'})).status,401);
  assert.equal((await call('/api/sessions/'+id+'/chat',{method:'POST',body:JSON.stringify({text:'Set up hosting'})})).status,202);
  await idle();
  const cards=await (await call('/api/sessions/'+id+'/cards')).json();
  assert.equal(cards.length,1);assert.equal(cards[0].status,'open');
  assert.equal((await call('/api/sessions/missing/cards')).status,404);
  for(const body of ['{}','{"optionId":5}',JSON.stringify({optionId:'x'.repeat(65)})])assert.equal((await call('/api/sessions/'+id+'/cards/'+cards[0].id+'/select',{method:'POST',body})).status,400);
  const selected=await call('/api/sessions/'+id+'/cards/'+cards[0].id+'/select',{method:'POST',body:JSON.stringify({optionId:'opt-2'})});
  assert.equal(selected.status,202);assert.equal((await selected.json()).delivery,'started');
  await idle();
  assert.equal(bodies.length,3);assert.match(JSON.stringify(bodies[2].messages.at(-1)),/selected opt-2/);
  const repeated=await call('/api/sessions/'+id+'/cards/'+cards[0].id+'/select',{method:'POST',body:JSON.stringify({optionId:'opt-2'})});
  assert.equal(repeated.status,409);
  assert.equal((await call('/api/settings/jev')).status,200);
  assert.equal((await call('/api/settings/jev',{method:'PUT',body:JSON.stringify({mode:'live',apiKey:'fixture-typesafe-key'})})).status,200);
  const state=await (await call('/api/state')).text();const jevView=await (await call('/api/settings/jev')).text();
  assert.doesNotMatch(state+jevView,/fixture-typesafe-key/);assert.match(jevView,/"hasKey":true/);
  assert.equal((await call('/api/settings/jev',{method:'PUT',body:JSON.stringify({mode:'nope'})})).status,400);
 }finally{await runtime.close();server.closeAllConnections();await new Promise<void>(r=>server.close(()=>r()));model.closeAllConnections();await new Promise<void>(r=>model.close(()=>r()));await new Promise(r=>setTimeout(r,600));await rm(directory,{recursive:true,force:true});}
});
