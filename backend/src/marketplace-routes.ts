import type {Express} from "express";
import type {Connectors} from "./connectors.js";
import {installMarketplace,listMarketplace} from "./marketplace.js";

/** Register before the bearer-auth middleware. One-time OAuth state authenticates this callback. */
export function registerMarketplaceCallback(app:Express,connectors:Connectors,port:number){
 app.get('/api/oauth/mcp/callback',async(req,res)=>{
  res.setHeader('Cache-Control','no-store');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('Content-Security-Policy',"default-src 'none'");
  if(![`127.0.0.1:${port}`,`localhost:${port}`].includes(req.headers.host||'')){res.status(403).type('text').send('Invalid callback host');return;}
  try{await connectors.finishOAuth(req.query.state,req.query.code);res.type('text').send('Authorization complete. Return to PockoBots and test this connection.');}
  catch{res.status(400).type('text').send('Authorization could not be completed. Return to PockoBots and start sign-in again.');}
 });
}
/** Register after the existing bearer-auth middleware. Installing config does not claim tool connectivity. */
export function registerMarketplaceApi(app:Express,connectors:Connectors,busy:()=>boolean=()=>false){
 const idle=()=>{if(busy())throw Error('Pause agents before changing integrations');};
 app.get('/api/marketplace',(_req,res)=>res.json(listMarketplace(connectors)));
 app.post('/api/marketplace/:id/install',async(req,res)=>{idle();res.status(201).json(await installMarketplace(connectors,String(req.params.id),req.body));});
 app.post('/api/connectors/:id/oauth/start',async(req,res)=>{idle();res.json(await connectors.startOAuth(String(req.params.id)));});
}
