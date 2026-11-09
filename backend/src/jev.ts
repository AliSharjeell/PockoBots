import type {Express} from 'express';
import type {Store} from './storage.js';

// Adapted from OpenMuse apps/server/src/jev/adapter.ts (MIT). See THIRD_PARTY_NOTICES.md.

export type JevControl='clarification'|'comparison'|'agent';
export type JevOption={label:string;details:string[];sources:{title:string;url:string}[]};
/** Only what the agent placed in context and options. The prepared control comes first. */
export type JevInput={context:string;options:JevOption[];allowedControls:JevControl[]};
/** `option` is a position in `JevInput.options`, because labels can be untrusted. */
export type JevDecision={control:JevControl;scores:{option:number;score:number}[]};
/** The answer is `unknown` until `validated` has read it, whichever client produced it. */
export interface JevClient{decide(input:JevInput,signal:AbortSignal):Promise<unknown>}
/**
 * What a card is allowed to learn from Jev: which control to show, and in which order.
 * Scores stop here. Nothing in this outcome reaches permissions, spending or action authorization.
 */
export type JevOutcome={used:true;source:'sample'|'live';control:JevControl;order:number[]}|{used:false;reason:'off'|'timeout'|'aborted'|'failed'};

export const JEV_MODEL='jev-1.14.0';
const JEV_URL='https://api.typesafe.ai/v1/systemone';
const TOP_SCORE=3;
/** An uncertain answer does not override the control the agent prepared. */
const OVERRIDE_CONFIDENCE=0.5;

/** Ranks by rubric level, keeping the agent's order for a tie: fractions between levels are not calibrated. */
function validated(raw:any,input:JevInput){
 if(!input.allowedControls.includes(raw?.control))throw Error('Jev returned an invalid control');
 const scores=raw.scores,count=input.options.length;
 if(!Array.isArray(scores)||scores.length!==count)throw Error('Jev must score every option');
 const levels=new Map<number,number>();
 for(const entry of scores){
  if(!Number.isInteger(entry?.option)||entry.option<0||entry.option>=count||levels.has(entry.option))throw Error('Jev must score every option exactly once');
  if(typeof entry.score!=='number'||!(entry.score>=0&&entry.score<=TOP_SCORE))throw Error('Jev returned an invalid score');
  levels.set(entry.option,Math.round(entry.score));
 }
 return {control:raw.control as JevControl,order:input.options.map((_,i)=>i).sort((a,b)=>levels.get(b)!-levels.get(a)!||a-b)};
}

/** Makes no network call: keeps the agent's control and order, so a sample is never mistaken for a judgment. */
export class SampleJevClient implements JevClient{
 async decide(input:JevInput,signal:AbortSignal):Promise<JevDecision>{
  signal.throwIfAborted();
  return {control:input.allowedControls[0],scores:input.options.map((_,option)=>({option,score:Math.max(0,TOP_SCORE-option)}))};
 }
}

const controlCriteria:Record<JevControl,string>={
 clarification:'Present the prepared options as clickable next steps',
 comparison:'Present the sourced candidates as clickable comparison cards',
 agent:'Answer in prose without choice cards',
};
/** Asks over the documented REST API, so PockoBots takes no SDK dependency. */
export class LiveJevClient implements JevClient{
 constructor(private apiKey:string,private fetcher:typeof fetch=fetch){}
 async decide(input:JevInput,signal:AbortSignal):Promise<JevDecision>{
  const questions={
   control:{type:'choice',instructions:'Which interaction best serves the need described in `context`? Present prepared choices when they let the owner select a useful, supported next step. Choose an ordinary agent response only when those options are unhelpful or unsupported.',criteria:Object.fromEntries(input.allowedControls.map(control=>[control,controlCriteria[control]]))},
   // Options are referenced by position; their labels stay in state, out of the instructions.
   ...Object.fromEntries(input.options.map((_,i)=>[`fit_${i}`,{type:'score',instructions:`How well does \`options[${i}]\` fit the need described in \`context\`? Use only the details of \`options[${i}]\` as evidence.`,criteria:['Does not fit','Some fit','Good fit','Best fit']}])),
  };
  const response=await this.fetcher(JEV_URL,{method:'POST',headers:{Authorization:'Bearer '+this.apiKey,'Content-Type':'application/json'},body:JSON.stringify({model:JEV_MODEL,state:{context:input.context,options:input.options},questions}),signal,redirect:'error'});
  if(!response.ok)throw Error('Jev answered HTTP '+response.status);
  const answers=(await response.json() as any)?.answers;
  if(!answers||typeof answers!=='object'||Array.isArray(answers))throw Error('Jev returned invalid answers');
  const {control,...fits}=answers as Record<string,any>;
  if(control?.type!=='choice'||typeof control.confidence!=='number'||!(control.confidence>=0&&control.confidence<=1))throw Error('Jev returned an invalid control');
  const scores=Object.entries(fits).map(([key,answer])=>{
   const position=/^fit_(\d+)$/.exec(key);
   if(!position||answer?.type!=='score')throw Error('Jev returned an unexpected answer');
   return {option:Number(position[1]),score:answer.score};
  });
  return {control:control.confidence<OVERRIDE_CONFIDENCE&&input.allowedControls.includes(control.choice)?input.allowedControls[0]:control.choice,scores};
 }
}

