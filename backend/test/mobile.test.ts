import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { mkdtemp, rm, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { ArtemisBridge } from "../src/artemis.js";
const { createApp } = process.env.POCKET_TEST_BUNDLE ? await import("../../dist/server.js") : await import("../src/server.js");

async function listen(server: http.Server) { await new Promise<void>((r) => server.listen(0,"127.0.0.1",r));return (server.address() as any).port as number; }
async function close(server: http.Server) {server.closeAllConnections();await new Promise<void>((r) => server.close(() => r()));}
async function until(fn: () => Promise<any>, check: (value: any) => boolean) {const end=Date.now()+12000;while(Date.now()<end){const value=await fn();if(check(value))return value;await new Promise((r)=>setTimeout(r,30));}throw Error("Timed out");}
function observation(body: any) {
  for(const message of [...body.messages].reverse()) {
    const content=typeof message.content==="string"?message.content:(message.content||[]).filter((p:any)=>p.type==="text").map((p:any)=>p.text).join("");
    try{const value=JSON.parse(content);if(value.screen_id)return value;}catch{}
  }
  throw Error("No observed screen in provider request");
}
function device() {
  const calls:any[]=[];let label="Battery";let token="test-token";let allowed=true;
  const server=http.createServer(async(req,res)=>{
    if(req.headers["x-artemis-token"]!==token){res.statusCode=401;res.end(JSON.stringify({success:false,error:"unauthorized"}));return;}
    if(!allowed&&!req.url?.startsWith("/ping")){res.statusCode=409;res.end(JSON.stringify({success:false,error:"controller inactive"}));return;}
    let body="";for await(const chunk of req)body+=chunk;
    const command=body?JSON.parse(body):null;if(command)calls.push(command);
    const screen={success:true,package:"com.android.settings",width:1000,height:2000,rotation:0,
      elements:[{text:label,enabled:true,package:"com.android.settings",class:"TextView","resource-id":"battery",bounds:"[100,200][300,400]",parsed_bounds:{left:100,top:200,right:300,bottom:400}}]};
    if(req.url?.startsWith("/ping"))res.end(JSON.stringify({success:true,authenticated:true,task_allowed:allowed}));
    else if(command){label="Battery details";res.end(JSON.stringify({success:true}));}
    else res.end(JSON.stringify({...screen,has_screenshot:true,screenshot_base64:"ZmFrZS1qcGVn"}));
  });
  return {server,calls,setToken:(value:string)=>token=value,setLabel:(value:string)=>label=value,setAllowed:(value:boolean)=>allowed=value};
}
function model() {
  const requests:any[]=[];let stage=0;let script:(stage:number,body:any)=>[string,any]=()=>["observe_screen",{}];
  const server=http.createServer(async(req,res)=>{
    let raw="";for await(const chunk of req)raw+=chunk;const body=JSON.parse(raw);requests.push(body);
    const [name,args]=script(stage++,body);
    res.setHeader("Content-Type","text/event-stream");
    const event=(delta:any,finish:any=null)=>res.write("data: "+JSON.stringify({id:"mobile-mock",object:"chat.completion.chunk",created:1,model:"mock",choices:[{index:0,delta,finish_reason:finish}]})+"\n\n");
    if(name==="text"){event({role:"assistant",content:args.text});event({},"stop");res.end("data: [DONE]\n\n");return;}
    event({role:"assistant",tool_calls:[{index:0,id:"mobile-"+stage,type:"function",function:{name,arguments:JSON.stringify(args)}}]});event({},"tool_calls");res.end("data: [DONE]\n\n");
  });
  return {server,requests,setScript:(value:typeof script)=>{script=value;stage=0;}};
}

test("Artemis transport rejects stale indices, unauthenticated actions and guessed coordinates",async()=>{
  const native=device();const port=await listen(native.server);const bridge=new ArtemisBridge("test-token","http://127.0.0.1:"+port);
  try{
    await assert.rejects(new ArtemisBridge("wrong","http://127.0.0.1:"+port).observe(),/unauthorized/);
    native.setAllowed(false);await assert.rejects(bridge.observe(),/inactive/);native.setAllowed(true);
    const screen=await bridge.observe();native.setLabel("Storage");
    await assert.rejects(bridge.target("tap",screen.id,0),/changed/);assert.equal(native.calls.length,0);
    native.setLabel("Battery");await assert.rejects(bridge.target("tap",screen.id,[500,500]),/description/);
    await bridge.target("tap",screen.id,0);assert.equal(native.calls[0].x,200);assert.equal(native.calls[0].y,300);
    await assert.rejects(bridge.target("tap",screen.id,0),/expired/);
  }finally{await close(native.server);}
});

test("mobile agent runs off-request, asks for input, resumes, persists and serializes device ownership",async()=>{
  const temp=await mkdtemp(join(tmpdir(),"pocket-mobile-"));const native=device();const provider=model();
  const bridgePort=await listen(native.server),modelPort=await listen(provider.server);const reserved=http.createServer();const port=await listen(reserved);await close(reserved);
  const runtime=await createApp({stateDir:join(temp,"state"),workspace:join(temp,"projects"),port,mobileBridgeBase:"http://127.0.0.1:"+bridgePort});native.setToken(runtime.token);
  const server=http.createServer(runtime.app);await new Promise<void>((r)=>server.listen(port,"127.0.0.1",r));
  const request=async(path:string,method="GET",body?:any,expected=200)=>{
    const res=await fetch("http://127.0.0.1:"+port+"/api"+path,{method,headers:{Authorization:"Bearer "+runtime.token,"Content-Type":"application/json"},body:body?JSON.stringify(body):undefined});
    const value=await res.json() as any;assert.equal(res.status,expected,JSON.stringify(value));return value;
  };
  try{
    await request("/settings","PUT",{provider:"openai",api:"openai-completions",baseUrl:"http://127.0.0.1:"+modelPort+"/v1",model:"mock",apiKey:"mock-key"});
    provider.setScript((stage,body)=>{
      if(stage===0||stage===2||stage===4)return ["observe_screen",{}];
      if(stage===1)return ["click",{screen_id:observation(body).screen_id,target:0}];
      if(stage===3)return ["ask_user",{question:"Ready to finish?"}];
      return ["report_task_status",{status:"completed",summary:"Battery details verified"}];
    });
    const task=await request("/mobile/tasks","POST",{prompt:"Open the battery page, ask me, then verify it"},201);
    await request("/mobile/tasks/"+task.id+"/resume","POST",{},202);
    const waiting=await until(()=>request("/mobile/tasks/"+task.id),(t)=>t.status==="waiting");
    assert.ok(runtime.mobile.anyBusy());assert.equal(waiting.question.text,"Ready to finish?");
    await request("/settings","PUT",{model:"changed"},409);
    await request("/mobile/tasks/"+task.id+"/answer","POST",{questionId:"expired",text:"ready"},400);
    await request("/mobile/tasks/"+task.id+"/answer","POST",{questionId:waiting.question.id,text:"ready"});
    const finished=await until(()=>request("/mobile/tasks/"+task.id),(t)=>t.status==="completed");assert.equal(finished.output,"Battery details verified");
    await until(async()=>runtime.mobile.anyBusy(),(busy)=>!busy);
    assert.ok(provider.requests.some((r)=>JSON.stringify(r.messages).includes("image_url")),"Mobile model must receive screen images");
    const saved=JSON.parse(await readFile(join(temp,"state/mobile-tasks.json"),"utf8"));assert.equal(saved[0].status,"completed");assert.ok(!JSON.stringify(saved).includes("ZmFrZS1qcGVn"));
    assert.equal(native.calls.length,1);
    provider.setScript((stage)=>stage===0?["observe_screen",{}]:["ask_user",{question:"Wait for your reply"}]);
    const a=await request("/mobile/tasks","POST",{prompt:"Another task"},201),b=await request("/mobile/tasks","POST",{prompt:"Competing task"},201);
    const starts=await Promise.all([a,b].map((t)=>fetch("http://127.0.0.1:"+port+"/api/mobile/tasks/"+t.id+"/resume",{method:"POST",headers:{Authorization:"Bearer "+runtime.token,"Content-Type":"application/json"},body:"{}"})));
    assert.deepEqual(starts.map((r)=>r.status).sort(),[202,400]);
    const active=starts[0].status===202?a:b;await until(()=>request("/mobile/tasks/"+active.id),(t)=>t.status==="waiting");
    await request("/mobile/tasks/"+active.id+"/pause","POST",{});await until(async()=>runtime.mobile.anyBusy(),(busy)=>!busy);
    assert.equal((await request("/mobile/tasks/"+active.id)).status,"paused");
  }finally{runtime.close();await close(server);await close(provider.server);await close(native.server);await rm(temp,{recursive:true,force:true});}
});


test("one unified bot writes files then uses the phone and continues the same conversation",async()=>{
 const temp=await mkdtemp(join(tmpdir(),"pocket-unified-phone-")),native=device(),provider=model();
 const bridgePort=await listen(native.server),modelPort=await listen(provider.server);const reserve=http.createServer();const port=await listen(reserve);await close(reserve);const runtime=await createApp({stateDir:join(temp,"state"),workspace:join(temp,"projects"),port,mobileBridgeBase:"http://127.0.0.1:"+bridgePort});native.setToken(runtime.token);
 const server=http.createServer(runtime.app);await new Promise<void>(r=>server.listen(port,"127.0.0.1",r));
 const call=async(path:string,method="GET",data?:any)=>{const res=await fetch("http://127.0.0.1:"+port+"/api"+path,{method,headers:{Authorization:"Bearer "+runtime.token,"Content-Type":"application/json"},body:data?JSON.stringify(data):undefined});const value:any=await res.json();assert.ok(res.ok,JSON.stringify(value));return value;};
 try{
 await call("/settings","PUT",{provider:"openai",api:"openai-completions",baseUrl:"http://127.0.0.1:"+modelPort,model:"fixture",apiKey:"fixture-key"});
 provider.setScript((_stage,body)=>{
  const names=body.tools.map((t:any)=>t.function.name);
  if(names.includes("write_file")){
   if(!body.messages.some((m:any)=>m.role==="tool"))return ["write_file",{path:"mixed.txt",content:"Created before phone work"}];
   if(!body.messages.some((m:any)=>m.role==="assistant"&&m.tool_calls?.some((t:any)=>t.function.name==="use_phone")))return ["use_phone",{task:"Check the Battery page"}];
   return ["text",{text:"File saved and Battery checked on this phone"}];
  }
  if(!body.messages.some((m:any)=>m.role==="tool"))return ["observe_screen",{}];
  return ["report_task_status",{status:"completed",summary:"Battery verified from the current screen"}];
 });
 const bot=await call("/bots","POST",{name:"Universal",role:"Code and use the phone"});await call("/bots/"+bot.id+"/messages","POST",{text:"Write a file and check Battery"});
 const pending=await until(()=>call("/bots/phone"),v=>v?.taskId);await call("/bots/phone/ready","POST",{taskId:pending.taskId});
 const result=await until(()=>call("/bots/"+bot.id),v=>v.jobs[0]?.status==="completed");assert.match(result.messages.at(-1).text,/Battery checked/);assert.equal(result.jobs[0].phoneTaskId,pending.taskId);assert.equal((await call("/file?projectId="+bot.projectId+"&path=mixed.txt")).content,"Created before phone work");assert.equal(runtime.mobile.state(pending.taskId).status,"completed");
 }finally{await runtime.close();await close(server);await close(provider.server);await close(native.server);await rm(temp,{recursive:true,force:true});}
});


test("phone tasks pause repeated app relaunches instead of looping",async()=>{
 const temp=await mkdtemp(join(tmpdir(),"pocket-loop-")),native=device(),provider=model();const bridgePort=await listen(native.server),modelPort=await listen(provider.server);
 const runtime=await createApp({stateDir:join(temp,"state"),workspace:join(temp,"projects"),mobileBridgeBase:"http://127.0.0.1:"+bridgePort});native.setToken(runtime.token);
 try{ // Configure through the authenticated API below
 const server=http.createServer(runtime.app);await new Promise<void>(r=>server.listen(8787,"127.0.0.1",r));
 try{const response=await fetch("http://127.0.0.1:8787/api/settings",{method:"PUT",headers:{Authorization:"Bearer "+runtime.token,"Content-Type":"application/json"},body:JSON.stringify({provider:"openai",api:"openai-completions",baseUrl:"http://127.0.0.1:"+modelPort,model:"fixture",apiKey:"fixture"})});assert.ok(response.ok);
 provider.setScript((stage,body)=>stage%2===0?["observe_screen",{}]:["manage_app",{screen_id:observation(body).screen_id,app_name:"com.android.chrome"}]);
 const task=await runtime.mobile.create("Open Chrome");await runtime.mobile.start(task.id);const result=await until(async()=>runtime.mobile.state(task.id),t=>t.status==="paused");assert.match(result.error,/repeated app/);assert.equal(native.calls.length,3);
 }finally{await close(server);}
 }finally{await runtime.close();await close(provider.server);await close(native.server);await rm(temp,{recursive:true,force:true});}
});
