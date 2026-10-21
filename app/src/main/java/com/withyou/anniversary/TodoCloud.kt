package com.withyou.anniversary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class TodoCloudError(val status:Int,val reason:String,val stage:SyncStage=SyncStage.UNKNOWN,val code:String="",val requestId:String=""):Exception(when {
    reason.contains("SESSION_CHANGED") -> "登录状态已变化，请重新登录；原账号的待同步记录仍保留"
    needsReauthentication(status,reason,stage) && !reason.contains("SESSION_REVOKED") -> "登录状态已失效，请重新登录；本机待同步记录会保留"
    status==0 -> if(code=="TIMEOUT") "连接超时，本机记录已保留，稍后会重试" else "网络暂时不可用，本机记录已保留，恢复连接后会重试"
    reason.contains("SESSION_REVOKED") -> "账号安全状态已更新，请退出后用新密码登录；本机待同步记录会保留"
    reason.contains("FRIEND_INVITE_REQUIRED") -> "请使用朋友发来的注册链接开通账号"
    reason.contains("INVALID_FRIEND_INVITE") -> "注册链接无效、已使用或已过期，请向朋友获取新链接"
    reason.contains("REGISTRATION_CLOSED") -> "当前暂停新朋友注册，已有账号仍可登录"
    reason.contains("rate_limit") || reason.contains("resource_exhausted") -> "发送或验证太频繁，请稍后重试"
    reason.contains("invalid_verification") || reason.contains("verification_expired") -> "验证码错误或已过期，请检查或重新发送"
    reason.contains("weak_password") -> "密码强度不足，请按页面要求设置"
    reason.contains("invalid_password") -> "当前密码不正确，请检查后重新发送验证码"
    reason.contains("sudo") -> "身份验证已过期，请重新输入当前密码并发送验证码"
    reason.contains("email") && (reason.contains("exist") || reason.contains("bound")) -> "此操作暂时无法完成，请尝试登录或找回密码"
    reason.contains("PAIR_CHANGED") -> "配对关系已变化，旧的共享操作已停止"
    reason.contains("DAY_CHANGED") -> "日期已经变化，请在今天的事项中重新打卡"
    reason.contains("TIME_INVALID") -> "打卡时间与日期不一致，请核对手机时间；本机记录已保留"
    reason.contains("INVALID_INVITE") -> "邀请码无效、已使用或已过期"
    reason.contains("ALREADY_PAIRED") -> "其中一方已经有伙伴，请先解除原配对"
    reason.contains("INVITE_PENDING") -> "你已有待确认的配对申请"
    reason.contains("TOO_FAST") -> "操作太频繁，请稍后重试"
    reason.contains("FORBIDDEN") || status==403 -> "没有权限执行此操作"
    reason.contains("REGISTER_DISABLED") -> "注册服务尚未配置，请联系应用管理员开通"
    reason.contains("ACTIVATION_INVALID") -> "激活码无效、已使用、过期或操作太频繁"
    reason.contains("INVALID_REGISTRATION") -> "请检查用户名、密码格式、昵称和注册激活码"
    reason.contains("REGISTER_RETRY_OR_NAME_TAKEN") -> "用户名已使用或注册暂未完成，请检查后重试"
    reason.contains("invalid_username_or_password") -> "用户名或密码不正确"
    reason.contains("unimplemented") -> "云端尚未开启此登录方式，请联系应用管理员"
    reason.contains("captcha") -> "登录需要额外验证，请稍后重试或联系管理员"
    reason.contains("PGRST202") || reason.contains("PGRST205") || status==404 -> "云端打卡服务尚未部署完成"
    status==401 -> "登录已过期，请重新登录；本机待同步记录会保留"
    else -> "${stage.label}未完成（$status），本机记录已保留，可查看同步详情"
}) {
    val requiresLogin get()=needsReauthentication(status,reason,stage)
}

fun interface TodoTransport { suspend fun call(path:String,body:JSONObject,token:String,method:String):JSONObject }

