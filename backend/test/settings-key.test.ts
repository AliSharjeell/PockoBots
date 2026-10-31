import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createApp} from '../src/server.js';

test('saved-key access requires the owner token and never appears in ordinary state',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'pocko-key-'));
 const runtime=await createApp({stateDir:join(directory,'state'),workspace:join(directory,'workspace'),port:19119});const server=runtime.app.listen(19119,'127.0.0.1');await new Promise<void>(r=>server.on('listening',r));
 const base='http://127.0.0.1:'+(server.address() as any).port;
 const headers={Host:'127.0.0.1:19119',Authorization:'Bearer '+runtime.token,'Content-Type':'application/json'};
 try{
  assert.equal((await fetch(base+'/api/settings/key',{headers:{Host:headers.Host}})).status,401);
  assert.equal((await fetch(base+'/api/settings',{method:'PUT',headers,body:JSON.stringify({apiKey:'fixture-private-key'})})).status,200);
  const key=await fetch(base+'/api/settings/key',{headers});assert.equal(key.headers.get('cache-control'),'no-store');assert.deepEqual(await key.json(),{apiKey:'fixture-private-key'});
  const state=await (await fetch(base+'/api/state',{headers})).text();assert.doesNotMatch(state,/fixture-private-key/);assert.match(state,/"hasKey":true/);
 }finally{await runtime.close();await new Promise<void>(r=>server.close(()=>r()));await rm(directory,{recursive:true,force:true});}
});
