import { spawn, type ChildProcess } from "node:child_process";
import { randomUUID, randomBytes, timingSafeEqual } from "node:crypto";
import { mkdir, writeFile, rm } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import type { Express } from "express";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import type { Processes } from "./processes.js";
import type { Terminals } from "./terminals.js";
import { Value } from "typebox/value";

export const providerCatalog = [
 { id:"codex", name:"Codex / ChatGPT", command:"codex", install:"npm install -g @openai/codex", login:"codex login --device-auth", docs:"https://developers.openai.com/codex/cli", supported:true },
 { id:"claude", name:"Claude Code", command:"claude", install:"npm install -g @anthropic-ai/claude-code", login:"claude auth login", docs:"https://code.claude.com/docs/en/authentication", supported:true },
 { id:"opencode", name:"OpenCode", command:"opencode", install:"npm install -g opencode-ai", login:"opencode auth login", docs:"https://opencode.ai/docs/cli/", supported:true },
 { id:"gemini", name:"Gemini CLI", command:"gemini", install:"npm install -g @google/gemini-cli", login:"gemini", docs:"https://geminicli.com/docs/get-started/authentication/", supported:true },
 { id:"antigravity", name:"Antigravity", command:"", install:"", login:"", docs:"https://antigravity.google/", supported:false, reason:"Antigravity desktop is not an Android/Linux subscription CLI adapter. Gemini CLI is a separate supported integration; its account eligibility is decided by Google." },
 { id:"freebuff", name:"Freebuff", command:"", install:"", login:"", docs:"", supported:false, reason:"No supported vendor CLI and subscription protocol has been verified. No credentials are requested." },
 { id:"commandcode", name:"CommandCode", command:"", install:"", login:"", docs:"", supported:false, reason:"No supported vendor CLI and subscription protocol has been verified. No credentials are requested." },
] as const;
export type ExecutionProvider = "byok" | typeof providerCatalog[number]["id"];
export function provider(id: string) {const p=providerCatalog.find(p=>p.id===id);if(!p)throw Error("Unknown subscription provider");return p;}

async function probe(command:string,args:string[],timeout=4000):Promise<{code:number|null;text:string}>{return new Promise(resolve=>{const child=spawn(command,args,{stdio:["ignore","pipe","pipe"]});let text="";const append=(b:Buffer)=>text=(text+b.toString()).slice(-8000);child.stdout.on("data",append);child.stderr.on("data",append);const timer=setTimeout(()=>{child.kill();resolve({code:null,text:""});},timeout);child.once("error",()=>{clearTimeout(timer);resolve({code:null,text:""});});child.once("close",code=>{clearTimeout(timer);resolve({code,text});});});}