/** CloudBase HTTP Auth API, not Supabase GoTrue endpoints. No administrator credentials. */
class TodoCloud(private val deviceId:String, private val registrationUrl:String=BuildConfig.CLOUD_REGISTER_URL,private val transport:TodoTransport?=null) {
    private val base="https://${BuildConfig.CLOUD_ENV}.api.tcloudbasegateway.com"
    internal suspend fun request(path:String,body:JSONObject,token:String="",absolute:Boolean=false,method:String="POST"):JSONObject=withContext(Dispatchers.IO) {
        val stage=SyncStage.of(path,body)
        if(transport!=null) {
            try {return@withContext transport.call(path,body,token,method)}
            catch(e:TodoCloudError) {throw if(e.stage==SyncStage.UNKNOWN) TodoCloudError(e.status,e.reason,stage,e.code,e.requestId) else e}
        }
        val address=if(absolute) path else base+path
        val url=URL(address)
        require(url.protocol=="https" && url.userInfo==null) { "云端连接必须使用 HTTPS" }
        val conn=(url.openConnection() as HttpURLConnection).apply {
            requestMethod=method;connectTimeout=12000;readTimeout=15000;instanceFollowRedirects=false;doOutput=method!="GET"
            setRequestProperty("Content-Type","application/json");setRequestProperty("Accept","application/json")
            setRequestProperty("x-device-id",deviceId)
            if(token.isNotBlank()) setRequestProperty("Authorization","Bearer $token")
        }
        try {
            if(method!="GET") conn.outputStream.use {it.write(body.toString().toByteArray())}
            val status=conn.responseCode
            val text=(if(status in 200..299) conn.inputStream else conn.errorStream)?.use {input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {val n=input.read(buffer);if(n<0) break;require(out.size()+n<=8*1024*1024) {"云端数据过大，请联系管理员"};out.write(buffer,0,n)}
                out.toString("UTF-8")
            }.orEmpty()
            val result=runCatching {JSONObject(text)}.getOrElse {
                if(status in 200..299 && text.isNotBlank()) throw TodoCloudError(502,"",stage,"INVALID_RESPONSE")
                JSONObject()
            }
            if(status !in 200..299) throw SyncErrorParser.parse(status,result,stage,conn.getHeaderField("x-request-id") ?: conn.getHeaderField("x-tcb-request-id").orEmpty())
            result
        } catch(e:java.io.IOException) {
            throw TodoCloudError(0,"",stage,if(e is java.net.SocketTimeoutException) "TIMEOUT" else "NETWORK")
        } finally {conn.disconnect()}
    }
    suspend fun login(username:String,password:String):TodoSession {
        val result=request("/auth/v1/signin",JSONObject().put("username",username).put("password",password))
        return sessionFromTokens(result,username)
    }
    internal suspend fun sessionFromTokens(result:JSONObject,fallback:String):TodoSession {
        val access=result.getString("access_token");val user=requestMe(access);val uid=user.getString("sub")
        require(result.str("sub",uid)==uid) {"登录身份不一致，请重新登录"}
        val status=request("/v1/rdb/rest/rpc/wy_account_status",JSONObject(),access)
        return TodoSession(uid,status.str("nickname").ifBlank {user.str("name").takeUnless {it.contains('@')}?.ifBlank {fallback} ?: fallback},access,result.getString("refresh_token"),System.currentTimeMillis()+result.optLong("expires_in",7200)*1000,status.optBoolean("admitted",false))
    }
    internal suspend fun contact(session:TodoSession)=requestMe(session.access).also {require(it.getString("sub")==session.uid)}.str("email")
    internal suspend fun friendInvite(session:TodoSession,action:String,code:String="",nickname:String="")=
        request("/v1/rdb/rest/rpc/wy_friend_invite",JSONObject().put("action",action).put("code",code).put("nickname",nickname),session.access)
    private suspend fun requestMe(token:String)=request("/auth/v1/user/me",JSONObject(),token,method="GET")
    suspend fun refresh(session:TodoSession):TodoSession {
        val result=request("/auth/v1/token",JSONObject().put("grant_type","refresh_token").put("refresh_token",session.refresh))
        require(result.optString("sub",session.uid)==session.uid) { "登录身份变化，请重新登录" }
        return session.copy(access=result.getString("access_token"),refresh=result.getString("refresh_token"),expiresAt=System.currentTimeMillis()+result.optLong("expires_in",7200)*1000)
    }
    suspend fun register(username:String,password:String,nickname:String,code:String) {
        if(registrationUrl.isBlank()) throw TodoCloudError(503,"REGISTER_DISABLED")
        request(registrationUrl,JSONObject().put("username",username).put("password",password).put("nickname",nickname).put("activation",code),absolute=true)
    }
    suspend fun signOut(session:TodoSession) {request("/auth/v1/user/signout",JSONObject(),session.access)}
    suspend fun snapshot(session:TodoSession,book:TodoBook)=request("/v1/rdb/rest/rpc/wy_snapshot",JSONObject().put("task_cursor",book.taskCursor).put("check_cursor",book.checkCursor).put("known_pair",book.pairId).put("nickname",session.name),session.access)
    suspend fun apply(session:TodoSession,op:TodoOperation)=request("/v1/rdb/rest/rpc/wy_apply",JSONObject().put("op_id",op.id).put("payload",JSONObject(op.body)),session.access)
    suspend fun pair(session:TodoSession,action:String,value:String)=request("/v1/rdb/rest/rpc/wy_pair_action",JSONObject().put("action",action).put("value",value),session.access)
    suspend fun memorySnapshot(session:TodoSession)=request("/v1/rdb/rest/rpc/wy_memory_snapshot",JSONObject(),session.access).getJSONArray("memories").mapObjects(::sharedMemoryFromJson)
    suspend fun memoryApply(session:TodoSession,job:ShareJob,path:String)=request("/v1/rdb/rest/rpc/wy_memory_apply",JSONObject().put("op_id",job.op).put("item",JSONObject().put("id",job.id).put("pair_id",job.pairId).put("expected",job.expected).put("active",job.active).put("payload",JSONObject(job.payload)).put("photo_path",path)),session.access)
    suspend fun photo(session:TodoSession,path:String,bytes:ByteArray?=null,delete:Boolean=false):ByteArray=withContext(Dispatchers.IO) {
        require(path.matches(Regex("[A-Za-z0-9._-]+/[a-f0-9-]{36}/[a-f0-9]{64}\\.jpg")))
        val url=URL(base+"/v1/storages/object/"+(if(bytes==null && !delete) "authenticated/" else "")+"withyou-memories/"+path)
        val c=(url.openConnection() as HttpURLConnection).apply {
            requestMethod=if(delete) "DELETE" else if(bytes==null) "GET" else "POST"
            connectTimeout=12000;readTimeout=20000;instanceFollowRedirects=false
            setRequestProperty("Authorization","Bearer ${session.access}");setRequestProperty("x-device-id",deviceId)
            setRequestProperty("Cache-Control","no-store")
            if(bytes!=null) {doOutput=true;setRequestProperty("Content-Type","image/jpeg");setFixedLengthStreamingMode(bytes.size)}
        }
        try {
            if(bytes!=null) c.outputStream.use {it.write(bytes)}
            val status=c.responseCode
            if(bytes!=null && status==409) return@withContext byteArrayOf() // Content-addressed immutable object already uploaded.
            if(status !in 200..299) {
                val text=c.errorStream?.use {input->val bytes=ByteArray(8192);val count=input.read(bytes);if(count>0) String(bytes,0,count,Charsets.UTF_8) else ""}.orEmpty()
                throw SyncErrorParser.parse(status,runCatching {JSONObject(text)}.getOrDefault(JSONObject()),SyncStage.PHOTO,c.getHeaderField("x-request-id").orEmpty())
            }
            val out=java.io.ByteArrayOutputStream()
            c.inputStream.use {input->val buffer=ByteArray(8192);while(true) {val n=input.read(buffer);if(n<0) break;require(out.size()+n<=786432) {"共享照片过大"};out.write(buffer,0,n)}}
            out.toByteArray()
        } catch(e:java.io.IOException) {throw TodoCloudError(0,"",SyncStage.PHOTO,if(e is java.net.SocketTimeoutException) "TIMEOUT" else "NETWORK")} finally {c.disconnect()}
    }
    companion object { fun newDeviceId()=UUID.randomUUID().toString() }
}
