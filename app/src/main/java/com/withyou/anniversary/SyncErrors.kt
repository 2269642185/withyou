package com.withyou.anniversary

import org.json.JSONObject

enum class SyncStage(val label:String) {
    UNKNOWN("云端请求"), SESSION("登录续期"), LOGIN("账号登录"), PROFILE("账号信息"), AUTH_ACTION("账号验证"),
    READ("读取事项"), WRITE_TASK("保存事项"), WRITE_CHECK("提交打卡"),
    SHARE_READ("读取共享纪念日"), SHARE_WRITE("保存共享纪念日"), PHOTO("共享照片"), PAIR("配对操作");
    companion object {
        internal fun of(path:String,body:JSONObject=JSONObject()):SyncStage=when {
            path=="/auth/v1/token" -> SESSION
            path=="/auth/v1/signin" -> LOGIN
            path=="/v1/rdb/rest/rpc/wy_snapshot" -> READ
            path=="/v1/rdb/rest/rpc/wy_apply" -> if(body.optJSONObject("payload")?.str("type")=="check") WRITE_CHECK else WRITE_TASK
            path=="/v1/rdb/rest/rpc/wy_memory_snapshot" -> SHARE_READ
            path=="/v1/rdb/rest/rpc/wy_memory_apply" -> SHARE_WRITE
            path.contains("/storages/") -> PHOTO
            path=="/v1/rdb/rest/rpc/wy_pair_action" -> PAIR
            path=="/auth/v1/user/me" || path.endsWith("wy_account_status") -> PROFILE
            path.startsWith("/auth/") -> AUTH_ACTION
            else -> UNKNOWN
        }
    }
}

internal fun needsReauthentication(status:Int,reason:String,stage:SyncStage):Boolean {
    if(stage==SyncStage.LOGIN || stage==SyncStage.AUTH_ACTION) return false
    if(status==401) return true
    if(status!=400 || stage!=SyncStage.SESSION) return false
    val lower=reason.lowercase(java.util.Locale.ROOT)
    return lower.contains("invalid_grant") || lower.contains("invalid_refresh_token") ||
        lower.contains("refresh_token") && listOf("invalid","expired","revoked","无效","过期","撤销").any {lower.contains(it)}
}

internal object SyncErrorParser {
    fun parse(status:Int,result:JSONObject,stage:SyncStage,headerId:String=""):TodoCloudError {
        val nested=result.optJSONObject("error")
        val objects=listOfNotNull(result,nested)
        val reason=objects.flatMap {j->listOf("message","error","code","error_code","error_description").map {j.str(it)}}.joinToString(" ")
        val code=objects.asSequence().flatMap {j->listOf("code","error_code","error").asSequence().map {j.str(it)}}
            .firstOrNull {safeCode(it)}.orEmpty()
        val id=(objects.flatMap {j->listOf("request_id","requestId","RequestId","reqId").map {j.str(it)}}+headerId).firstOrNull {safeRequestId(it)}.orEmpty()
        return TodoCloudError(status,reason,stage,code,id)
    }
    private val known=setOf("invalid_grant","invalid_refresh_token","invalid_token","token_expired","invalid_client","rate_limit_exceeded","resource_exhausted","NETWORK","TIMEOUT","INVALID_RESPONSE","SESSION_CHANGED")
    fun safeCode(value:String)=value in known || value.matches(Regex("(?:PGRST[0-9]{3}|[0-9A-Z]{5}|[0-9]{3,6})"))
    fun safeRequestId(value:String)=value.matches(Regex("(?:[a-fA-F0-9]{32}|[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12})"))
}

/** No raw body, exception message, identity, record title, credential or token is exported. */
internal fun syncDiagnostic(error:TodoCloudError?,pending:Int,photoJobs:Int):String=buildString {
    append("与你 · 纪念日 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
    append("环节：${error?.stage?.label ?: "本机处理"}\n")
    append("HTTP：${error?.status?.takeIf {it>0} ?: "未收到响应"}\n")
    append("错误码：${error?.code?.takeIf(SyncErrorParser::safeCode)?.ifBlank {null} ?: "未提供"}\n")
    append("请求编号：${error?.requestId?.takeIf(SyncErrorParser::safeRequestId)?.ifBlank {null} ?: "未提供"}\n")
    append("本机待同步操作：$pending\n共享队列：$photoJobs\n")
    append("本机记录保留。诊断信息不含账号、事项内容、密码或登录令牌。")
}
