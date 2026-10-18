package com.withyou.anniversary

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate

internal fun parseUpdate(text: String, repo: String): AppUpdate {
    val json = JSONObject(text)
    require(json.getInt("schemaVersion") == 1) { "不支持的更新信息格式" }
    return AppUpdate(json.getLong("versionCode"), json.getString("versionName"), json.getString("notes"),
        json.getString("apkUrl"), json.getLong("size"), json.getString("sha256"), json.getInt("minSdk"))
        .also { UpdateRules.validate(it, repo) }
}

internal object UpdateNetwork {
    fun open(address: String): HttpURLConnection {
        var current = UpdateRules.https(address)
        repeat(6) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 15000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "WithYouAnniversary/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                when (connection.responseCode) {
                    200 -> return connection
                    301, 302, 303, 307, 308 -> {
                        current = UpdateRules.https(URL(URL(current), connection.getHeaderField("Location") ?: error("缺少下载跳转地址")).toString())
                        connection.disconnect()
                    }
                    404 -> error("更新文件尚未发布，请稍后重试")
                    else -> error("更新服务暂时不可用（${connection.responseCode}）")
                }
            } catch (e: Exception) { connection.disconnect(); throw e }
        }
        error("更新地址跳转次数过多")
    }
    suspend fun manifest(repo: String, connect: (String) -> HttpURLConnection = ::open): AppUpdate = withContext(Dispatchers.IO) {
        val connection = connect("https://github.com/${UpdateRules.repository(repo)}/releases/latest/download/update.json")
        try {
            val bytes = connection.inputStream.use { input ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(4096)
                while(output.size()<=65536) {
                    ensureActive()
                    val n=input.read(buffer,0,minOf(buffer.size,65537-output.size()))
                    if(n<0) break
                    output.write(buffer,0,n)
                }
                output.toByteArray()
            }
            ensureActive(); require(bytes.size <= 65536) { "更新信息过大" }
            parseUpdate(bytes.toString(Charsets.UTF_8), repo)
        } finally { connection.disconnect() }
    }
    suspend fun download(update: AppUpdate, file: File, connect: (String) -> HttpURLConnection = ::open, progress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        file.parentFile!!.mkdirs()
        val connection = connect(update.url)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            connection.inputStream.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(32768)
                var previous = -1
                while (true) {
                    ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    count += n; require(count <= update.size) { "下载文件大小不符" }
                    digest.update(buffer, 0, n); output.write(buffer, 0, n)
                    val percent = (count * 100 / update.size).toInt()
                    if (percent != previous) { progress(percent); previous = percent }
                }
            } }
            ensureActive()
            require(count == update.size && digest.digest().joinToString("") { "%02x".format(it) }.equals(update.sha256, true)) { "安装包校验失败，请重新下载" }
        } catch (e: Exception) { file.delete(); throw e }
        finally { connection.disconnect() }
    }
}

