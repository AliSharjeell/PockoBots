import {test,expect} from '@playwright/test';
import http from 'node:http';
import {mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createApp} from '../backend/src/server.js';
let runtime:Awaited<ReturnType<typeof createApp>>,server:http.Server,provider:http.Server,temp:string,providerPort:number;
test.beforeAll(async()=>{
  temp=await mkdtemp(join(tmpdir(),'pocket-ui-'));
  provider=http.createServer(async(req,res)=>{
    for await(const chunk of req){};
    res.setHeader('Content-Type','text/event-stream');
    for(const [delta,finish] of [[{role:'assistant',content:'Hello from your local coding agent.'},null],[{},'stop']])
      res.write('data: '+JSON.stringify({id:'ui-test',object:'chat.completion.chunk',created:123,model:'ui-test',choices:[{index:0,delta,finish_reason:finish}]})+'\n\n');
    res.end('data: [DONE]\n\n');
  });
  await new Promise<void>(r=>provider.listen(0,'127.0.0.1',r));providerPort=(provider.address() as any).port;
  runtime=await createApp({stateDir:join(temp,'state'),workspace:join(temp,'workspace'),port:18888});
  server=http.createServer(runtime.app);await new Promise<void>(r=>server.listen(18888,'127.0.0.1',r));
});
test.afterAll(async()=>{
  runtime?.close();
  for(const s of [server,provider]){s?.closeAllConnections();if(s)await new Promise<void>(r=>s.close(()=>r()));}
  if(temp)await rm(temp,{recursive:true,force:true});
});
test('pair, test a model, stream chat, edit a file, and run a command on a phone-sized screen',async({page},info)=>{
  const errors:string[]=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('/');
  await page.locator('#token').fill(runtime.token);await page.locator('#pair-form button').click();
  await expect(page.locator('#pairing')).toBeHidden();
  await page.locator('[data-tab="settings"]').click();
  await page.locator('#api').selectOption('openai-completions');
  await page.locator('#base-url').fill('http://127.0.0.1:'+providerPort+'/v1');
  await page.locator('#model').fill('ui-test');await page.locator('#api-key').fill('browser-fixture-key');
  await page.locator('#test-api').click();await expect(page.locator('#test-result')).toContainText('Connected');
  await page.locator('#settings-form').getByRole('button',{name:'Save connection'}).click();
  await expect(page.locator('#api-key')).toHaveValue('');
  await page.locator('[data-tab="chat"]').click();
  await page.locator('#prompt').fill('Hello');await page.locator('#send').click();
  await expect(page.locator('#messages')).toContainText('Hello from your local coding agent.');
  await expect(page.locator('#send')).toBeEnabled();
  await page.locator('[data-tab="files"]').click();await page.locator('#new-file').click();
  await page.locator('#file-path').fill('src/hello.js');await page.locator('#file-content').fill('console.log("Hello Android");');
  await page.locator('#editor-form').getByRole('button',{name:'Save file'}).click();
  await expect(page.locator('#file-list')).toContainText('src');
  await page.locator('#file-list').getByRole('button',{name:'▸ src'}).click();
  await page.locator('#file-list').getByRole('button',{name:'· hello.js'}).click();
  await expect(page.locator('#file-content')).toHaveValue('console.log("Hello Android");');
  await page.locator('[data-tab="terminal"]').click();await page.locator('#command').fill('node src/hello.js');
  await page.locator('#command-form').getByRole('button',{name:'Run →'}).click();
  await expect(page.locator('#terminal-output')).toContainText('Hello Android');
  await page.locator('[data-tab="servers"]').click();await expect(page.locator('#anonymous')).not.toBeChecked();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.locator('[data-tab="chat"]').click();await expect(page.locator('#send')).toBeInViewport();
  await page.screenshot({path:'test-results/'+info.project.name+'.png',fullPage:true});
  await page.reload();await expect(page.locator('#pairing')).toBeHidden();
  await expect(page.locator('#messages')).toContainText('Hello from your local coding agent.');
  expect(errors).toEqual([]);
});
