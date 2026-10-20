import {PGlite} from '../build/pg-test/node_modules/@electric-sql/pglite/dist/index.js';
import fs from 'node:fs';
const db=new PGlite();await db.exec('CREATE ROLE anon;CREATE ROLE authenticated;');
for(const f of ['001_schema.sql','002_api.sql','005_completion_dates.sql','006_email_admission.sql','007_email_session_revocation.sql']) await db.exec(fs.readFileSync('cloudbase/sql/'+f,'utf8'));
await db.exec("INSERT INTO wy_private.members(user_id) VALUES('member-a'),('member-b');");
const results=[];const now=Math.floor(Date.now()/1000);
async function as(uid,iat,sql) {await db.exec('RESET ROLE');await db.query("SELECT set_config('request.jwt.claims',$1,false)",[JSON.stringify({sub:uid,...(iat===null?{}:{iat})})]);await db.exec('SET ROLE authenticated');return db.query(sql);}
function check(name,ok) {if(!ok)throw Error(name);results.push({name,pass:true});}
async function rejects(name,fn){try {await fn();}catch {check(name,true);return;}throw Error(name);}
check('legacy sessions work before any revocation',(await as('member-a',null,'SELECT public.wy_uid() AS u')).rows[0].u==='member-a');
await rejects('missing issue time cannot revoke sessions',()=>as('member-a',null,'SELECT public.wy_revoke_old_sessions()'));
check('current verified session can revoke older sessions',(await as('member-a',now-20,'SELECT public.wy_revoke_old_sessions() AS r')).rows[0].r.revoked);
await rejects('older business reads denied',()=>as('member-a',now-30,'SELECT public.wy_snapshot()'));
await rejects('same-second access token denied',()=>as('member-a',now-20,'SELECT public.wy_uid()'));
await rejects('revoked session cannot regain admission',()=>as('member-a',now-30,"SELECT public.wy_friend_invite('join','', '测试')"));
await rejects('revoked session cannot read account status',()=>as('member-a',now-30,'SELECT public.wy_account_status()'));
await rejects('revoked session cannot roll back cutoff',()=>as('member-a',now-30,'SELECT public.wy_revoke_old_sessions()'));
check('fresh login after cutoff restores same UID',(await as('member-a',now,'SELECT public.wy_uid() AS u')).rows[0].u==='member-a');
check('another member remains signed in',(await as('member-b',now-100,'SELECT public.wy_uid() AS u')).rows[0].u==='member-b');
check('unadmitted user does not get admission via recovery',(await as('unadmitted',now,'SELECT public.wy_revoke_old_sessions() AS r')).rows[0].r.revoked&&!(await as('unadmitted',now,'SELECT public.wy_account_status() AS r')).rows[0].r.admitted);
await rejects('ordinary account cannot edit cutoff directly',()=>as('member-b',now,'UPDATE wy_private.members SET auth_iat_min=0'));
await db.exec('RESET ROLE');await db.exec(fs.readFileSync('cloudbase/sql/007_email_session_revocation.sql','utf8'));
await rejects('migration rerun preserves cutoff',()=>as('member-a',now-30,'SELECT public.wy_uid()'));
await db.exec('RESET ROLE');await db.exec(fs.readFileSync('cloudbase/sql/006_email_admission.sql','utf8'));
await rejects('admission migration rerun cannot undo session revocation',()=>as('member-a',now-30,'SELECT public.wy_uid()'));
await db.close();const report={passed:results.length,tests:results};fs.writeFileSync('build/email-auth/session-sql-tests.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report));
