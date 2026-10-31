import {randomUUID} from 'node:crypto';
import type {FileChange} from './file-changes.js';

export type ToolProgress={id:string;name:string;input:string;output:string;changes?:FileChange[];status:'running'|'done'|'error'|'interrupted'};
export type RunProgress={id:string;status:'working'|'completed'|'paused'|'stopped'|'failed';started?:number;updated:number;text:string;thinking:string;thoughts:Record<string,string>;tools:ToolProgress[]};
export function newProgress():RunProgress{return {id:randomUUID(),status:'working',started:Date.now(),updated:Date.now(),text:'',thinking:'',thoughts:{},tools:[]};}
export function updateProgress(progress:RunProgress,event:any,redact:(text:string)=>string,message?:any){
 const safe=(value:unknown,max:number)=>redact(typeof value==='string'?value:JSON.stringify(value??'')).slice(0,max);
 if(event.type==='message_update'||event.type==='message_end'){
  const content=message?.content||event.message?.content;
  if(Array.isArray(content)){
   const text=content.filter(p=>p.type==='text').map(p=>p.text||'').join('\n');if(text)progress.text=safe(text,32000);
   const thinking=content.filter(p=>p.type==='thinking').map(p=>p.thinking||'').join('\n');
   if(thinking){progress.thoughts??={};progress.thoughts[String(message?.timestamp||event.message?.timestamp||'current')]=safe(thinking,64000);progress.thinking=Object.values(progress.thoughts).join('\n\n');}
  }
 }
 if(['tool_execution_start','tool_execution_update','tool_execution_end'].includes(event.type)){
  const id=String(event.toolCallId||event.id||event.toolName);let tool=progress.tools.find(t=>t.id===id);
  if(!tool&&!event.toolName)return;
  if(!tool){tool={id,name:String(event.toolName||'Tool'),input:'',output:'',status:'running'};progress.tools.push(tool);}
  if(event.args!==undefined)tool.input=safe(event.args,6000);
  const result=event.result??event.partialResult;if(result!==undefined)tool.output=safe(result,12000);
  if(Array.isArray(result?.details?.changes))tool.changes=result.details.changes.slice(0,12).map((change:FileChange)=>({...change,path:safe(change.path,1000),diff:safe(change.diff,24000)}));
  if(event.type==='tool_execution_end')tool.status=event.isError?'error':'done';
 }
 if(event.type==='thinking'&&typeof event.text==='string'){progress.thoughts??={};progress.thoughts[String(event.id||'provider')]=safe(event.text,64000);progress.thinking=Object.values(progress.thoughts).join('\n\n');}
 progress.updated=Date.now();
}
export function interruptProgress(progress:RunProgress,status:'paused'|'stopped'|'failed'){
 progress.status=status;progress.updated=Date.now();for(const tool of progress.tools)if(tool.status==='running')tool.status='interrupted';
}