/** The vendor CLI owns login and token refresh. No vendor credentials are imported. */
export class SubscriptionProviders {
 constructor(private launcher:(command:string)=>{command:string;args:string[]} = command=>({command,args:[]})) {}
 private base="http://127.0.0.1:8787";
 private apiToken="";
 private runs=new Map<string,{secret:string;tools:AgentTool<any>[];signal:AbortSignal}>();
 configure(base:string,token:string){this.base=base;this.apiToken=token;}
 async status(){return Promise.all(providerCatalog.map(async p=>{if(!p.supported)return {...p,installed:false,authenticated:false};const version=await probe(p.command,["--version"]);let authenticated:boolean|null=null;if(version.code===0&&(p.id==="codex"||p.id==="claude")){const auth=await probe(p.command,p.id==="codex"?["login","status"]:["auth","status"]);authenticated=auth.code===0;}return {...p,installed:version.code===0,authenticated,version:version.code===0?version.text.trim().slice(0,100):"",note:"Install and sign in on this phone. A subscription does not automatically include API access; the vendor determines eligibility and usage."};}));}
 async bridge(id:string,secret:string,operation:string,input:any){const run=this.runs.get(id);if(!run||secret.length!==run.secret.length||!timingSafeEqual(Buffer.from(secret),Buffer.from(run.secret)))throw Error("Provider tool session expired or unauthorized");if(run.signal.aborted)throw Error("Provider task cancelled");if(operation==="list")return {tools:run.tools.map(t=>({name:t.name,description:t.description,inputSchema:t.parameters}))};if(operation!=="call")throw Error("Unknown provider bridge operation");const tool=run.tools.find(t=>t.name===input?.name);if(!tool)throw Error("Unknown provider tool");if(!Value.Check(tool.parameters,input.arguments||{}))throw Error("Invalid provider tool arguments");return tool.execute(randomUUID(),input.arguments||{},run.signal);}
 async run(id:string,options:{cwd:string;prompt:string;model?:string;tools:AgentTool<any>[];signal:AbortSignal;onText?:(text:string)=>void}){
  const p=provider(id);if(!p.supported)throw Error(p.reason);if(options.signal.aborted)throw Error("Cancelled");
  const runId=randomUUID(),secret=randomBytes(32).toString("hex"),directory=join(tmpdir(),"pockobots-provider-"+runId);await mkdir(directory,{recursive:true,mode:0o700});
  this.runs.set(runId,{secret,tools:options.tools,signal:options.signal});
  try{
   const helper=join(directory,"bridge.cjs"),configPath=join(directory,"mcp.json");await writeFile(helper,bridgeSource,{mode:0o600});
   const environment={POCKO_PROVIDER_API:this.base,POCKO_PROVIDER_RUN:runId,POCKO_PROVIDER_BRIDGE_TOKEN:secret,POCKO_PROVIDER_API_TOKEN:this.apiToken};const mcp={command:process.execPath,args:[helper],env:environment};
   await writeFile(configPath,JSON.stringify({mcpServers:{pockobots:mcp}}),{mode:0o600});
   const env:NodeJS.ProcessEnv={...process.env,...environment,NO_COLOR:"1"};let args:string[]=[];const model=options.model&&options.model!=="default"?options.model:undefined;
   if(id==="codex"){args=["exec","--json","--skip-git-repo-check","--dangerously-bypass-approvals-and-sandbox","-c",`mcp_servers.pockobots.command=${JSON.stringify(process.execPath)}`,"-c",`mcp_servers.pockobots.args=${JSON.stringify([helper])}`,"-c",`mcp_servers.pockobots.env_vars=${JSON.stringify(Object.keys(environment))}`,"-c","mcp_servers.pockobots.tool_timeout_sec=600",...(model?["--model",model]:[]),"-"];}
   if(id==="claude")args=["-p","--output-format","stream-json","--verbose","--allowedTools","Bash,Read,Edit,Write,Glob,Grep,mcp__pockobots__*","--mcp-config",configPath,...(model?["--model",model]:[])];
   if(id==="opencode"){env.OPENCODE_CONFIG_CONTENT=JSON.stringify({permission:"allow",mcp:{pockobots:{type:"local",command:[process.execPath,helper],environment,enabled:true,timeout:600000}}});args=["run","--format","json",...(model?["--model",model]:[]),options.prompt];}
   if(id==="gemini"){const config=join(directory,"gemini.json");await writeFile(config,JSON.stringify({mcpServers:{pockobots:{...mcp,timeout:600000,trust:true}}}),{mode:0o600});env.GEMINI_CLI_SYSTEM_SETTINGS_PATH=config;args=["--prompt",options.prompt,"--output-format","stream-json","--approval-mode","yolo",...(model?["--model",model]:[])];}
   const launcher=this.launcher(p.command); return await executeProvider(launcher.command,[...launcher.args,...args],{cwd:options.cwd,env,prompt:["codex","claude"].includes(id)?options.prompt:undefined,signal:options.signal,onText:options.onText});
  }finally{this.runs.delete(runId);await rm(directory,{recursive:true,force:true});}
 }
}

