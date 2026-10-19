// HTTP cloud function. Temporary execution-role credentials never leave this runtime.
const http=require('node:http');
const crypto=require('node:crypto');
const CloudBase=require('@cloudbase/manager-node');
const envId='withyou-sync-d0gg468vheead040b';
function manager() {
 return CloudBase.init({envId,region:'ap-shanghai',secretId:process.env.TENCENTCLOUD_SECRETID,secretKey:process.env.TENCENTCLOUD_SECRETKEY,token:process.env.TENCENTCLOUD_SESSIONTOKEN});
}
function sha(value) {return crypto.createHash('sha256').update(value).digest('hex');}
function literal(value) {return "'"+String(value).replaceAll("'","''")+"'";}
function valid(input) {
 return /^[A-Za-z0-9][A-Za-z0-9_.-]{1,47}$/.test(input.username||'') &&
  typeof input.password==='string' && input.password.length>=8 && input.password.length<=32 && /^[A-Za-z0-9]/.test(input.password) &&
  [/[A-Z]/,/[a-z]/,/[0-9]/,/[()!@#$%^&*|?><_-]/].filter(r=>r.test(input.password)).length>=3 &&
  typeof input.nickname==='string' && input.nickname.trim().length>=2 && input.nickname.trim().length<=32 &&
  /^[a-f0-9]{48}$/i.test(input.activation||'');
}
async function register(input,ip,app=manager()) {
 if(!valid(input)) return {status:400,body:{message:'INVALID_REGISTRATION'}};
 const hash=sha(input.activation.toLowerCase());
 const limitKey=sha(envId+':'+ip);
 const reservation=await app.database.executePGSql({Sql:`SELECT wy_private.reserve_registration(${literal(hash)},${literal(input.username)},${literal(limitKey)}) AS allowed`});
 const row=reservation.Rows?.[0];
 const allowed=Array.isArray(row)?row[0]:(typeof row==='string'?JSON.parse(row)[0]:false);
 if(allowed!==true && allowed!=='t' && allowed!=='true') return {status:403,body:{message:'ACTIVATION_INVALID'}};
 try {
  await app.user.createUser({name:input.username,password:input.password,nickName:input.nickname.trim(),type:'externalUser',userStatus:'ACTIVE'});
 } catch {
  // A previous create may have committed while its response was lost. Never reset an existing password.
  const response=await fetch(`https://${envId}.api.tcloudbasegateway.com/auth/v1/signin`,{method:'POST',headers:{'Content-Type':'application/json','x-device-id':'withyou-registration'},body:JSON.stringify({username:input.username,password:input.password})});
  if(!response.ok) return {status:409,body:{message:'REGISTER_RETRY_OR_NAME_TAKEN'}};
 }
 await app.database.executePGSql({Sql:`UPDATE wy_private.registration_slots SET state='created' WHERE code_hash=${literal(hash)} AND username=${literal(input.username)}`});
 return {status:200,body:{ok:true}};
}
module.exports={valid,register};
if(require.main===module) {
 http.createServer(async(req,res)=>{
  res.setHeader('Content-Type','application/json; charset=utf-8');res.setHeader('Cache-Control','no-store');
  if(req.method!=='POST') {res.writeHead(405);res.end('{}');return;}
  let bytes=0;const chunks=[];
  try {
   for await(const chunk of req) {bytes+=chunk.length;if(bytes>8192) throw Error('TOO_LARGE');chunks.push(chunk);}
   const input=JSON.parse(Buffer.concat(chunks).toString('utf8'));
   const output=await register(input,req.socket.remoteAddress||'unknown');
   res.writeHead(output.status);res.end(JSON.stringify(output.body));
  } catch {res.writeHead(503);res.end(JSON.stringify({message:'REGISTER_TEMPORARILY_UNAVAILABLE'}));}
 }).listen(9000,'0.0.0.0');
}
