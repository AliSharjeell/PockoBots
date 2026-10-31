import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileChange,snapshotFiles,snapshotChanges} from '../src/file-changes.js';
import {newProgress,updateProgress} from '../src/activity-progress.js';
import {agentTools} from '../src/tools.js';
import {Workspace} from '../src/workspace.js';

test('diffs count actual additions and removals, including empty and CRLF files',()=>{
 assert.equal(fileChange('same','hello\n','hello\n'),undefined);
 assert.equal(fileChange('new','','one\ntwo\n')?.added,2);
 assert.equal(fileChange('deleted','one\ntwo\n','')?.removed,2);
 const edit=fileChange('app.ts','a\r\nb\r\nc\r\n','a\r\nchanged\r\nc\r\n')!;
 assert.equal(edit.added,1);assert.equal(edit.removed,1);assert.match(edit.diff,/-b\r?\n\+changed/);
});
test('display truncation retains full line counts',()=>{
 const edit=fileChange('large.txt','',('x'.repeat(100)+'\n').repeat(500))!;
 assert.equal(edit.added,500);assert.equal(edit.diff.length,24000);assert.equal(edit.truncated,true);
});
test('shell snapshots include new, edited, deleted files, excluding dependencies and binary files',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-diff-'));
 try{
  await mkdir(join(root,'node_modules'));await writeFile(join(root,'app.js'),'old\n');await writeFile(join(root,'gone.txt'),'gone\n');await writeFile(join(root,'binary'),Buffer.from([0,1,2]));
  const before=await snapshotFiles(root);
  await writeFile(join(root,'app.js'),'new\n');await rm(join(root,'gone.txt'));await writeFile(join(root,'new.css'),'body {}\n');await writeFile(join(root,'node_modules','ignore.js'),'ignore\n');
  const changes=snapshotChanges(before,await snapshotFiles(root));assert.deepEqual(changes.map(c=>c.path).sort(),['app.js','gone.txt','new.css']);assert.equal(changes.find(c=>c.path==='app.js')?.removed,1);
 }finally{await rm(root,{recursive:true,force:true});}
});
test('write tool emits real patches, and progress redacts secrets in stored patches',async()=>{
 const root=await mkdtemp(join(tmpdir(),'pocko-write-'));try{
  const workspace=new Workspace(root);await workspace.init();await workspace.write('app.js','old\n');
  const write=agentTools(workspace,{} as any).find(t=>t.name==='write_file')!;
  const result=await write.execute('write',{path:'app.js',content:'private-key\n'});
  const progress=newProgress();updateProgress(progress,{type:'tool_execution_start',toolCallId:'write',toolName:'write_file'},s=>s);
  updateProgress(progress,{type:'tool_execution_end',toolCallId:'write',result},s=>s.replaceAll('private-key','[redacted]'));
  assert.equal(progress.tools[0].changes?.[0].added,1);assert.equal(progress.tools[0].changes?.[0].removed,1);assert.doesNotMatch(JSON.stringify(progress),/private-key/);
 }finally{await rm(root,{recursive:true,force:true});}
});