/** Parse only user-facing text; never persist a raw provider event or reasoning trace. */
export function providerText(event:any):string|undefined {
 if(event.type==="result"&&typeof event.result==="string")return event.result;
 if(event.type==="item.completed"&&event.item?.type==="agent_message")return event.item.text;
 if(event.type==="text"&&typeof event.part?.text==="string")return event.part.text;
 if(event.type==="message"&&event.role==="assistant"&&typeof event.content==="string")return event.content;
 if(event.type==="assistant"&&Array.isArray(event.message?.content))return event.message.content.filter((p:any)=>p.type==="text").map((p:any)=>p.text).join("\n");
 return undefined;
}
export function executeProvider(command:string,args:string[],options:{cwd:string;env:NodeJS.ProcessEnv;prompt?:string;signal:AbortSignal;onText?:(text:string)=>void}):Promise<string>{
 return new Promise((resolve,reject)=>{const child=spawn(command,args,{cwd:options.cwd,env:options.env,detached:process.platform!=="win32",stdio:["pipe","pipe","pipe"]});let pending="",answer="",settled=false;let force:ReturnType<typeof setTimeout>|undefined;
 const kill=()=>{try{if(child.pid&&process.platform!=="win32")process.kill(-child.pid,"SIGTERM");else child.kill();}catch{}if(!force){force=setTimeout(()=>{try{if(child.pid&&process.platform!=="win32")process.kill(-child.pid,"SIGKILL");else child.kill("SIGKILL");}catch{}},3000);force.unref();}};
 const abort=()=>kill();const timer=setTimeout(kill,3600000);timer.unref();options.signal.addEventListener("abort",abort,{once:true});
 const accept=(line:string)=>{try{const text=providerText(JSON.parse(line));if(text){answer=text.slice(-30000);options.onText?.(answer);}}catch{}};
 child.stdout.on("data",b=>{pending+=b.toString();if(pending.length>2000000){pending="";kill();return;}let newline;while((newline=pending.indexOf("\n"))>=0){accept(pending.slice(0,newline));pending=pending.slice(newline+1);}});
 child.stderr.on("data",()=>{});
 const finish=(failure?:Error)=>{if(settled)return;settled=true;clearTimeout(timer);if(force)clearTimeout(force);options.signal.removeEventListener("abort",abort);if(failure)reject(failure);else resolve(answer||"The provider ended without a text response. Check the project's files and provider account.");};
 child.once("error",()=>finish(Error("Provider executable is unavailable. Install it in Main terminal and sign in first.")));
 child.once("close",code=>{if(pending)accept(pending);if(options.signal.aborted)return finish(Error("Provider task cancelled"));if(code!==0)return finish(Error("Provider CLI stopped ("+code+"). Open its login in Settings and check its account or platform compatibility."));finish();});
 child.stdin.on("error",()=>{});child.stdin.end(options.prompt||"");if(options.signal.aborted)abort();
 });
}
export const subscriptions=new SubscriptionProviders();
/** Register after the app's existing bearer authentication middleware. */
export function registerProviderRoutes(app:Express,options:{port:number;root:string;processes:Processes;terminals:Terminals;token:string}){
 subscriptions.configure("http://127.0.0.1:"+options.port,options.token);
 app.get("/api/providers",async(_req,res)=>res.json(await subscriptions.status()));
 app.post("/api/providers/:id/install",(req,res)=>{const p=provider(req.params.id);if(!p.supported)throw Error(p.reason);res.status(201).json(options.processes.start(p.install,options.root,"command",undefined,"Install "+p.name));});
 app.post("/api/providers/:id/login",(req,res)=>{const p=provider(req.params.id);if(!p.supported)throw Error(p.reason);const terminal=options.terminals.start(options.root);options.terminals.input(terminal.id,p.login+"\n");res.status(201).json({...terminal,provider:p.id,instruction:"Complete the vendor's login in this terminal. Credentials stay in the vendor CLI's phone-owned account directory."});});
 app.post("/api/provider-tools/:run/:operation",async(req,res)=>res.json(await subscriptions.bridge(req.params.run,String(req.headers["x-provider-bridge-token"]||""),req.params.operation,req.body)));
}

const bridgeSource=String.raw`const readline=require('node:readline');const origin=process.env.POCKO_PROVIDER_API;const id=process.env.POCKO_PROVIDER_RUN;const secret=process.env.POCKO_PROVIDER_BRIDGE_TOKEN;
// Each request has a run-scoped secret in addition to the host's private API token.
async function request(operation,body){const response=await fetch(origin+'/api/provider-tools/'+id+'/'+operation,{method:'POST',headers:{'Content-Type':'application/json','X-Provider-Bridge-Token':secret,Authorization:'Bearer '+process.env.POCKO_PROVIDER_API_TOKEN},body:JSON.stringify(body),signal:AbortSignal.timeout(600000)});const result=await response.json();if(!response.ok)throw Error(result.error||'Provider tool failed');return result;}
readline.createInterface({input:process.stdin}).on('line',async line=>{let m;try{m=JSON.parse(line);if(!('id'in m))return;let result;if(m.method==='initialize')result={protocolVersion:m.params.protocolVersion,capabilities:{tools:{}},serverInfo:{name:'PockoBots tools',version:'1'}};else if(m.method==='tools/list')result=await request('list',{});else if(m.method==='tools/call')result=await request('call',m.params);else if(m.method==='ping')result={};else throw Error('Unsupported method');process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:m.id,result})+'\n');}catch(e){if(m&&'id'in m)process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:m.id,error:{code:-32000,message:String(e.message)}})+'\n');}});`;
