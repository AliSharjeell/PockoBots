import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readdir, rm,readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { GitHubLogin } from "../src/github-login.js";

async function until(check:()=>boolean){const deadline=Date.now()+5000;while(!check()&&Date.now()<deadline)await new Promise(r=>setTimeout(r,20));assert.ok(check());}
const fixture=fileURLToPath(new URL("./fixtures/github-login.mjs",import.meta.url));
test("GitHub device sign-in exposes only its code and saves the token privately",async()=>{
 const root=await mkdtemp(join(tmpdir(),"pocko-github-"));let saved="";
 const login=new GitHubLogin(root,async(id,token)=>{assert.equal(id,"github");saved=token;},{command:process.execPath,args:[fixture]});
 try{
  assert.equal(login.start("github").status,"preparing");
  await until(()=>login.status("github").status==="waiting");
  const waiting=login.status("github");assert.equal(waiting.userCode,"ABCD-1234");assert.equal(waiting.authorizationUrl,"https://github.com/login/device");assert.doesNotMatch(JSON.stringify(waiting),/private-token/);
  await until(()=>login.status("github").status==="authorized");assert.equal(saved,"fixture-github-private-token");
  await until(()=>false===login.status("github").status.includes("waiting"));
  // Temporary CLI account files are removed after copying the token into the connector store.
  for(let i=0;(await readdir(root)).length&&i<50;i++)await new Promise(r=>setTimeout(r,20));assert.deepEqual(await readdir(root),[]);
 }finally{login.close();await rm(root,{recursive:true,force:true});}
});

test('all bot commands reuse private CLI authorization, including concurrent calls, without exporting tokens',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-gh-shared-'));const login=new GitHubLogin(root,async()=>{},{command:process.execPath,args:[fixture]});
 try{
  const calls=await Promise.all([login.command('fixture-github-private-token',['repo','list']),login.command('fixture-github-private-token',['repo','list'])]);
  for(const result of calls){assert.equal(result.exitCode,0);assert.match(result.stdout,/private-project/);assert.doesNotMatch(JSON.stringify(result),/fixture-github-private-token/);}
  assert.doesNotMatch(await readFile(join(root,'account','credential-version'),'utf8'),/fixture-github-private-token/);
  await assert.rejects(login.command('fixture-github-private-token',['auth','token']),/credential export/);
  await assert.rejects(login.command('fixture-github-private-token',['auth','status','--show-token']),/credential export/);
 }finally{login.close();await rm(root,{recursive:true,force:true});}
});
test("cancelling GitHub sign-in kills its process and never applies a late token",async()=>{
 const root=await mkdtemp(join(tmpdir(),"pocko-github-cancel-"));let saves=0;
 const login=new GitHubLogin(root,async()=>{saves++;},{command:process.execPath,args:[fixture,"--slow"]});
 try{login.start("github");await until(()=>login.status("github").status==="waiting");login.cancel("github");assert.equal(login.status("github").status,"cancelled");await new Promise(r=>setTimeout(r,150));assert.equal(saves,0);assert.doesNotMatch(JSON.stringify(login.status("github")),/ABCD|private-token/);}
 finally{login.close();await rm(root,{recursive:true,force:true});}
});
