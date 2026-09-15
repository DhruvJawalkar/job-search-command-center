import assert from 'node:assert/strict';
import {readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';
const root=path.resolve(process.argv[2]??'');
const instance=JSON.parse(await readFile(path.join(root,'instance.json'),'utf8'));
assert.equal(instance.syntheticOnly,true);assert.equal(instance.api,'http://127.0.0.1:8081');
const base=instance.api+'/api/v1';
const count=(await (await fetch(base+'/opportunities')).json()).length;
// A local single-user diagnostic budget, not a production SLA or load test.
const budgetMs=750, samples=20, results=[];
for(const route of ['/opportunities/intelligence','/dashboard/morning','/applications','/skills/overview','/skills/market-signals','/preparation/overview','/reviews/weekly']){
 await fetch(base+route).then(r=>r.arrayBuffer()); // warm-up, excluded
 const times=[];let bytes=0;
 for(let i=0;i<samples;i++){
  const start=performance.now();const response=await fetch(base+route);assert.ok(response.ok,route);
  bytes=(await response.arrayBuffer()).byteLength;times.push(performance.now()-start);
 }
 times.sort((a,b)=>a-b);
 const result={route,samples,bytes,p50Ms:+times[9].toFixed(1),p95Ms:+times[18].toFixed(1),maxMs:+times[19].toFixed(1)};
 results.push({...result,withinBudget:result.p95Ms<=budgetMs});console.log(JSON.stringify(results.at(-1)));
}
await writeFile(path.join(root,`performance-${count}.json`),JSON.stringify({measuredAt:new Date().toISOString(),opportunities:count,budgetMs,mode:'warm sequential single-user HTTP, body fully consumed',results},null,2));
