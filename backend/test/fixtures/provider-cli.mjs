import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
const options=process.argv.slice(2);const entry=options.find(v=>v.startsWith('mcp_servers.pockobots.args='));
const helper=JSON.parse(entry.slice(entry.indexOf('=')+1))[0];
const client=new Client({name:'fake-vendor-cli',version:'1'},{capabilities:{}});
await client.connect(new StdioClientTransport({command:process.execPath,args:[helper],env:process.env,stderr:'pipe'}));
const list=await client.listTools();
if(list.tools.some(t=>t.name==='write_note')){
 let rejected=false;try{await client.callTool({name:'write_note',arguments:{text:123}});}catch{rejected=true;}if(!rejected)throw Error('Invalid tool arguments were accepted');
 await client.callTool({name:'write_note',arguments:{text:'Called through the real provider MCP bridge'}});
}else{
 await client.callTool({name:'observe_screen',arguments:{}});
 await client.callTool({name:'report_task_status',arguments:{status:'completed',summary:'Battery verified using the subscription phone tools'}});
}
await client.close();
console.log(JSON.stringify({type:'item.completed',item:{type:'reasoning',text:'Private reasoning must not be shown'}}));
console.log(JSON.stringify({type:'item.completed',item:{type:'agent_message',text:'The subscription CLI used the PockoBots tool'}}));