type JevMode='off'|'sample'|'live';
/** Optional helper. Default off. The TypeSafe key lives in the private store, as other provider keys do. */
export class Jev{
 private mode:JevMode='off';private apiKey='';
 constructor(private store:Store,private liveClient:(apiKey:string)=>JevClient=apiKey=>new LiveJevClient(apiKey),private timeoutMs=5000){}
 async init(){
  // Jev is optional, so an unreadable file leaves it off rather than stopping the app.
  try{const saved=await this.store.read<any>('jev',{});this.apiKey=typeof saved.apiKey==='string'?saved.apiKey:'';if(saved.mode==='sample'||saved.mode==='live'&&this.apiKey)this.mode=saved.mode;}catch{}
 }
 view(){return {mode:this.mode,hasKey:!!this.apiKey,model:JEV_MODEL};}
 redact(text:string){return this.apiKey?text.split(this.apiKey).join('[redacted]'):text;}
 async configure(input:any){
  const mode=input?.mode??this.mode;let apiKey=this.apiKey;
  if(!['off','sample','live'].includes(mode))throw Error('Choose off, sample or live');
  if(input?.apiKey!==undefined&&input.apiKey!==''){if(typeof input.apiKey!=='string'||input.apiKey.length>512||/\s/.test(input.apiKey))throw Error('Enter a valid TypeSafe API key');apiKey=input.apiKey;}
  if(input?.clearKey===true)apiKey='';
  // Clearing the key of a live helper turns it off; asking for live without a key is a mistake.
  if(mode==='live'&&!apiKey&&input?.clearKey!==true)throw Error('Live Jev needs a TypeSafe API key');
  const next=mode==='live'&&!apiKey?'off':mode;
  await this.store.write('jev',{mode:next,apiKey});
  this.mode=next;this.apiKey=apiKey;
 }
 /** Never rejects: any failure is an outcome that leaves the caller's chat and tools working. */
 async decide(input:JevInput,signal?:AbortSignal):Promise<JevOutcome>{
  const mode=this.mode;
  if(mode==='off')return {used:false,reason:'off'};
  const timeout=AbortSignal.timeout(this.timeoutMs),bound=signal?AbortSignal.any([signal,timeout]):timeout;
  try{
   const client=mode==='live'?this.liveClient(this.apiKey):new SampleJevClient();
   const answer=client.decide(input,bound);
   // The race holds the bound even for a client that ignores its signal.
   const stopped=new Promise<never>((_,reject)=>{if(bound.aborted)reject(bound.reason);bound.addEventListener('abort',()=>reject(bound.reason),{once:true});});
   return {used:true,source:mode,...validated(await Promise.race([answer,stopped]),input)};
  }catch{
   return {used:false,reason:signal?.aborted?'aborted':timeout.aborted?'timeout':'failed'};
  }
 }
}

/** Register after the app's bearer authentication middleware. The key is write-only here. */
export function registerJevRoutes(app:Express,jev:Jev){
 app.get('/api/settings/jev',(_req,res)=>{res.json(jev.view());});
 app.put('/api/settings/jev',async(req,res)=>{await jev.configure(req.body);res.json(jev.view());});
}
