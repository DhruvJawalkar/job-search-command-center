import assert from 'node:assert/strict';
import {readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
const root=path.resolve(process.argv[2] ?? '');
const instance=JSON.parse(await readFile(path.join(root,'instance.json'),'utf8'));
assert.equal(instance.syntheticOnly,true); assert.equal(instance.api,'http://127.0.0.1:8081');
const base=instance.api+'/api/v1', results=JSON.parse(await readFile(path.join(root,'api-acceptance.json'),'utf8').catch(()=>'[]'));
async function api(route,method='GET',body,expected){
 const response=await fetch(base+route,{method,headers:body instanceof FormData?{}:{'Content-Type':'application/json'},body:body==null?undefined:body instanceof FormData?body:JSON.stringify(body)});
 const text=await response.text(); if(expected){assert.equal(response.status,expected,text);return text;}
 assert.ok(response.ok,`${method} ${route}: ${response.status} ${text}`); return text?JSON.parse(text):null;
}
async function check(name,fn){if(results.some(x=>x.name===name&&x.passed)){console.log('PREVIOUS PASS '+name);return;}await fn();results.push({name,passed:true});console.log('PASS '+name);await writeFile(path.join(root,'api-acceptance.json'),JSON.stringify(results,null,2));}
const openings=await api('/opportunities'); const op=openings.find(x=>x.companyName==='C5 Fixture 00');assert.ok(op);
const resume=(await api('/resumes')).find(x=>x.name==='C5 Synthetic Resume'); assert.ok(resume);
let application=(await api('/applications')).find(x=>x.opportunityId===op.id);
await check('Replay imports adds no duplicate openings or observations',async()=>{const r=await api('/imports/daily-high-fit','POST');assert.equal(r.filesFailed,0);assert.equal(r.opportunitiesCreated,0);assert.equal(r.filesUnchanged,2);assert.equal((await api('/opportunities')).length,24);});
await check('Workbook summaries do not produce skill observations',async()=>{await api('/skills/extractions','POST',{opportunityIds:openings.map(x=>x.id),includeArchived:false});assert.equal((await api('/skills/observations')).length,0);});
await check('Application stores immutable PDF/JD bytes and optional follow-up defaults',async()=>{
 // Generate a valid one-page PDF with cross-reference offsets; no personal resume data.
 const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>','<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',null,'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'];
 const stream='BT /F1 16 Tf 72 720 Td (C5 Synthetic Resume - Test Only) Tj ET\n'; objects[3]=`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}endstream`;
 let pdf='%PDF-1.4\n',offsets=[0];objects.forEach((body,i)=>{offsets.push(Buffer.byteLength(pdf));pdf+=`${i+1} 0 obj\n${body}\nendobj\n`;});
 const xref=Buffer.byteLength(pdf);pdf+='xref\n0 6\n0000000000 65535 f \n'+offsets.slice(1).map(n=>`${String(n).padStart(10,'0')} 00000 n \n`).join('')+`trailer\n<< /Root 1 0 R /Size 6 >>\nstartxref\n${xref}\n%%EOF\n`;
 await writeFile(path.join(root,'c5-resume.pdf'),pdf);
 const form=new FormData();form.set('request',new Blob([JSON.stringify({resumeVariantId:resume.id,stage:'APPLIED',channel:'C5 test',nextAction:null,nextActionAt:null})],{type:'application/json'}));form.set('resumeFile',new Blob([pdf],{type:'application/pdf'}),'c5-resume.pdf');form.set('jobDescription','C5 full description: Java is required. Kubernetes is preferred.');
 application=(await api('/applications')).find(x=>x.opportunityId===op.id) ?? await api(`/opportunities/${op.id}/applications`,'POST',form);assert.ok(['APPLIED','RECRUITER_SCREEN'].includes(application.stage));assert.equal(application.followUpActive,false);assert.equal(application.artifacts.length,2);
 const stored=application.artifacts.find(a=>a.artifactType==='RESUME_PDF'); assert.ok(stored);const bytes=await readFile(path.join(root,stored.storedPath));assert.equal(createHash('sha256').update(bytes).digest('hex'),stored.contentHash);
 await writeFile(path.join(root,'browser-fixtures.json'),JSON.stringify({applicationId:application.id,opportunityId:op.id,resumeId:resume.id},null,2));
});
await check('Follow-up opt-in/drop and manual stage transition preserve history',async()=>{
 const nextActionAt=new Date(Date.now()+86400000).toISOString();let a=await api(`/applications/${application.id}/follow-up`,'PATCH',{drop:false,nextAction:'C5 follow-up',nextActionAt});assert.equal(a.followUpActive,true);
 a=await api(`/applications/${application.id}/follow-up`,'PATCH',{drop:true});assert.equal(a.followUpActive,false);
 a=await api(`/applications/${application.id}/transitions`,'POST',{toStage:'RECRUITER_SCREEN',note:'C5 explicit stage change'});assert.equal(a.stage,'RECRUITER_SCREEN');assert.ok(a.events.length>=2);
});
await check('Outreach follow-up count and closed state retain the record',async()=>{
 const o=await api('/outreach','POST',{opportunityId:op.id,newContact:{fullName:'C5 Test Contact',companyName:'C5 Fixture 00',relationshipStrength:'ACQUAINTANCE'},outreachType:'REFERRAL_REQUEST',status:'SENT',channel:'Test only',messageSummary:'Synthetic outreach; never sent externally',followUpAt:new Date(Date.now()+86400000).toISOString()});
 const follow=await api(`/outreach/${o.id}/follow-ups`,'POST',{nextFollowUpAt:new Date(Date.now()+172800000).toISOString(),notes:'Synthetic follow-up'});assert.equal(follow.followUpCount,1);
 const closed=await api(`/outreach/${o.id}`,'PATCH',{status:'CLOSED',outcome:'C5 dropped',notes:'Retain history'});assert.equal(closed.status,'CLOSED');assert.ok((await api('/outreach')).some(x=>x.id===o.id));
});
await check('Preparation task/session persists and early sprint close is rejected',async()=>{
 const track=await api('/preparation/tracks','POST',{name:'C5 Coding Practice',category:'DATA_STRUCTURES',displayOrder:1});
 const story=await api(`/preparation/tracks/${track.id}/milestones`,'POST',{title:'C5 concurrency story',description:'Deliver tested queue',displayOrder:1});
 const task=await api(`/preparation/milestones/${story.id}/items`,'POST',{title:'C5 implement bounded queue',priority:2,estimatedMinutes:60,displayOrder:1});assert.equal(task.status,'BACKLOG');
 const sprint=(await api('/preparation/overview')).activeSprint ?? await api('/preparation/sprints','POST');await api(`/preparation/sprints/current/items/${task.id}`,'POST');await api(`/preparation/items/${task.id}`,'PATCH',{status:'IN_PROGRESS'});
 await api(`/preparation/items/${task.id}/sessions`,'POST',{sessionType:'DRILL',durationMinutes:25,resultSummary:'Tested queue bounds',practicedAt:new Date().toISOString()});assert.equal((await api(`/preparation/items/${task.id}/sessions`)).length,1);
 await api(`/preparation/sprints/${sprint.id}/close`,'POST',null,409);
});
console.log('C5 API checks completed. Synthetic artifacts retained in '+root);
