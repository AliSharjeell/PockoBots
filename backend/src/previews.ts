import {randomUUID} from 'node:crypto';
import {Type} from 'typebox';
import type {AgentTool} from '@earendil-works/pi-agent-core';
import type {Store} from './storage.js';

type Preview={id:string;botId:string;url:string;refresh:boolean;created:number;status:'queued'|'opened'|'failed';error?:string};
/** Native URL hand-off: no accessibility task or screen-control model is needed. */
export class Previews {
 private records:Preview[]=[];
 constructor(private store:Store){}
 async init(){this.records=await this.store.read<Preview[]>('previews',[]);}
 pending(){return this.records.find(p=>p.status==='queued'&&Date.now()-p.created<120000)||null;}
 async request(botId:string,target:unknown,refresh=false){
  if(typeof target!=='string'||target.length>4000)throw Error('Enter a preview URL');
  const url=new URL(target);if(!['http:','https:'].includes(url.protocol)||url.username||url.password)throw Error('Preview URLs must use HTTP/HTTPS without credentials');
  const item:Preview={id:randomUUID(),botId,url:url.href,refresh,created:Date.now(),status:'queued'};
  this.records=this.records.filter(p=>Date.now()-p.created<120000||p.status!=='queued').slice(-31);this.records.push(item);await this.store.write('previews',this.records);return item;
 }
 async acknowledge(id:string,opened:boolean,error?:string){const item=this.records.find(p=>p.id===id);if(!item||item.status!=='queued')throw Error('Preview request expired');item.status=opened?'opened':'failed';item.error=opened?undefined:String(error||'Android could not open the preview').slice(0,500);await this.store.write('previews',this.records);return item;}
 tool(botId:string):AgentTool<any>{return {name:'open_preview',label:'Open webapp preview',description:'When the owner explicitly asks to open/take them to a webapp or site, open its verified HTTP/HTTPS URL directly in Chrome on Android. No in-app preview, Chrome manual typing, accessibility task or use_phone is needed. Start the server and verify its URL first. refresh=true reloads the existing preview after edits; prefer dev-server hot reload for immediate changes. The floating bot remains available for follow-up edits if overlay permission is enabled. Only call for an owner-requested preview or site opening, never optional visual testing.',parameters:Type.Object({url:Type.String({maxLength:4000}),refresh:Type.Optional(Type.Boolean())}),execute:async(_id,p:any,signal)=>{
  if(signal?.aborted)throw Error('Opening cancelled');
  const item=await this.request(botId,p.url,p.refresh===true);const deadline=Date.now()+15000;
  while(item.status==='queued'&&Date.now()<deadline){if(signal?.aborted){item.status='failed';item.error='Opening cancelled';await this.store.write('previews',this.records);throw Error(item.error);}await new Promise(r=>setTimeout(r,150));}
  return {content:[{type:'text',text:JSON.stringify({...item,note:item.status==='opened'?'Android opened the URL in Chrome directly. This is a launch receipt, not visual verification of the page.':item.status==='queued'?'Waiting for Android to open the URL in Chrome. Do not claim it is visible; the app runtime must be running.':item.error})}],details:{}};
 }};}
}
