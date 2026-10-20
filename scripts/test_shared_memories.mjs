import nodePath from 'node:path';
import {PGlite} from '../build/pg-test/node_modules/@electric-sql/pglite/dist/index.js';
import fs from 'node:fs';import crypto from 'node:crypto';
const db=new PGlite();const results=[];
await db.exec(`CREATE ROLE anon;CREATE ROLE authenticated;CREATE SCHEMA storage;
CREATE TABLE storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
CREATE TABLE storage.objects(id uuid default gen_random_uuid(),bucket_id text,name text,metadata jsonb);
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;GRANT USAGE ON SCHEMA storage TO authenticated;GRANT SELECT,INSERT,UPDATE,DELETE ON storage.objects TO authenticated;`);
for(const f of ['001_schema.sql','002_api.sql','004_shared_memories.sql','005_completion_dates.sql']) await db.exec(fs.readFileSync('cloudbase/sql/'+f,'utf8'));
async function as(uid,sql,args=[]) {await db.exec('RESET ROLE');await db.query("select set_config('request.jwt.claims',$1,false)",[JSON.stringify({sub:uid})]);await db.exec('SET ROLE authenticated');return db.query(sql,args);}
async function rpc(uid,name,args={}) {const keys=Object.keys(args);return (await as(uid,`select public.${name}(${keys.map((k,i)=>k+'=> $'+(i+1)).join(',')}) x`,Object.values(args))).rows[0].x;}
function ok(name,val) {if(!val) throw Error(name);results.push({test:name,pass:true});}
async function denied(name,fn){try{await fn();}catch {ok(name,true);return;}throw Error('expected denial: '+name);}
const inv=await rpc('a','wy_pair_action',{action:'create'});await rpc('b','wy_pair_action',{action:'join',value:inv.code});const pi=(await rpc('a','wy_snapshot')).invites[0].id;const pair=(await rpc('a','wy_pair_action',{action:'confirm',value:pi})).pair_id;
const id=crypto.randomUUID(),path='a/'+id+'/'+'a'.repeat(64)+'.jpg';
await as('a',"insert into storage.objects(bucket_id,name) values('withyou-memories',$1)",[path]);
ok('unpublished photo hidden from partner',(await as('b',"select * from storage.objects")).rows.length===0);
await denied('third user cannot forge upload',()=>as('c',"insert into storage.objects(bucket_id,name) values('withyou-memories',$1)",[path]));
const payload={id,title:'周年测试',nickname:'你',date:'2024-02-29',mode:'INCLUSIVE',color:2,template:'polaroid',pinned:false,appearance:{tone:'AUTO',overlay:0.6,background:'#D9E2D1',days:'#354D33'}};
const item={id,pair_id:pair,payload,photo_path:path,expected:0,active:true};const op=crypto.randomUUID();
const saved=await rpc('a','wy_memory_apply',{op_id:op,item});
ok('appearance snapshot commits with photo',saved.revision===1);
ok('idempotent retry no duplicate version',(await rpc('a','wy_memory_apply',{op_id:op,item})).revision===1);
ok('partner reads complete appearance',(await rpc('b','wy_memory_snapshot')).memories[0].payload.appearance.days==='#354D33');
ok('partner reads authenticated photo',(await as('b',"select * from storage.objects")).rows.length===1);
ok('third account cannot read shared row',(await as('c',"select * from public.wy_memories")).rows.length===0);
ok('third account cannot read photo',(await as('c',"select * from storage.objects")).rows.length===0);
await denied('partner cannot overwrite creator',()=>rpc('b','wy_memory_apply',{op_id:crypto.randomUUID(),item}));
ok('stale update conflicts',(await rpc('a','wy_memory_apply',{op_id:crypto.randomUUID(),item})).conflict);
await denied('null title cannot break peer decoding',()=>rpc('a','wy_memory_apply',{op_id:crypto.randomUUID(),item:{...item,expected:1,payload:{...payload,title:null}}}));
await denied('local photo path rejected',()=>rpc('a','wy_memory_apply',{op_id:crypto.randomUUID(),item:{...item,expected:1,photo_path:'file:///data/photo.jpg'}}));
await rpc('a','wy_memory_apply',{op_id:crypto.randomUUID(),item:{...item,expected:1,active:false}});
ok('unshare hides row',(await rpc('b','wy_memory_snapshot')).memories.length===0);
ok('unshare immediately denies photo',(await as('b',"select * from storage.objects")).rows.length===0);
await rpc('a','wy_memory_apply',{op_id:crypto.randomUUID(),item:{...item,expected:2}});
await rpc('a','wy_pair_action',{action:'unlink',value:pair});
ok('unlink hides anniversary',(await rpc('b','wy_memory_snapshot')).memories.length===0);
ok('unlink hides cloud photo',(await as('b',"select * from storage.objects")).rows.length===0);
const todo=crypto.randomUUID();
await rpc('a','wy_apply',{op_id:crypto.randomUUID(),payload:{id:todo,type:'task',expected:0,kind:'todo',visibility:'private',title:'离线待办',weekdays:[1,2,3,4,5,6,7]}});
await rpc('a','wy_apply',{op_id:crypto.randomUUID(),payload:{id:todo,type:'check',expected:0,day:'1970-01-01',completed_on:'2026-10-18',done:true}});
ok('offline one-time completion preserves actual completed date',(await as('a','select completed_on::text as day from public.wy_checks where task_id=$1',[todo])).rows[0].day==='2026-10-18');
const output=process.argv[2]||'build/sql-tests/shared-sql-tests.json';fs.mkdirSync(nodePath.dirname(output),{recursive:true});fs.writeFileSync(output,JSON.stringify({engine:'PostgreSQL PGlite',tests:results},null,2));console.log(JSON.stringify({passed:results.length}));await db.close();
