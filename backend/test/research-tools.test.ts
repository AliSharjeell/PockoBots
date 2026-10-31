import {test} from 'node:test';
import assert from 'node:assert/strict';
import {parseSearchResults,parseSearchFeed,searchWeb,researchTools} from '../src/research-tools.js';

const rss='<rss><channel><item><title><![CDATA[Android location]]></title><link>https://developer.android.com/location?a=1&amp;b=2</link><description>Official &amp; current</description></item><item><title>Unsafe</title><link>javascript:alert(1)</link></item></channel></rss>';
const html=`<a href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fdocs" class="result-link">Example <b>docs</b></a><td class='result-snippet'>Actual source text</td>`;
test('search parsers retain real citations, snippets and CDATA while rejecting unsafe links',()=>{
 assert.deepEqual(parseSearchFeed(rss),[{title:'Android location',url:'https://developer.android.com/location?a=1&b=2',snippet:'Official & current'}]);
 assert.deepEqual(parseSearchResults(html),[{title:'Example docs',url:'https://example.org/docs',snippet:'Actual source text'}]);
});
test('search falls back to another provider and never invents results on verification pages',async()=>{
 let calls=0;const fetcher=(async()=>++calls===1?new Response('Unavailable',{status:503}):new Response(html)) as typeof fetch;
 const result=await searchWeb('public query',3,undefined,fetcher);assert.equal(result.provider,'DuckDuckGo');assert.equal(result.results.length,1);
 await assert.rejects(searchWeb('public query',3,undefined,(async()=>new Response('anomaly.js',{status:202})) as typeof fetch),/unavailable/);
 const abort=new AbortController();abort.abort();await assert.rejects(searchWeb('public query',3,abort.signal,(async()=>{throw Error('Aborted');}) as typeof fetch),/cancelled/);
});
test('location tool requires opt-in and a recent fix, and identifies emulator locations',async()=>{
 const tool=(value:any)=>researchTools(async()=>value).find(tool=>tool.name==='get_current_location')!;
 await assert.rejects(tool({enabled:false}).execute('test',{},undefined),/sharing is off/);
 await assert.rejects(tool({enabled:true,latitude:1,longitude:2,timestamp:1}).execute('test',{},undefined),/No recent/);
 const result=await tool({enabled:true,latitude:33.6,longitude:73.1,timestamp:Date.now(),accuracyMeters:2000,emulated:true}).execute('test',{},undefined);assert.match(JSON.stringify(result),/emulator configured location/);assert.match(JSON.stringify(result),/accuracyMeters/);
});
