import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import http from 'node:http';
import express from 'express';
import {registerMarketplaceCallback,registerMarketplaceApi} from '../src/marketplace-routes.js';
import {Store} from '../src/storage.js';
import {Connectors} from '../src/connectors.js';
import {marketplaceCatalog,installMarketplace,listMarketplace} from '../src/marketplace.js';

test('marketplace contains all 30 reference vendors and Google Workspace with honest setup states',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'pocko-market-'));const store=new Store(directory);await store.init();const connectors=new Connectors(store);await connectors.init();
 try{assert.equal(marketplaceCatalog.length,31);assert.equal(new Set(marketplaceCatalog.map(e=>e.id)).size,31);assert.equal(listMarketplace(connectors).every(e=>e.status==='not_configured'),true);
 await assert.rejects(installMarketplace(connectors,'paper'),/desktop host/);
 await assert.rejects(installMarketplace(connectors,'google-workspace'),/OAuth client ID/);
 const result=await installMarketplace(connectors,'google-workspace',{credentials:{'google-client-id':'private-google-client','google-client-secret':'private-google-secret'}});assert.equal(result.status,'configured');assert.equal(result.connection.command,'uvx');assert.equal(JSON.stringify(connectors.list()).includes('private-google'),false);assert.equal(connectors.redact('private-google-secret'),'[redacted]');
 const second=await installMarketplace(connectors,'google-workspace',{credentials:{'google-client-id':'private-google-client','google-client-secret':'private-google-secret'}});assert.equal(result.connection.id,second.connection.id);assert.equal(listMarketplace(connectors).find(e=>e.id==='google-workspace')?.status,'configured');
 const headerConnection=await connectors.save({name:'Custom headers',transport:'http',url:'https://example.com/mcp',headers:{'x-api-key':'private-header'}});assert.equal(JSON.stringify(connectors.list()).includes('private-header'),false);const moved=await connectors.save({id:headerConnection.id,name:'Custom headers',transport:'http',url:'https://other.example/mcp'});assert.deepEqual(moved.headerKeys,[]);
 await assert.rejects(connectors.save({name:'Bad',transport:'stdio',command:'node',env:{'INVALID-NAME':'secret'}}),/Invalid secret/);
 }finally{await connectors.close();await rm(directory,{recursive:true,force:true});}
});

test('MCP OAuth performs PKCE browser handoff, stores tokens privately and rejects replayed state',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'pocko-oauth-'));const store=new Store(directory);await store.init();let base='',exchanges=0;
 const server=http.createServer(async(req,res)=>{res.setHeader('Content-Type','application/json');const path=req.url?.split('?')[0]||'';
 if(path.startsWith('/.well-known/oauth-protected-resource'))return res.end(JSON.stringify({resource:base+'/mcp',authorization_servers:[base]}));
 if(path.startsWith('/.well-known/oauth-authorization-server')||path.startsWith('/.well-known/openid-configuration'))return res.end(JSON.stringify({issuer:base,authorization_endpoint:base+'/authorize',token_endpoint:base+'/token',registration_endpoint:base+'/register',response_types_supported:['code'],grant_types_supported:['authorization_code','refresh_token'],code_challenge_methods_supported:['S256'],token_endpoint_auth_methods_supported:['none']}));
 if(path==='/register')return res.end(JSON.stringify({client_id:'fixture-client',redirect_uris:[base+'/callback'],grant_types:['authorization_code','refresh_token'],response_types:['code'],token_endpoint_auth_method:'none'}));
 if(path==='/token'){let body='';for await(const chunk of req)body+=chunk;const params=new URLSearchParams(body);assert.ok(params.get('code_verifier'));assert.equal(params.get('code'),'owner-approved');exchanges++;return res.end(JSON.stringify({access_token:'private-access-token',refresh_token:'private-refresh-token',token_type:'Bearer',expires_in:3600}));}
 res.statusCode=404;res.end('{}');});await new Promise<void>(r=>server.listen(0,'127.0.0.1',r));base='http://127.0.0.1:'+(server.address() as any).port;
 const connectors=new Connectors(store,base+'/callback');await connectors.init();
 try{const connection=await connectors.save({name:'OAuth fixture',transport:'http',url:base+'/mcp',oauth:true});const start=await connectors.startOAuth(connection.id);assert.equal(start.status,'authorization_required');const authorization=new URL(start.authorizationUrl!);assert.equal(authorization.searchParams.get('code_challenge_method'),'S256');const state=authorization.searchParams.get('state');assert.ok(state&&state.length>30);
 await assert.rejects(connectors.finishOAuth('wrong','owner-approved'),/state/);assert.equal(exchanges,0);const finish=await connectors.finishOAuth(state,'owner-approved');assert.equal(finish.status,'authorized');assert.equal(exchanges,1);await assert.rejects(connectors.finishOAuth(state,'owner-approved'),/state/);assert.equal(exchanges,1);
 assert.equal(JSON.stringify(connectors.list()).includes('private-access-token'),false);assert.equal(connectors.redact('private-access-token private-refresh-token'),'[redacted] [redacted]');assert.equal(connectors.list()[0].authStatus,'authorized');
 const persisted=JSON.parse(await readFile(join(directory,'connectors.json'),'utf8'));assert.equal(persisted[0].oauthData.tokens.access_token,'private-access-token');assert.equal(persisted[0].oauthData.verifier,undefined);
 }finally{await connectors.close();server.closeAllConnections();await new Promise<void>(r=>server.close(()=>r()));await rm(directory,{recursive:true,force:true});}
});


test('marketplace mutations stay behind bearer auth and callback rejects foreign hosts',async()=>{
 const directory=await mkdtemp(join(tmpdir(),'pocko-market-routes-'));const store=new Store(directory);await store.init();const connectors=new Connectors(store);await connectors.init();const app=express();app.use(express.json());const server=http.createServer(app);await new Promise<void>(r=>server.listen(0,'127.0.0.1',r));const port=(server.address() as any).port;
 registerMarketplaceCallback(app,connectors,port);app.use((req,res,next)=>{if(req.headers.authorization!=='Bearer fixture'){res.status(401).json({error:'unauthorized'});return;}next();});registerMarketplaceApi(app,connectors);app.use((error:any,_req:any,res:any,_next:any)=>res.status(400).json({error:String(error.message)}));
 try{const base='http://127.0.0.1:'+port;
 assert.equal((await fetch(base+'/api/marketplace')).status,401);
 const allowed=await fetch(base+'/api/marketplace',{headers:{Authorization:'Bearer fixture'}});assert.equal(allowed.status,200);assert.equal(((await allowed.json()) as any[]).length,31);
 const foreignStatus=await new Promise<number>((resolve,reject)=>{const request=http.get(base+'/api/oauth/mcp/callback?state=bad&code=bad',{headers:{Host:'foreign.example'}},response=>{response.resume();resolve(response.statusCode!);});request.on('error',reject);});assert.equal(foreignStatus,403);
 const callback=await fetch(base+'/api/oauth/mcp/callback?state=bad&code=bad');assert.equal(callback.status,400);assert.match(await callback.text(),/Authorization could not/);assert.equal(callback.headers.get('cache-control'),'no-store');
 const result=await fetch(base+'/api/marketplace/notion/install',{method:'POST',headers:{Authorization:'Bearer fixture','Content-Type':'application/json'},body:'{}'});assert.equal(result.status,201);assert.equal(((await result.json()) as any).status,'configured');
 }finally{await connectors.close();server.closeAllConnections();await new Promise<void>(r=>server.close(()=>r()));await rm(directory,{recursive:true,force:true});}
});
