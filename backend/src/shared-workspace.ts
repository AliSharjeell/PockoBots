import {Type} from 'typebox';
import {createHash} from 'node:crypto';
import type {AgentTool} from '@earendil-works/pi-agent-core';
import type {Projects} from './projects.js';
import {fileChange} from './file-changes.js';

const revisions=(text:string)=>createHash('sha256').update(text).digest('hex');
const writes=new Map<string,Promise<void>>();
async function serialWrite<T>(key:string,work:()=>Promise<T>){const previous=writes.get(key)||Promise.resolve();let release!:()=>void;const tail=new Promise<void>(r=>release=r);writes.set(key,tail);await previous;try{return await work();}finally{release();if(writes.get(key)===tail)writes.delete(key);}}
export function sharedWorkspaceTool(projects:Projects,run:(command:string,signal?:AbortSignal)=>Promise<unknown>):AgentTool<any>{
 return {name:'shared_workspace',label:'Files',description:'All bots use one filesystem. info returns the main directory; list/read/write use paths relative to it; run executes a shell command there. Coordinate edits with teammates. Read returns a revision; include expectedRevision when replacing a file to detect another bot changing it. Existing project folders remain accessible in this same directory.',parameters:Type.Object({action:Type.Union(['info','list','read','write','run'].map(v=>Type.Literal(v))),path:Type.Optional(Type.String()),content:Type.Optional(Type.String()),expectedRevision:Type.Optional(Type.String()),command:Type.Optional(Type.String())}),execute:async(_id,p:any,signal)=>{
  const workspace=await projects.shared();let result:unknown,changes:any[]=[];
  switch(p.action){
   case 'info':result={path:workspace.root,sharedBy:'all bots'};break;
   case 'list':result=await workspace.list(p.path||'.');break;
   case 'read':{const content=await workspace.read(p.path);result={path:p.path,content,revision:revisions(content)};break;}
   case 'write':{result=await serialWrite(await workspace.path(p.path,true),async()=>{let before='';try{before=await workspace.read(p.path);}catch(error:any){if(error.code!=='ENOENT')throw error;}if(p.expectedRevision&&p.expectedRevision!==revisions(before))throw Error('File changed. Read the latest file and merge your edit before retrying.');await workspace.write(p.path,p.content);const change=fileChange(p.path,before,p.content);if(change)changes=[change];return {path:p.path,saved:true,revision:revisions(p.content)};});break;}
   case 'run':if(typeof p.command!=='string'||!p.command.trim())throw Error('Enter a shared directory command');result=await run(p.command,signal);break;
   default:throw Error('Unknown shared workspace action');
  }
  return {content:[{type:'text',text:JSON.stringify(result)}],details:{changes}};
 }};
}
