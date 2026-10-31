import { diffLines, createTwoFilesPatch } from 'diff';
import { readdir, readFile, lstat } from 'node:fs/promises';
import { join } from 'node:path';

export type FileChange = {path:string;added:number;removed:number;diff:string;truncated:boolean};
/** Counts use complete contents; only the displayed patch is bounded. */
export function fileChange(path:string,before:string,after:string):FileChange|undefined {
 if(before===after)return;
 const parts=diffLines(before,after,{timeout:1000});
 if(!parts)return; // Do not invent counts when a pathological diff times out.
 let added=0,removed=0;
 for(const part of parts){if(part.added)added+=part.count||0;if(part.removed)removed+=part.count||0;}
 const patch=createTwoFilesPatch(path,path,before,after,undefined,undefined,{context:3,timeout:1000});
 if(!patch)return;
 return {path,added,removed,diff:patch.slice(0,24000),truncated:patch.length>24000};
}

const ignored=new Set(['.git','node_modules','.gradle','build','dist','.next','.venv','vendor']);
type Snapshot={files:Map<string,string>;seen:Set<string>;complete:boolean};
/** Bounded text snapshots also catch edits made through shell commands. Never follow symlinks. */
export async function snapshotFiles(root:string):Promise<Snapshot>{
 const snapshot:Snapshot={files:new Map(),seen:new Set(),complete:true};let visited=0,bytes=0;
 async function walk(relative=''):Promise<void>{
  let entries;try{entries=await readdir(join(root,relative),{withFileTypes:true});}catch{snapshot.complete=false;return;}
  for(const entry of entries){
   if(++visited>4096){snapshot.complete=false;return;}
   const path=relative?relative+'/'+entry.name:entry.name;
   if(entry.isDirectory()){if(!ignored.has(entry.name))await walk(path);continue;}
   snapshot.seen.add(path);
   if(!entry.isFile())continue;
   try{
    const full=join(root,path),stat=await lstat(full);if(stat.isSymbolicLink()||stat.size>256000)continue;
    if(snapshot.files.size>=128||bytes+stat.size>4000000){snapshot.complete=false;continue;}
    const content=await readFile(full);if(content.includes(0))continue;
    snapshot.files.set(path,content.toString('utf8'));bytes+=content.length;
   }catch{snapshot.complete=false;}
  }
 }
 await walk();return snapshot;
}
export function snapshotChanges(before:Snapshot,after:Snapshot):FileChange[]{
 const changes:FileChange[]=[];
 for(const path of new Set([...before.files.keys(),...after.files.keys()])){
  const old=before.files.get(path),next=after.files.get(path);
  if(old===undefined&&(!before.complete||before.seen.has(path)))continue;
  if(next===undefined&&(!after.complete||after.seen.has(path)))continue;
  const change=fileChange(path,old??'',next??'');if(change)changes.push(change);
  if(changes.length===12)break;
 }
 return changes;
}
