import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {Store} from '../src/storage.js';
import {Connectors} from '../src/connectors.js';
import {integrationTools} from '../src/integration-tools.js';

test('a bot can install a global MCP, another bot discovers its schema and calls it after restart',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-global-mcp-'));const store=new Store(root);await store.init();let connectors=new Connectors(store);await connectors.init();
 try{
  const tool=(name:string)=>integrationTools(connectors).find(t=>t.name===name)!;
  const config={command:process.execPath,args:[fileURLToPath(new URL('./fixtures/mcp-stdio.mjs',import.meta.url))],env:{PRIVATE_TEST_KEY:'private-mcp-key'}};
  await tool('add_integration').execute('install',{name:'Shared notes',config});
  await tool('add_integration').execute('install-again',{name:'Shared notes',config});assert.equal(connectors.list().length,1);
  await connectors.close();connectors=new Connectors(store);await connectors.init();
  const inventory=await tool('list_integrations').execute('other-bot',{});assert.match(JSON.stringify(inventory),/Shared notes/);assert.doesNotMatch(JSON.stringify(inventory),/private-mcp-key/);
  const id=connectors.list()[0].id;
  const schemas=await tool('integration_tools').execute('schema',{connection_id:id,query:'note'});assert.match(JSON.stringify(schemas),/local_note/);
  const result=await tool('call_integration_tool').execute('call',{connection_id:id,tool:'local_note',arguments:{}});assert.match(JSON.stringify(result),/stdio transport ran/);
  await connectors.save({...connectors.list()[0],enabled:false});await assert.rejects(tool('call_integration_tool').execute('disabled',{connection_id:id,tool:'local_note',arguments:{}}),/disabled/);
 }finally{await connectors.close();await rm(root,{recursive:true,force:true});}
});
test('Supabase project URLs are supported, while secrets in URLs and sign-in bypass are rejected',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-supabase-'));const store=new Store(root);await store.init();const connectors=new Connectors(store);await connectors.init();
 try{
  const tool=integrationTools(connectors).find(t=>t.name==='add_integration')!;
  await tool.execute('install',{name:'Supabase',config:{url:'https://mcp.supabase.com/mcp?project_ref=my-project',oauth:true}});
  const saved=connectors.list()[0];assert.equal(saved.authStatus,'sign_in_required');await assert.rejects(connectors.discover(saved.id),/Sign in/);
  await assert.rejects(tool.execute('unsafe',{name:'Secret in URL',config:{url:'https://example.org/mcp?access_token=private'}}),/secret query/);
 }finally{await connectors.close();await rm(root,{recursive:true,force:true});}
});
