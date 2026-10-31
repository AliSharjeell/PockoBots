import type {Express} from "express";
import type {Connectors} from "./connectors.js";
import {installMarketplace,listMarketplace} from "./marketplace.js";

/** Register before the bearer-auth middleware. One-time OAuth state authenticates this callback. */
export function registerMarketplaceCallback(app:Express,connectors:Connectors,port:number){
 app.get('/api/oauth/mcp/callback',async(req,res)=>{
  res.setHeader('Cache-Control','no-store');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('Content-Security-Policy',"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'");
  if(![`127.0.0.1:${port}`,`localhost:${port}`].includes(req.headers.host||'')){res.status(403).type('text').send('Invalid callback host');return;}
  try{
   const result=await connectors.finishOAuth(req.query.state,req.query.code);
   let ready=false;try{await connectors.inspect(result.id);ready=true;}catch{}
   res.type('html').send(`<!doctype html><html lang="en"><meta name="viewport" content="width=device-width,initial-scale=1"><title>PockoBots</title><style>body{margin:0;background:#09090b;color:#fff;font:16px system-ui;letter-spacing:-.06em;display:grid;place-items:center;min-height:100vh}main{padding:32px;max-width:420px}h1{font-size:32px;margin:0 0 12px}p{color:#a1a1aa;line-height:1.5}a{display:block;margin-top:28px;padding:16px 24px;background:#fff;color:#09090b;text-align:center;border-radius:28px;text-decoration:none;font-weight:700}</style><main><h1>You're signed in.</h1><p>${ready?"Your connection is ready for your bots.":"Your account is authorized. Open the app to finish connecting or review its settings."}</p><a href="pockobots://oauth/complete">Return to PockoBots</a></main></html>`);
  }
  catch{res.status(400).type('text').send('Authorization could not be completed. Return to PockoBots and start sign-in again.');}
 });
}
/** Register after the existing bearer-auth middleware. Installing config does not claim tool connectivity. */
export function registerMarketplaceApi(app:Express,connectors:Connectors,busy:()=>boolean=()=>false){
 const idle=()=>{if(busy())throw Error('Pause agents before changing integrations');};
 app.post('/api/github/cli/setup',async(_req,res)=>{idle();res.json(await connectors.prepareGitHubCli());});
 app.get('/api/marketplace',(_req,res)=>res.json(listMarketplace(connectors)));
 app.post('/api/marketplace/:id/install',async(req,res)=>{idle();res.status(201).json(await installMarketplace(connectors,String(req.params.id),req.body));});
 app.get('/api/connectors/:id/oauth/status',(req,res)=>res.json(connectors.githubStatus(String(req.params.id))));
 app.post('/api/connectors/:id/oauth/cancel',(req,res)=>res.json(connectors.cancelGitHub(String(req.params.id))));
 app.post('/api/connectors/:id/oauth/start',async(req,res)=>{idle();res.json(await connectors.startOAuth(String(req.params.id)));});
}
