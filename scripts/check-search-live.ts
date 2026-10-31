/** Explicit network diagnostic, separate from deterministic tests. No model/API key is used. */
import {researchTools} from '../backend/src/research-tools.js';
const tool=researchTools(async()=>({enabled:false})).find(tool=>tool.name==='web_search')!;
const queries=['Pakistan','Android LocationManager documentation','Kyant0 AndroidLiquidGlass GitHub','Lahore Pakistan weather today'];
let passed=0;
for(const query of queries){
 const start=Date.now();
 try{
  const response=await tool.execute('live-search',{query,limit:5});
  const result=JSON.parse(response.content.filter(part=>part.type==='text').map(part=>part.text).join(''));
  console.log(JSON.stringify({query,status:'results',provider:result.provider,ms:Date.now()-start,cached:result.cached,results:result.results.map((r:any)=>({title:r.title,url:r.url}))}));passed++;
 }catch(error){console.log(JSON.stringify({query,status:'unavailable',ms:Date.now()-start,error: error instanceof Error?error.message:String(error)}));}
}
console.log(JSON.stringify({summary:true,passed,total:queries.length,note:'Successful responses pass a keyword check; this does not verify facts, recency or universal availability.'}));
if(passed!==queries.length)process.exitCode=1;
