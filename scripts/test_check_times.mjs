import {PGlite} from '../build/pg-test/node_modules/@electric-sql/pglite/dist/index.js';
import fs from 'node:fs';import crypto from 'node:crypto';
const db=new PGlite();const results=[];
await db.exec('CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role BYPASSRLS;');
for(const n of ['001_schema.sql','002_api.sql','005_completion_dates.sql','006_email_admission.sql','007_email_session_revocation.sql']) await db.exec(fs.readFileSync('cloudbase/sql/'+n,'utf8'));
await db.exec("INSERT INTO wy_private.members(user_id) VALUES ('time-a'),('time-b'),('time-c')");
async function rpc(uid,name,args={}) {await db.exec('RESET ROLE');await db.query("SELECT set_config('request.jwt.claims',$1,false)",[JSON.stringify({sub:uid})]);await db.exec('SET ROLE authenticated');const keys=Object.keys(args);return (await db.query(`SELECT public.${name}(${keys.map((x,i)=>x+' => $'+(i+1)).join(',')}) AS data`,Object.values(args))).rows[0].data;}
function ok(test,value) {if(!value) throw Error(test);results.push({test,pass:true})}
async function rejects(test,fn) {try {await fn()}catch {ok(test,true);return}throw Error(test)}
const apply=(uid,payload,op=crypto.randomUUID())=>rpc(uid,'wy_apply',{op_id:op,payload});
for(const u of ['time-a','time-b','time-c']) await rpc(u,'wy_snapshot',{nickname:u});
const inv=await rpc('time-a','wy_pair_action',{action:'create'});await rpc('time-b','wy_pair_action',{action:'join',value:inv.code});
const pending=(await rpc('time-a','wy_snapshot')).invites[0];const pair=(await rpc('time-a','wy_pair_action',{action:'confirm',value:pending.id})).pair_id;
const today=(await rpc('time-a','wy_snapshot')).server_day;const yesterday=new Date(Date.parse(today+'T12:00:00Z')-86400000).toISOString().slice(0,10);
const stamp=yesterday+'T10:19:32+08:00';
async function task(kind='todo') {const id=crypto.randomUUID();await apply('time-a',{type:'task',id,expected:0,title:'Controlled timestamp fixture',kind,visibility:'partner',pair_id:pair,weekdays:[1,2,3,4,5,6,7]});return id;}
const legacy=await task();await apply('time-a',{type:'check',id:legacy,expected:0,day:'1970-01-01',done:true,completed_on:today,pair_id:pair});
await db.exec('RESET ROLE');await db.exec(fs.readFileSync('cloudbase/sql/008_check_times.sql','utf8'));
let snap=await rpc('time-a','wy_snapshot');ok('migration does not invent old completion times',snap.checks.find(c=>c.task_id===legacy).completed_at===null);
const id=await task();const body={type:'check',id,expected:0,day:'1970-01-01',done:true,completed_on:yesterday,recorded_at:stamp,completed_at:stamp,pair_id:pair};const op=crypto.randomUUID();
await apply('time-a',body,op);await apply('time-a',body,op);
let row=(await rpc('time-b','wy_snapshot')).checks.find(c=>c.task_id===id);
ok('offline completion timestamp visible to partner unchanged',Date.parse(row.completed_at)===Date.parse(stamp));ok('receipt dedup preserves timestamp and revision',row.revision===1);ok('completion day stays actual offline day',row.completed_on===yesterday);
ok('third user cannot read shared timestamp',!(await rpc('time-c','wy_snapshot')).checks.some(c=>c.task_id===id));
await apply('time-a',{...body,expected:1,done:false,recorded_at:today+'T00:00:00+08:00',completed_on:today,completed_at:null});
row=(await rpc('time-a','wy_snapshot')).checks.find(c=>c.task_id===id);ok('undo clears completion timestamp',!row.done && row.completed_at===null && row.completed_on===null);
await apply('time-a',{...body,expected:2,completed_on:today,recorded_at:today+'T00:00:00+08:00'});
row=(await rpc('time-a','wy_snapshot')).checks.find(c=>c.task_id===id);ok('recheck uses new actual action time',Date.parse(row.completed_at)===Date.parse(today+'T00:00:00+08:00'));
const habit=await task('habit');await apply('time-a',{...body,id:habit,day:yesterday});row=(await rpc('time-a','wy_snapshot')).checks.find(c=>c.task_id===habit);ok('past offline habit accepted with matching actual timestamp',row.day===yesterday && Date.parse(row.completed_at)===Date.parse(stamp));
await rejects('old unstamped past-day habit remains rejected',()=>apply('time-a',{...body,id:habit,day:yesterday,expected:1,recorded_at:null,completed_at:null}));
await rejects('mismatched completion date rejected',()=>apply('time-a',{...body,id:habit,day:yesterday,expected:1,recorded_at:today+'T00:00:00+08:00'}));
await rejects('future timestamp rejected',()=>apply('time-a',{...body,id:habit,expected:1,day:yesterday,recorded_at:'2099-01-01T00:00:00Z'}));
const shared=await task('shared');for(const u of ['time-a','time-b']) await apply(u,{...body,id:shared,day:yesterday});
row=(await rpc('time-a','wy_snapshot')).checks.filter(c=>c.task_id===shared);ok('shared completion remains two separate users',row.length===2 && row.every(c=>Date.parse(c.completed_at)===Date.parse(stamp)));
await apply('time-a',{type:'check',id:legacy,expected:1,day:'1970-01-01',done:false,pair_id:pair});ok('old client without timestamps still works',!(await rpc('time-a','wy_snapshot')).checks.find(c=>c.task_id===legacy).done);
await db.close();fs.mkdirSync('build/sql-tests',{recursive:true});fs.writeFileSync('build/sql-tests/v182-times.json',JSON.stringify({engine:'PGlite PostgreSQL; not native CloudBase HTTP authentication',tests:results},null,2));console.log(JSON.stringify({passed:results.length,results}));
