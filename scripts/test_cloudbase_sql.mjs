import nodePath from 'node:path';
// Executes actual PostgreSQL functions and RLS in embedded PostgreSQL, not a JS permissions mock.
import {PGlite} from '../build/pg-test/node_modules/@electric-sql/pglite/dist/index.js';
import fs from 'node:fs';
import crypto from 'node:crypto';
const db=new PGlite();
await db.exec('CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role BYPASSRLS;');
for(const name of ['001_schema.sql','002_api.sql','005_completion_dates.sql']) await db.exec(fs.readFileSync(new URL('../cloudbase/sql/'+name,import.meta.url),'utf8'));
const results=[];
async function user(uid,query,args=[]) {
 await db.exec('RESET ROLE');
 await db.query("SELECT set_config('request.jwt.claims',$1,false)",[JSON.stringify({sub:uid})]);
 await db.exec('SET ROLE authenticated');
 return await db.query(query,args);
}
async function rpc(uid,name,args) {
 const fields=Object.keys(args);return (await user(uid,`select public.${name}(${fields.map((name,i)=>name+' => $'+(i+1)).join(',')}) as data`,Object.values(args))).rows[0].data;
}
function check(test,value) {if(!value) throw Error(test);results.push({test,pass:true});}
async function rejects(test,fn) {try {await fn();}catch {check(test,true);return;}throw Error('Expected rejection: '+test);}
const uidA='user-a',uidB='user-b',uidC='user-c';
for(const uid of [uidA,uidB,uidC]) await rpc(uid,'wy_snapshot',{nickname:uid});
const invite=await rpc(uidA,'wy_pair_action',{action:'create'});
await rejects('self pairing rejected',()=>rpc(uidA,'wy_pair_action',{action:'join',value:invite.code}));
await rpc(uidB,'wy_pair_action',{action:'join',value:invite.code});
check('join waits for owner confirmation',!(await rpc(uidB,'wy_snapshot',{})).pair_id);
await rejects('used invite rejected',()=>rpc(uidC,'wy_pair_action',{action:'join',value:invite.code}));
const pending=(await rpc(uidA,'wy_snapshot',{})).invites[0];
await rejects('third user cannot confirm',()=>rpc(uidC,'wy_pair_action',{action:'confirm',value:pending.id}));
const pair=(await rpc(uidA,'wy_pair_action',{action:'confirm',value:pending.id})).pair_id;
await rejects('cannot create another active pairing',()=>rpc(uidA,'wy_pair_action',{action:'create'}));
const id=crypto.randomUUID(),privateId=crypto.randomUUID();
function task(id,visibility='partner',kind='shared') {return {type:'task',id,kind,visibility,pair_id:visibility==='partner'?pair:'',title:'共同读书',notes:'测试',weekdays:[1,2,3,4,5,6,7],expected:0};}
async function apply(uid,payload,op=crypto.randomUUID()) {return rpc(uid,'wy_apply',{op_id:op,payload});}
await apply(uidA,task(id));await apply(uidA,task(privateId,'private','habit'));
check('partner sees shared task', (await rpc(uidB,'wy_snapshot',{})).tasks.some(t=>t.id===id));
check('private task invisible to partner', !(await rpc(uidB,'wy_snapshot',{})).tasks.some(t=>t.id===privateId));
check('third user sees no tasks',(await rpc(uidC,'wy_snapshot',{})).tasks.length===0);
check('direct RLS read rejects third-user visibility',(await user(uidC,'select * from public.wy_tasks')).rows.length===0);
await rejects('direct table insert forbidden',()=>user(uidC,"insert into public.wy_profiles values ('user-a','forged')"));
await rejects('partner cannot edit creator task',()=>apply(uidB,{...task(id),expected:1}));
await rejects('forged pair rejected',()=>apply(uidC,task(crypto.randomUUID())));
const day=(await rpc(uidA,'wy_snapshot',{})).server_day;
const mark={type:'check',id,pair_id:pair,day,done:true,expected:0};
const op=crypto.randomUUID();await apply(uidA,mark,op);await apply(uidA,mark,op);
check('duplicate operation applied once',(await rpc(uidA,'wy_snapshot',{})).checks.filter(c=>c.task_id===id).length===1);
await apply(uidB,mark);
check('both users have individual checks',(await rpc(uidA,'wy_snapshot',{})).checks.filter(c=>c.task_id===id && c.done).length===2);
check('stale revision is a conflict',(await apply(uidA,{...task(id),expected:0})).conflict===true);
await rejects('past day rejected',()=>apply(uidA,{...mark,day:'2020-01-01',expected:1}));
const before=await rpc(uidA,'wy_snapshot',{});
await apply(uidA,{...task(id),title:'新版标题',expected:1});
const delta=await rpc(uidB,'wy_snapshot',{task_cursor:before.task_cursor,check_cursor:before.check_cursor,known_pair:pair});
check('incremental snapshot returns modified task',delta.tasks.length===1 && delta.tasks[0].title==='新版标题');
await rpc(uidA,'wy_pair_action',{action:'unlink',value:pair});
check('unlink revokes partner immediately',(await rpc(uidB,'wy_snapshot',{})).tasks.length===0);
await rejects('old-pair check rejected',()=>apply(uidB,{...mark,expected:1}));
await rejects('old-pair task mutation rejected',()=>apply(uidA,{...task(id),expected:2}));
const expired=await rpc(uidC,'wy_pair_action',{action:'create'});
await db.exec("RESET ROLE; UPDATE public.wy_invites SET expires_at=now()-interval '1 second' WHERE owner_id='user-c';");
await rejects('expired invite rejected',()=>rpc(uidB,'wy_pair_action',{action:'join',value:expired.code}));
await rejects('anonymous RPC rejected',async()=>{await db.exec('RESET ROLE;SET ROLE anon');await db.query("select public.wy_snapshot()");});
await db.close();
const output=process.argv[2]||'build/sql-tests/core-sql-tests.json';fs.mkdirSync(nodePath.dirname(output),{recursive:true});fs.writeFileSync(output,JSON.stringify({engine:'PGlite PostgreSQL; CloudBase gateway not simulated as deployed',tests:results},null,2));
console.log(JSON.stringify({passed:results.length,results}));
