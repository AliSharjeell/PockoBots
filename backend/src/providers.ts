import type {Effort} from "./storage.js";
import { spawn, type ChildProcess } from "node:child_process";
import { randomUUID, randomBytes, timingSafeEqual, createHash } from "node:crypto";
import { mkdir, writeFile, rm, readFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir, homedir } from "node:os";
import type { Express } from "express";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import type { Processes } from "./processes.js";
import type { Terminals } from "./terminals.js";
import { Value } from "typebox/value";
import { catalogFor, discoverModels, type ModelCatalog } from "./provider-models.js";
import { readUsage, type ProviderUsage } from "./provider-usage.js";

export const providerCatalog = [
 { id:"codex", name:"ChatGPT", command:"codex", install:"npm install -g @openai/codex", login:"codex login --device-auth", docs:"https://developers.openai.com/codex/cli", supported:true },
 { id:"claude", name:"Claude", command:"claude", install:"npm install -g @anthropic-ai/claude-code", login:"claude auth login", docs:"https://code.claude.com/docs/en/authentication", supported:true },
 { id:"opencode", name:"OpenCode", command:"opencode", install:"npm install -g opencode-ai", login:"opencode auth login", docs:"https://opencode.ai/docs/cli/", supported:true },
 { id:"gemini", name:"Gemini", command:"gemini", install:"npm install -g @google/gemini-cli", login:"gemini", docs:"https://geminicli.com/docs/get-started/authentication/", supported:true },
 { id:"grok", name:"Grok", command:"grok", install:"mkdir -p \"$HOME/.local/bin\" && ARCH=$(uname -m) && case \"$ARCH\" in aarch64|arm64) A=aarch64 ;; *) A=x86_64 ;; esac && curl -fsSL \"https://x.ai/cli/grok-1.0.22-linux-$A\" -o \"$HOME/.local/bin/grok\" && chmod +x \"$HOME/.local/bin/grok\"", login:"grok --no-auto-update login", docs:"https://x.ai/cli", supported:true },
 { id:"cursor", name:"Cursor", command:"cursor-agent", install:"mkdir -p \"$HOME/.local/bin\" && curl -fsSL https://cursor.com/install -fsS | bash", login:"cursor-agent login", docs:"https://cursor.com/docs/cli/overview", supported:true },
] as const;

/** One provider of the catalogue, in the shape a caller reads it. */
export type ProviderEntry = { readonly id:string; readonly name:string; readonly command:string; readonly install:string; readonly login:string; readonly docs:string; readonly supported:boolean; readonly reason?:string };

/**
 * Providers that run without an account.
 *
 * OpenCode serves free models to anyone, so signing in is a choice there and not a step. Every
 * other provider hands out nothing until the owner has signed in with the vendor.
 */
export const FREE_TIER_PROVIDERS = new Set<string>(["opencode"]);
export type ExecutionProvider = "byok" | typeof providerCatalog[number]["id"];
export function provider(id: string) {const p=(providerCatalog as readonly ProviderEntry[]).find(p=>p.id===id);if(!p)throw Error("Unknown subscription provider");return p;}

/** npm takes flags that quieten its output. A vendor's own installer does not. */
export function installCommand(p:{install:string}) {return p.install.startsWith("npm install")?p.install+" --no-fund --no-audit --loglevel=http":p.install;}

async function probe(command:string,args:string[],timeout=15000):Promise<{code:number|null;text:string}>{return new Promise(resolve=>{const child=spawn(command,args,{stdio:["ignore","pipe","pipe"]});let text="";const append=(b:Buffer)=>text=(text+b.toString()).slice(-8000);child.stdout.on("data",append);child.stderr.on("data",append);const timer=setTimeout(()=>{child.kill();resolve({code:null,text:""});},timeout);child.once("error",()=>{clearTimeout(timer);resolve({code:null,text:""});});child.once("close",code=>{clearTimeout(timer);resolve({code,text});});});}