internal object UpdateInstaller {
    @Suppress("DEPRECATION")
    private fun PackageInfo.signers(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) signingInfo?.apkContentsSigners else signatures
        return signatures.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File, update: AppUpdate) {
        require(file.isFile && file.length() == update.size) { "安装包已失效，请重新下载" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(32768); while (true) { val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
        require(digest.digest().joinToString("") { "%02x".format(it) }.equals(update.sha256,true)) { "安装包校验失败，请重新下载" }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("无法读取安装包或签名")
        val installedCode = if(Build.VERSION.SDK_INT>=28) installed.longVersionCode else installed.versionCode.toLong()
        val archiveCode = if(Build.VERSION.SDK_INT>=28) archive.longVersionCode else archive.versionCode.toLong()
        require(archive.packageName == context.packageName && archiveCode == update.code && archiveCode > installedCode) { "安装包的应用或版本不匹配" }
        require(archive.versionName == update.version && archive.applicationInfo!!.minSdkVersion <= Build.VERSION.SDK_INT && update.minSdk <= Build.VERSION.SDK_INT) { "新版不支持当前系统或版本信息不一致" }
        val expected = installed.signers()
        require(expected.isNotEmpty() && archive.signers() == expected) { "安装包签名不一致，已停止安装以保护原有数据" }
    }
    fun intent(context: Context, file: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", file), "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("新版安装包", data)
    }
}

data class UpdateStatus(val repo: String = "", val phase: String = "idle", val update: AppUpdate? = null,
    val progress: Int = 0, val message: String = "", val automatic: Boolean = false)

class UpdateViewModel(app: Application): AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val initialRepo = prefs.getString("repository", BuildConfig.UPDATE_REPOSITORY).orEmpty()
    private val mutable = MutableStateFlow(UpdateStatus(repo = initialRepo))
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private val file = File(app.filesDir, "updates/update.apk")
    init {
        // Only reuse a fully downloaded package after rechecking it against the installed app.
        prefs.getString("pending", null)?.let { saved ->
            work = viewModelScope.launch {
                try {
                    val update = parseUpdate(saved, initialRepo)
                    withContext(Dispatchers.IO) { UpdateInstaller.verify(app, file, update) }
                    mutable.value = mutable.value.copy(phase="ready",update=update,message="新版已下载，点击安装")
                } catch (_: Exception) { withContext(Dispatchers.IO) { file.delete() }; prefs.edit().remove("pending").apply() }
            }
        }
    }
    fun configure(value: String) {
        if(work?.isActive == true) return
        try {
            val repo = UpdateRules.repository(value)
            prefs.edit().putString("repository",repo).remove("lastCheck").remove("ignored").remove("pending").apply()
            mutable.value = UpdateStatus(repo=repo,message="更新地址已保存")
        } catch(e: Exception) { mutable.value=mutable.value.copy(message=e.message.orEmpty()) }
    }
    fun check(manual: Boolean) {
        val current = mutable.value
        if(current.repo.isBlank() || work?.isActive == true || current.phase == "ready") return
        val today = LocalDate.now().toString()
        if(!manual && !UpdateRules.shouldCheck(today,prefs.getString("lastCheck","").orEmpty())) return
        prefs.edit().putString("lastCheck",today).apply()
        work = viewModelScope.launch {
            mutable.value = current.copy(phase="checking",message="正在检查更新…",automatic=!manual)
            try {
                val update = UpdateNetwork.manifest(current.repo)
                val newer = UpdateRules.newer(update,BuildConfig.VERSION_CODE.toLong())
                val ignored = prefs.getLong("ignored",0) == update.code
                mutable.value = current.copy(phase=if(newer && (manual || !ignored)) "available" else "latest",
                    update=if(newer && (manual || !ignored)) update else null, automatic=!manual,
                    message=if(newer && (manual || !ignored)) "有新版本 ${update.version}" else "当前已是最新版本")
            } catch(e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value=current.copy(phase="error",message="无法连接更新服务，请检查网络后重试。GitHub 在部分网络下可能无法访问。",automatic=!manual) }
        }
    }
    fun ignore() { mutable.value.update?.let { prefs.edit().putLong("ignored",it.code).apply() }; mutable.value=mutable.value.copy(phase="idle",update=null,message="已忽略此版本；手动检查仍可查看") }
    fun download() {
        val update = mutable.value.update ?: return
        if(work?.isActive == true) return
        work = viewModelScope.launch {
            mutable.value=mutable.value.copy(phase="downloading",progress=0,message="正在下载新版…")
            try {
                require(update.minSdk <= Build.VERSION.SDK_INT) { "新版需要更高版本 Android" }
                UpdateNetwork.download(update,file) { mutable.value=mutable.value.copy(progress=it) }
                withContext(Dispatchers.IO) { UpdateInstaller.verify(getApplication(),file,update) }
                prefs.edit().putString("pending", JSONObject().put("schemaVersion",1).put("versionCode",update.code).put("versionName",update.version).put("notes",update.notes).put("apkUrl",update.url).put("size",update.size).put("sha256",update.sha256).put("minSdk",update.minSdk).toString()).apply()
                mutable.value=mutable.value.copy(phase="ready",message="校验通过，可以安装新版")
            } catch(e: CancellationException) { withContext(NonCancellable+Dispatchers.IO) {file.delete()}; mutable.value=mutable.value.copy(phase="available",message="已取消下载，可重新下载"); throw e }
            catch(e: Exception) { withContext(Dispatchers.IO) {file.delete()}; mutable.value=mutable.value.copy(phase="error",message=e.message ?: "下载失败，请重试") }
        }
    }
    fun cancel() { work?.cancel() }
    fun install(launch: (Intent) -> Unit) {
        if(work?.isActive == true || mutable.value.phase != "ready") return
        val update=mutable.value.update ?: return
        work=viewModelScope.launch {
            try { withContext(Dispatchers.IO) { UpdateInstaller.verify(getApplication(),file,update) }; launch(UpdateInstaller.intent(getApplication(),file)) }
            catch(e: Exception) { mutable.value=mutable.value.copy(message=e.message ?: "无法打开系统安装界面") }
        }
    }
}
