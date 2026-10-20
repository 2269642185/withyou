import {PGlite} from '../build/pg-test/node_modules/@electric-sql/pglite/dist/index.js';
import fs from 'node:fs';
const db=new PGlite();const results=[];
await db.exec('CREATE ROLE anon;CREATE ROLE authenticated;CREATE ROLE service_role BYPASSRLS;');
for(const f of ['001_schema.sql','002_api.sql','005_completion_dates.sql']) await db.exec(fs.readFileSync('cloudbase/sql/'+f,'utf8'));
await db.exec("INSERT INTO public.wy_profiles VALUES('legacy','小杏');");
const migration=fs.readFileSync('cloudbase/sql/006_email_admission.sql','utf8');await db.exec(migration);
async function admin(sql,args=[]) {await db.exec('RESET ROLE');return db.query(sql,args);}
async function rpc(uid,name,args={}) {
 await db.exec('RESET ROLE');await db.query("SELECT set_config('request.jwt.claims',$1,false)",[JSON.stringify({sub:uid})]);await db.exec('SET ROLE authenticated');
 const keys=Object.keys(args);return (await db.query(`SELECT public.${name}(${keys.map((k,i)=>k+' => $'+(i+1)).join(',')}) AS data`,Object.values(args))).rows[0].data;
}
function check(name,value) {if(!value) throw Error(name);results.push({name,pass:true});}
async function rejects(name,action) {try {await action();} catch {check(name,true);return;}throw Error('Expected rejection: '+name);}
check('legacy account admitted without migration of user identity',(await rpc('legacy','wy_account_status')).admitted);
check('unknown account sees only its own non-admitted status',!(await rpc('new','wy_account_status')).admitted);
await rejects('unadmitted user cannot access business snapshot',()=>rpc('new','wy_snapshot'));
await rejects('closed registration rejects invite creation',()=>rpc('legacy','wy_friend_invite',{action:'create'}));
await admin('UPDATE wy_private.email_registration SET registration_open=true');
await rejects('unadmitted user cannot invite friends',()=>rpc('new','wy_friend_invite',{action:'create'}));
const invite=await rpc('legacy','wy_friend_invite',{action:'create'});
check('invite has 128-bit random token and 72-hour expiry',/^[a-f0-9]{32}$/.test(invite.code)&&invite.expires_in===259200);
check('only token hash stored',(await admin('SELECT code_hash FROM wy_private.friend_invites')).rows.every(r=>r.code_hash.length===64&&r.code_hash!==invite.code));
await rejects('unknown invitation rejected',()=>rpc('new','wy_friend_invite',{action:'join',code:'0'.repeat(32),nickname:'新朋友'}));
const joined=await rpc('new','wy_friend_invite',{action:'join',code:invite.code,nickname:'新朋友'});
check('valid friend invite admits same signed-in UID',joined.admitted&&joined.nickname==='新朋友');
check('admission does not auto-pair',(await admin('SELECT count(*)::int AS n FROM public.wy_pairs')).rows[0].n===0);
check('new admitted user can read own snapshot',(await rpc('new','wy_snapshot',{nickname:'新朋友'})).tasks.length===0);
check('retry of admission is idempotent',(await rpc('new','wy_friend_invite',{action:'join',code:invite.code,nickname:'新朋友'})).admitted);
await rejects('one-use invitation cannot admit another UID',()=>rpc('other','wy_friend_invite',{action:'join',code:invite.code,nickname:'另一个人'}));
const expired=await rpc('legacy','wy_friend_invite',{action:'create'});
await admin("UPDATE wy_private.friend_invites SET expires_at=now()-interval '1 second' WHERE used_by IS NULL");
await rejects('expired invitation rejected',()=>rpc('other','wy_friend_invite',{action:'join',code:expired.code,nickname:'另一个人'}));
const capInvite=await rpc('legacy','wy_friend_invite',{action:'create'});
await admin('UPDATE wy_private.email_registration SET member_limit=2');
await rejects('small-group member cap enforced server-side',()=>rpc('other','wy_friend_invite',{action:'join',code:capInvite.code,nickname:'另一个人'}));
await rpc('legacy','wy_friend_invite',{action:'create'});await rpc('legacy','wy_friend_invite',{action:'create'});
await rejects('invitation creation rate limit enforced',()=>rpc('legacy','wy_friend_invite',{action:'create'}));
await rpc('new','wy_account_status');
await rejects('ordinary user cannot read private admission table',()=>db.query('SELECT * FROM wy_private.members'));
await rejects('ordinary user cannot open registration',()=>db.query('UPDATE wy_private.email_registration SET registration_open=true'));
await admin('UPDATE wy_private.email_registration SET registration_open=false');
check('global admission stop preserves existing account access',(await rpc('new','wy_snapshot')).visible_ids.length===0);
await rejects('global stop blocks new admission',()=>rpc('other','wy_friend_invite',{action:'join',code:capInvite.code,nickname:'另一个人'}));
await admin('SELECT 1');await db.exec(migration);
check('migration rerun preserves global closed setting',!(await rpc('legacy','wy_account_status')).registration_open);
await rejects('anonymous cannot read account status',async()=>{await db.exec('RESET ROLE;SET ROLE anon');await db.query('SELECT public.wy_account_status()');});
await db.close();const report={passed:results.length,tests:results};fs.mkdirSync('build/email-auth',{recursive:true});fs.writeFileSync('build/email-auth/admission-tests.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report));