/** The vendor CLI owns login and token refresh. No vendor credentials are imported. */
export class SubscriptionProviders {
 constructor(private launcher:(command:string)=>{command:string;args:string[]} = command=>({command,args:[]})) {}
 private base="http://127.0.0.1:8787";
 private apiToken="";
 private root="";
 private runs=new Map<string,{secret:string;tools:AgentTool<any>[];signal:AbortSignal;onActivity?:(event:any)=>void}>();
 private catalogs=new Map<string,{catalog:ModelCatalog;at:number}>();
 private usages=new Map<string,{usage:ProviderUsage;at:number}>();
 private refreshing=new Map<string,Promise<void>>();
 private statusCache:{value:any[];at:number}|null=null;
 configure(base:string,token:string,root?:string){this.base=base;this.apiToken=token;if(root)this.root=root;}
 /** The model list to show for a provider: the CLI's own list once it has answered, else the shipped one. */
 private catalog(id:string){const cached=this.catalogs.get(id);return cached?cached.catalog:catalogFor(id,null);}
 /** Asks a provider's CLI which models it can run, and keeps the answer for a few minutes. */
 private refreshCatalog(id:string,force=false):Promise<void>{
  const running=this.refreshing.get(id);if(running)return running;
  const cached=this.catalogs.get(id);if(!force&&cached&&Date.now()-cached.at<300000)return Promise.resolve();
  const p=provider(id);if(!p.supported)return Promise.resolve();
  const work=(async()=>{try{const discovered=await discoverModels(id,{command:p.command,cwd:this.root||process.cwd(),env:process.env});this.catalogs.set(id,{catalog:catalogFor(id,discovered),at:Date.now()});}catch{}finally{this.refreshing.delete(id);}})();
  this.refreshing.set(id,work);return work;
 }
 /** The models a provider can run. A caller that wants fresh ones asks the CLI again. */
 async models(id:string,refresh=false){if(refresh)await this.refreshCatalog(id,true);else void this.refreshCatalog(id);return this.catalog(id);}
 /**
  * The names a provider and a model are shown by.
  *
  * A menu that shows these cannot show a raw identifier while it waits for the provider list, so the
  * names travel with the state instead of being fetched again.
  */
 describe(providerId:string,modelId:string){if(!providerId||providerId==="byok")return {providerId:"byok",providerName:"",modelId:modelId||"",modelName:modelId||"Default"};const p=providerCatalog.find(x=>x.id===providerId);const catalog=this.catalog(providerId);const model=catalog.models.find(m=>m.id===modelId);return {providerId,providerName:p?.name||providerId,modelId:modelId||"",modelName:model?.name||modelId||""};}
 /**
  * The plan limits a provider reports.
  *
  * The reading is kept for a couple of minutes, so opening the screen does not start the provider's
  * CLI over and over.
  */
 async usage(id:string,refresh=false):Promise<ProviderUsage>{
  const cached=this.usages.get(id);
  if(!refresh&&cached&&Date.now()-cached.at<120000)return cached.usage;
  const p=provider(id);
  const usage=await readUsage(p.id,p.name,{command:p.command,cwd:this.root||process.cwd(),env:process.env});
  this.usages.set(id,{usage,at:Date.now()});
  return usage;
 }
 async status(refresh=false){
  const cached=this.statusCache;
  if(!refresh&&cached&&Date.now()-cached.at<60000)return cached.value;
  const value=await Promise.all(providerCatalog.map(async p=>{
   if(!p.supported)return {...p,installed:false,authenticated:false};
   const version=await probe(p.command,["--version"]),installed=version.code===0;
   let authenticated:boolean|null=null;
   if(installed){
    if(p.id==="codex"||p.id==="claude"){const auth=await probe(p.command,p.id==="codex"?["login","status"]:["auth","status"]);authenticated=auth.code===0;}
    else if(p.id==="opencode"||p.id==="gemini"){try{const path=p.id==="opencode"?join(process.env.XDG_DATA_HOME||join(homedir(),".local/share"),"opencode/auth.json"):join(homedir(),".gemini/oauth_creds.json");const credentials=JSON.parse(await readFile(path,"utf8"));authenticated=p.id==="opencode"?Object.values(credentials).some((value:any)=>value?.type==="oauth"&&(value?.refresh||value?.access)):!!credentials.refresh_token;}catch{authenticated=false;}}
    else{const auth=await probe(p.command,["login","status"]);authenticated=auth.code===0?true:null;}
    void this.refreshCatalog(p.id);
   }
   const catalog=this.catalog(p.id);
   return {...p,installed,authenticated,signInRequired:!FREE_TIER_PROVIDERS.has(p.id),version:installed?version.text.trim().slice(0,100):"",models:catalog.models,modelSource:catalog.source,note:FREE_TIER_PROVIDERS.has(p.id)?"Its free models run without an account. Signing in adds the models your plan includes.":"Install and sign in on this phone. A subscription does not automatically include API access; the vendor determines eligibility and usage."};
  }));
  this.statusCache={value,at:Date.now()};
  return value;
 }
 async bridge(id:string,secret:string,operation:string,input:any){const run=this.runs.get(id);if(!run||!timingSafeEqual(createHash("sha256").update(secret).digest(),createHash("sha256").update(run.secret).digest()))throw Error("Provider tool session expired or unauthorized");if(run.signal.aborted)throw Error("Provider task cancelled");if(operation==="list")return {tools:run.tools.map(t=>({name:t.name,description:t.description,inputSchema:t.parameters}))};if(operation!=="call")throw Error("Unknown provider bridge operation");const tool=run.tools.find(t=>t.name===input?.name);if(!tool)throw Error("Unknown provider tool");if(!Value.Check(tool.parameters,input.arguments||{}))throw Error("Invalid provider tool arguments");const callId=randomUUID();run.onActivity?.({type:"tool_execution_start",toolCallId:callId,toolName:tool.name,args:input.arguments||{}});try{const result=await tool.execute(callId,input.arguments||{},run.signal);run.onActivity?.({type:"tool_execution_end",toolCallId:callId,toolName:tool.name,result,isError:!!(result as any).isError});return result;}catch(error){run.onActivity?.({type:"tool_execution_end",toolCallId:callId,toolName:tool.name,result:"Tool failed",isError:true});throw error;}}
 async run(id:string,options:{effort?:Effort;cwd:string;prompt:string;model?:string;tools:AgentTool<any>[];signal:AbortSignal;onText?:(text:string)=>void;onActivity?:(event:any)=>void}){
  const p=provider(id);if(!p.supported)throw Error(p.reason);if(options.signal.aborted)throw Error("Cancelled");
  const runId=randomUUID(),secret=randomBytes(32).toString("hex"),directory=join(tmpdir(),"pockobots-provider-"+runId);await mkdir(directory,{recursive:true,mode:0o700});
  this.runs.set(runId,{secret,tools:options.tools,signal:options.signal,onActivity:options.onActivity});
  try{
   const helper=join(directory,"bridge.cjs"),configPath=join(directory,"mcp.json");await writeFile(helper,bridgeSource,{mode:0o600});
   const environment={POCKO_PROVIDER_API:this.base,POCKO_PROVIDER_RUN:runId,POCKO_PROVIDER_BRIDGE_TOKEN:secret,POCKO_PROVIDER_API_TOKEN:this.apiToken};const mcp={command:process.execPath,args:[helper],env:environment};
   await writeFile(configPath,JSON.stringify({mcpServers:{pockobots:mcp}}),{mode:0o600});
   const env:NodeJS.ProcessEnv={...process.env,...environment,NO_COLOR:"1"};let args:string[]=[];const model=options.model&&options.model!=="default"?options.model:undefined;
   if(id==="codex"){args=["exec",...(options.effort?["-c",`model_reasoning_effort=${JSON.stringify(options.effort==="max"?"xhigh":options.effort)}`]:[]),"--json","--skip-git-repo-check","--dangerously-bypass-approvals-and-sandbox","-c",`mcp_servers.pockobots.command=${JSON.stringify(process.execPath)}`,"-c",`mcp_servers.pockobots.args=${JSON.stringify([helper])}`,"-c",`mcp_servers.pockobots.env_vars=${JSON.stringify(Object.keys(environment))}`,"-c","mcp_servers.pockobots.tool_timeout_sec=600",...(model?["--model",model]:[]),"-"];}
   if(id==="claude")args=["-p",...(options.effort?["--effort",options.effort==="xhigh"?"high":options.effort]:[]),"--output-format","stream-json","--verbose","--allowedTools","Bash,Read,Edit,Write,Glob,Grep,mcp__pockobots__*","--mcp-config",configPath,...(model?["--model",model]:[])];
   if(id==="opencode"){env.OPENCODE_CONFIG_CONTENT=JSON.stringify({permission:"allow",mcp:{pockobots:{type:"local",command:[process.execPath,helper],environment,enabled:true,timeout:600000}}});args=["run","--format","json",...(model?["--model",model]:[]),"--",options.prompt];}
   if(id==="gemini"){const config=join(directory,"gemini.json");await writeFile(config,JSON.stringify({mcpServers:{pockobots:{...mcp,timeout:600000,trust:true}}}),{mode:0o600});env.GEMINI_CLI_SYSTEM_SETTINGS_PATH=config;args=["--prompt",options.prompt,"--output-format","stream-json","--approval-mode","yolo",...(model?["--model",model]:[])];}
   if(id==="grok")args=["--no-auto-update","agent","--single",options.prompt,"--output-format","streaming-json","--always-approve",...(model?["--model",model]:[])];
   if(id==="cursor")args=["-p",options.prompt,"--output-format","text","--force",...(model?["--model",model]:[])];
   const launcher=this.launcher(p.command); return await executeProvider(launcher.command,[...launcher.args,...args],{cwd:options.cwd,env,prompt:["codex","claude"].includes(id)?options.prompt:undefined,signal:options.signal,onText:options.onText,onActivity:options.onActivity});
  }finally{this.runs.delete(runId);await rm(directory,{recursive:true,force:true});}
 }
}

