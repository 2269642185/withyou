package com.withyou.anniversary

import org.json.JSONObject
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal object EmailRules {
    fun email(value:String):String {
        val clean=value.trim().lowercase(Locale.ROOT)
        require(clean.length<=254 && clean.matches(Regex("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+"))) {"请输入有效的邮箱地址"}
        return clean
    }
    fun password(value:String) {
        require(value.length in 8..32 && value.first().isLetterOrDigit() && listOf(value.any(Char::isUpperCase),value.any(Char::isLowerCase),value.any(Char::isDigit),value.any {it in "()!@#$%^&*|?><_-"}).count {it}>=3) {"密码为 8～32 位，以字母或数字开头，包含大小写字母、数字、符号中的至少三类"}
    }
    fun invite(value:String):String {
        val text=value.trim()
        if(text.matches(Regex("[a-f0-9]{32}"))) return text
        val uri=runCatching {URI(text)}.getOrNull() ?: return ""
        if(uri.rawUserInfo!=null || uri.port!=-1) return ""
        val part=when {
            uri.scheme=="withyou" && uri.host=="register" && uri.path.orEmpty().isEmpty()->uri.rawQuery
            uri.scheme=="https" && uri.host=="2269642185.github.io" && uri.path=="/withyou/register.html"->uri.rawFragment
            else->null
        }.orEmpty()
        return part.removePrefix("invite=").takeIf {part.startsWith("invite=") && it.matches(Regex("[a-f0-9]{32}"))}.orEmpty()
    }
}

enum class EmailPurpose { REGISTER, RESET, BIND }
class EmailChallenge(val id:String,val email:String,val purpose:EmailPurpose,val expiresAt:Long,val resendAt:Long,val existingAccount:Boolean=false)

/** Native CloudBase auth only. No SMTP credentials or custom password database. */
internal class EmailAuth(private val call:suspend (String,JSONObject,String,String)->JSONObject,private val now:()->Long=System::currentTimeMillis) {
    suspend fun send(address:String,purpose:EmailPurpose,session:TodoSession?=null):EmailChallenge {
        val email=EmailRules.email(address)
        val result=try {call("/auth/v1/verification",JSONObject().put("email",email).put("target",if(purpose==EmailPurpose.RESET) "USER" else "ANY"),session?.access.orEmpty(),"POST")}
        catch(e:TodoCloudError) {if(purpose==EmailPurpose.RESET && e.reason.contains("user_not_found")) JSONObject() else throw e}
        val stamp=now()
        return EmailChallenge(result.str("verification_id"),email,purpose,stamp+result.optLong("expires_in",600).coerceIn(1,600)*1000,stamp+60000,result.optBoolean("is_user",false))
    }
    private suspend fun verify(challenge:EmailChallenge,purpose:EmailPurpose,address:String,code:String):String {
        require(challenge.purpose==purpose && EmailRules.email(address)==challenge.email) {"邮箱已变化，请重新发送验证码"}
        require(challenge.id.isNotBlank() && now()<challenge.expiresAt && code.matches(Regex("[0-9]{6}"))) {"验证码错误或已过期，请重新发送"}
        return call("/auth/v1/verification/verify",JSONObject().put("verification_id",challenge.id).put("verification_code",code),"","POST").getString("verification_token")
    }
    suspend fun register(challenge:EmailChallenge,address:String,code:String,password:String):JSONObject {
        EmailRules.password(password)
        val token=verify(challenge,EmailPurpose.REGISTER,address,code)
        require(!challenge.existingAccount) {"此操作暂时无法完成，请尝试登录或找回密码"}
        return call("/auth/v1/signup",JSONObject().put("email",challenge.email).put("password",password).put("verification_token",token),"","POST")
    }
    suspend fun reset(challenge:EmailChallenge,address:String,code:String,password:String):Boolean {
        EmailRules.password(password)
        val token=verify(challenge,EmailPurpose.RESET,address,code)
        call("/auth/v1/reset",JSONObject().put("email",challenge.email).put("new_password",password).put("verification_token",token),"","POST")
        return try {
            val session=call("/auth/v1/signin",JSONObject().put("username",challenge.email).put("password",password),"","POST")
            val access=session.getString("access_token")
            call("/auth/v1/user/revoke/all",JSONObject(),access,"DELETE")
            val result=call("/v1/rdb/rest/rpc/wy_revoke_old_sessions",JSONObject(),access,"POST")
            require(result.optBoolean("revoked"))
            val seconds=result.optLong("wait_seconds",1);require(seconds in 0..3)
            delay((seconds+1)*1000)
            true
        } catch(e:CancellationException) {throw e} catch(_:Exception) {false}
    }
    suspend fun sudo(session:TodoSession,password:String):String {
        require(password.isNotBlank()) {"请输入当前账号密码"}
        return call("/auth/v1/user/sudo",JSONObject().put("password",password),session.access,"POST").getString("sudo_token")
    }
    suspend fun bind(session:TodoSession,sudo:String,challenge:EmailChallenge,address:String,code:String) {
        require(sudo.isNotBlank()) {"请重新验证当前密码并发送验证码"}
        val token=verify(challenge,EmailPurpose.BIND,address,code)
        call("/auth/v1/user/contact",JSONObject().put("email",challenge.email).put("verification_token",token).put("sudo_token",sudo),session.access,"PATCH")
        val me=call("/auth/v1/user/me",JSONObject(),session.access,"GET")
        require(me.getString("sub")==session.uid && me.str("email").equals(challenge.email,true)) {"邮箱绑定状态待确认，请重新登录检查；原记录保留"}
    }
}
