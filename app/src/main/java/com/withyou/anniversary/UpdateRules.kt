package com.withyou.anniversary

import java.net.URI

data class AppUpdate(val code: Long, val version: String, val notes: String, val url: String,
    val size: Long, val sha256: String, val minSdk: Int)

object UpdateRules {
    const val MAX_APK = 150L * 1024 * 1024
    fun repository(value: String): String {
        val text = value.trim().removeSuffix("/").removeSuffix(".git")
            .removePrefix("https://github.com/")
        require(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}").matches(text)) { "请填写 GitHub 仓库地址或 用户名/仓库名" }
        require(text.substringAfter('/') !in setOf(".", ".."))
        return text
    }
    fun https(value: String): String {
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) { "更新文件必须使用 HTTPS 地址" }
        return value
    }
    fun validate(update: AppUpdate, repo: String) {
        require(update.code > 0 && update.version.isNotBlank() && update.version.length <= 80)
        require(update.size in 1..MAX_APK && Regex("[a-fA-F0-9]{64}").matches(update.sha256)) { "更新文件信息无效" }
        require(update.minSdk in 26..100 && update.notes.length <= 10000)
        https(update.url)
        val uri = URI(update.url)
        require(uri.host == "github.com" && uri.path.startsWith("/${repository(repo)}/releases/download/") && uri.path.endsWith(".apk")) { "安装包必须来自配置仓库的版本发布附件" }
    }
    fun shouldCheck(today: String, last: String) = today != last
    fun newer(update: AppUpdate, installed: Long) = update.code > installed
}
