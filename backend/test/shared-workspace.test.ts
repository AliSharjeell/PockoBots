import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {Store} from '../src/storage.js';
import {Workspace} from '../src/workspace.js';
import {Projects} from '../src/projects.js';
import {sharedWorkspaceTool} from '../src/shared-workspace.js';
test('bots share files without relocating private projects and conflicting edits are detected',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-shared-'));const store=new Store(join(root,'state'));await store.init();const workspace=new Workspace(join(root,'projects'));await workspace.init();const projects=new Projects(store,workspace);await projects.init();
 try{
  const privateProject=await projects.create('private-bot');await (await projects.workspace(privateProject.id)).write('keep.txt','private');
  const a=sharedWorkspaceTool(projects,async()=>{}),b=sharedWorkspaceTool(projects,async()=>{});
  await a.execute('a',{action:'write',path:'joint/index.html',content:'initial\n'});
  const read=await b.execute('b',{action:'read',path:'joint/index.html'});const value=JSON.parse((read.content[0] as any).text);assert.equal(value.content,'initial\n');
  const edits=await Promise.allSettled([a.execute('a',{action:'write',path:'joint/index.html',content:'first\n',expectedRevision:value.revision}),b.execute('b',{action:'write',path:'joint/index.html',content:'second\n',expectedRevision:value.revision})]);assert.equal(edits.filter(e=>e.status==='fulfilled').length,1);assert.equal(edits.filter(e=>e.status==='rejected').length,1);
  assert.equal(await (await projects.workspace(privateProject.id)).read('keep.txt'),'private');assert.match(await (await projects.workspace('shared')).read('joint/index.html'),/first|second/);
  await assert.rejects(a.execute('escape',{action:'write',path:'../../state/secret',content:'no'}),/outside workspace/);
 }finally{await rm(root,{recursive:true,force:true});}
});
