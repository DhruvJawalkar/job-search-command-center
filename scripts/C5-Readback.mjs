import assert from 'node:assert/strict';
import {readFile,writeFile,readdir} from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
const root=path.resolve(process.argv[2]??'');
const config=JSON.parse(await readFile(path.join(root,'instance.json'),'utf8'));
assert.equal(config.syntheticOnly,true);assert.equal(config.api,'http://127.0.0.1:8081');
const base=config.api+'/api/v1';
async function api(route,method='GET',body){const r=await fetch(base+route,{method,headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});assert.ok(r.ok,`${route}: ${r.status} ${await r.clone().text()}`);return r.status===204?null:r.json();}
const apps=await api('/applications');assert.equal(apps.length,2);
for(const app of apps)for(const artifact of app.artifacts){
 const target=path.resolve(root,artifact.storedPath);assert.ok(target.startsWith(root+path.sep));
 assert.equal(createHash('sha256').update(await readFile(target)).digest('hex'),artifact.contentHash);
}
const prep=await api('/preparation/overview');assert.equal(prep.stats.minutesThisWeek,60);
const sessions=prep.recentSessions;assert.ok(sessions.some(s=>s.resultSummary==='C5 browser session: verified queue boundaries'));
const observations=await api('/skills/observations');assert.ok(observations.some(o=>o.reviewStatus==='ACCEPTED'&&o.strength==='REQUIRED'));
const plans=await api('/skills/backlog');assert.ok(JSON.stringify(plans).includes('BACKLOG'));
const notes=await readdir(path.join(root,'notes'));assert.ok(notes.length>0);
assert.ok((await readFile(path.join(root,'notes',notes[0]),'utf8')).includes('C5 synthetic scratchpad'));
const rounds=(await Promise.all(apps.map(a=>api(`/applications/${a.id}/interviews`)))).flat();assert.ok(rounds.some(r=>r.status==='COMPLETED'&&r.outcome==='ADVANCED'));
const weekly=await api('/reviews/weekly');assert.ok(JSON.stringify(weekly).includes('deliberate practice and traceable evidence'));
if(process.argv.includes('--closure-fixtures')){
 const story=prep.tracks[0].milestones[0];
 for(const [title,status] of [['C5 closure open','READY'],['C5 closure done','COMPLETED'],['C5 closure review','IN_REVIEW']]){
  if(prep.tracks.flatMap(t=>t.milestones.flatMap(m=>m.items)).some(i=>i.title===title))continue;
  const item=await api(`/preparation/milestones/${story.id}/items`,'POST',{title,priority:3,estimatedMinutes:30,displayOrder:3});
  await api(`/preparation/sprints/current/items/${item.id}`,'POST');
  await api(`/preparation/items/${item.id}`,'PATCH',{status});
 }
}
await writeFile(path.join(root,'readback.json'),JSON.stringify({verifiedAt:new Date().toISOString(),applications:apps.length,artifacts:apps.flatMap(a=>a.artifacts).length,sessionMinutes:60,notes:notes.length,completedRounds:rounds.filter(r=>r.status==='COMPLETED').length,checksPassed:true},null,2));
console.log('PASS application artifact hashes, practice sessions, accepted skill, backlog, saved notes, completed interview and weekly reflection');
