import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Bots } from "../src/bots.js";
import { Store } from "../src/storage.js";

const bot=(id:string)=>({id,name:id,avatar:"A",accent:"#007AFF",personality:"",enabled:true,intervalMinutes:0,lastReadMessageId:""});
const message=(id:string,botId:string,role:string)=>({id,botId,role,text:id,time:1});
const runtime=(store:Store)=>new Bots(store,{} as any,{progress:()=>undefined} as any,{} as any,{} as any,()=>{},text=>text);

test("unread counts exclude own messages and read markers never clear newer replies",async()=>{
 const root=await mkdtemp(join(tmpdir(),"pocko-unread-"));const store=new Store(root);await store.init();
 await store.write("bots",{schema:2,enabled:false,bots:[bot("alpha"),bot("beta")],jobs:[],messages:[message("system","alpha","system"),message("mine","alpha","user"),message("reply-1","alpha","assistant"),message("reply-2","alpha","assistant"),message("beta-reply","beta","assistant")]});
 const bots=runtime(store);await bots.init();
 try{
  assert.equal(bots.list()[0].unreadCount,2);assert.equal(bots.list()[1].unreadCount,1);
  assert.equal((await bots.markRead("alpha","reply-1")).unreadCount,1);
  await assert.rejects(bots.markRead("alpha","beta-reply"),/not found/);
  assert.equal((await bots.markRead("alpha","reply-2")).unreadCount,0);
  await bots.alert("alpha","A newer reply",false);
  assert.equal((await bots.markRead("alpha","reply-1")).unreadCount,1);
  const reopened=runtime(store);await reopened.init();try{assert.equal(reopened.list()[0].unreadCount,1);assert.equal(reopened.list()[0].lastReadMessageId,"reply-2");}finally{await reopened.close();}
 }finally{await bots.close();await rm(root,{recursive:true,force:true});}
});

test("existing conversations migrate without marking historical replies unread",async()=>{
 const root=await mkdtemp(join(tmpdir(),"pocko-read-migration-"));const store=new Store(root);await store.init();const legacy:any=bot("legacy");delete legacy.lastReadMessageId;
 await store.write("bots",{schema:2,enabled:false,bots:[legacy],jobs:[],messages:[message("historical","legacy","assistant")]});const bots=runtime(store);await bots.init();
 try{assert.equal(bots.list()[0].unreadCount,0);await bots.alert("legacy","New reply after upgrade",false);assert.equal(bots.list()[0].unreadCount,1);}finally{await bots.close();await rm(root,{recursive:true,force:true});}
});