/** Extract assistant prose separately from structured progress events. */
export function providerText(event:any):string|undefined {
 if(event.type==="result"&&typeof event.result==="string")return event.result;
 if(event.type==="item.completed"&&event.item?.type==="agent_message")return event.item.text;
 if(event.type==="text"&&typeof event.part?.text==="string")return event.part.text;
 if(event.type==="message"&&event.role==="assistant"&&typeof event.content==="string")return event.content;
 if(event.type==="assistant"&&Array.isArray(event.message?.content))return event.message.content.filter((p:any)=>p.type==="text").map((p:any)=>p.text).join("\n");
 // An Agent Client Protocol agent, such as the Grok CLI, reports what it said as a session update.
 if(event.sessionUpdate==="agent_message_chunk"&&typeof event.content?.text==="string")return event.content.text;
 return undefined;
}
/** Accept only structured vendor activity, not arbitrary stderr or an invented thought stream. */
export function providerActivity(event:any):any[]{
 if(event.sessionUpdate==="agent_thought_chunk"&&typeof event.content?.text==="string")return [{type:"thinking",id:event.toolCallId||"",text:event.content.text}];
 if(event.sessionUpdate==="tool_call")return [{type:"tool_execution_start",toolCallId:event.toolCallId,toolName:event.title||"tool",args:event.rawInput||{},result:"",isError:false}];
 if(event.sessionUpdate==="tool_call_update"&&(event.status==="completed"||event.status==="failed"))return [{type:"tool_execution_end",toolCallId:event.toolCallId,toolName:event.title||"tool",result:event.content||"",isError:event.status==="failed"}];
 const item=event.item;
 if(item&&["item.started","item.updated","item.completed"].includes(event.type)){
  if(item.type==="reasoning"&&typeof item.text==="string")return [{type:"thinking",id:item.id,text:item.text}];
  if(item.type==="command_execution"&&typeof item.command==="string")return [{type:event.type==="item.completed"?"tool_execution_end":"tool_execution_start",toolCallId:item.id,toolName:"bash",args:{command:item.command},result:item.aggregated_output,isError:item.exit_code!=null&&item.exit_code!==0}];
 }
 if(event.type==="assistant"&&Array.isArray(event.message?.content))return event.message.content.flatMap((part:any)=>part.type==="thinking"&&typeof part.thinking==="string"?[{type:"thinking",id:event.message.id,text:part.thinking}]:part.type==="tool_use"&&!String(part.name).startsWith("mcp__pockobots__")?[{type:"tool_execution_start",toolCallId:part.id,toolName:part.name,args:part.input}]:[]);
 if(event.type==="user"&&Array.isArray(event.message?.content))return event.message.content.filter((part:any)=>part.type==="tool_result").map((part:any)=>({type:"tool_execution_end",toolCallId:part.tool_use_id,result:part.content,isError:part.is_error===true}));
 return [];
}
export function executeProvider(command:string,args:string[],options:{cwd:string;env:NodeJS.ProcessEnv;prompt?:string;signal:AbortSignal;onText?:(text:string)=>void;onActivity?:(event:any)=>void}):Promise<string>{
 return new Promise((resolve,reject)=>{const child=spawn(command,args,{cwd:options.cwd,env:options.env,detached:process.platform!=="win32",stdio:["pipe","pipe","pipe"]});let pending="",raw="",answer="",providerFailed=false,settled=false,stopped=false;let force:ReturnType<typeof setTimeout>|undefined;
 const kill=()=>{stopped=true;try{if(child.pid&&process.platform!=="win32")process.kill(-child.pid,"SIGTERM");else child.kill();}catch{}if(!force){force=setTimeout(()=>{try{if(child.pid&&process.platform!=="win32")process.kill(-child.pid,"SIGKILL");else child.kill("SIGKILL");}catch{}},2000);force.unref();}};
 const abort=()=>kill();const timer=setTimeout(kill,3600000);timer.unref();options.signal.addEventListener("abort",abort,{once:true});
 const accept=(line:string)=>{try{const event=JSON.parse(line);for(const activity of providerActivity(event))options.onActivity?.(activity);if(event.type==="turn.failed"||event.type==="result"&&(event.is_error===true||event.status==="error"))providerFailed=true;const text=providerText(event);if(text){answer=(event.type==="message"&&event.delta===true?answer+text:text).slice(-30000);options.onText?.(answer);}}catch{}};
 child.stdout.on("data",b=>{const chunk=b.toString();raw=(raw+chunk).slice(-30000);pending+=chunk;if(pending.length>2000000){pending="";kill();return;}let newline;while((newline=pending.indexOf("\n"))>=0){accept(pending.slice(0,newline));pending=pending.slice(newline+1);}});
 child.stderr.on("data",()=>{});
 const finish=(failure?:Error)=>{if(settled)return;settled=true;clearTimeout(timer);if(force&&!stopped)clearTimeout(force);options.signal.removeEventListener("abort",abort);if(failure)reject(failure);else if(!answer&&!providerFailed){// A CLI that answers in plain text still answered.
  const plain=raw.replace(/\x1B\[[0-?]*[ -/]*[@-~]/g,"").trim();if(plain&&!plain.startsWith("{")&&!plain.startsWith("["))answer=plain;}if(!answer||providerFailed)reject(Error("Provider ended without a successful text result. Check its account and project activity."));else resolve(answer);};
 child.once("error",()=>finish(Error("Provider executable is unavailable. Install it in Main terminal and sign in first.")));
 child.once("close",code=>{if(pending)accept(pending);if(options.signal.aborted)return finish(Error("Provider task cancelled"));if(stopped)return finish(Error("Provider task exceeded its time or output limit"));if(code!==0)return finish(Error("Provider CLI stopped ("+code+"). Open its login in Settings and check its account or platform compatibility."));finish();});
 child.stdin.on("error",()=>{});child.stdin.end(options.prompt||"");if(options.signal.aborted)abort();
 });
}
export const subscriptions=new SubscriptionProviders();
/** Register after the app's existing bearer authentication middleware. */
export function registerProviderRoutes(app:Express,options:{port:number;root:string;processes:Processes;terminals:Terminals;token:string}){
 subscriptions.configure("http://127.0.0.1:"+options.port,options.token,options.root);
 const installations=new Map<string,string>();
 app.get("/api/providers",async(req,res)=>res.json((await subscriptions.status(req.query.refresh==="1")).map(p=>({...p,installation:options.processes.jobs.get(installations.get(p.id)||"")||null}))));
 app.get("/api/providers/:id/models",async(req,res)=>{const p=provider(req.params.id);if(!p.supported)throw Error(p.reason);res.json(await subscriptions.models(p.id,req.query.refresh==="1"));});
 app.get("/api/providers/:id/usage",async(req,res)=>{const p=provider(req.params.id);if(!p.supported)throw Error(p.reason);res.json(await subscriptions.usage(p.id,req.query.refresh==="1"));});
 app.post("/api/providers/:id/install",(req,res)=>{const p=provider(req.params.id);if(!p.supported)throw Error(p.reason);const current=options.processes.jobs.get(installations.get(p.id)||"");if(current?.status==="running"){res.json(current);return;}const job=options.processes.start(installCommand(p),options.root,"command",undefined,"Install "+p.name);installations.set(p.id,job.id);res.status(201).json(job);});
 app.get("/api/providers/:id/install/:job",(req,res)=>{provider(req.params.id);const job=options.processes.jobs.get(req.params.job);if(installations.get(req.params.id)!==req.params.job||!job){res.status(404).json({error:"Installation not found. Refresh the provider list."});return;}res.json(job);});
 app.post("/api/providers/:id/login",(req,res)=>{const p=provider(req.params.id);if(!p.supported)throw Error(p.reason);const terminal=options.terminals.start(options.root);options.terminals.input(terminal.id,p.login+"\n");res.status(201).json({...terminal,provider:p.id,instruction:"Complete the vendor's login in this terminal. Credentials stay in the vendor CLI's phone-owned account directory."});});
 app.post("/api/provider-tools/:run/:operation",async(req,res)=>res.json(await subscriptions.bridge(req.params.run,String(req.headers["x-provider-bridge-token"]||""),req.params.operation,req.body)));
}

const bridgeSource=String.raw`const readline=require('node:readline');const origin=process.env.POCKO_PROVIDER_API;const id=process.env.POCKO_PROVIDER_RUN;const secret=process.env.POCKO_PROVIDER_BRIDGE_TOKEN;
// Each request has a run-scoped secret in addition to the host's private API token.
async function request(operation,body){const response=await fetch(origin+'/api/provider-tools/'+id+'/'+operation,{method:'POST',headers:{'Content-Type':'application/json','X-Provider-Bridge-Token':secret,Authorization:'Bearer '+process.env.POCKO_PROVIDER_API_TOKEN},body:JSON.stringify(body),signal:AbortSignal.timeout(600000)});const result=await response.json();if(!response.ok)throw Error(result.error||'Provider tool failed');return result;}
readline.createInterface({input:process.stdin}).on('line',async line=>{let m;try{m=JSON.parse(line);if(!('id'in m))return;let result;if(m.method==='initialize')result={protocolVersion:m.params.protocolVersion,capabilities:{tools:{}},serverInfo:{name:'PockoBots tools',version:'1'}};else if(m.method==='tools/list')result=await request('list',{});else if(m.method==='tools/call')result=await request('call',m.params);else if(m.method==='ping')result={};else throw Error('Unsupported method');process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:m.id,result})+'\n');}catch(e){if(m&&'id'in m)process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:m.id,error:{code:-32000,message:String(e.message)}})+'\n');}});`;
