package com.withyou.anniversary

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun JSONObject.str(name: String, fallback: String="") = if(isNull(name)) fallback else optString(name,fallback)
internal fun <T> JSONArray.mapObjects(transform: (JSONObject)->T): List<T> = (0 until length()).map {transform(getJSONObject(it))}
internal fun TodoTask.json() = JSONObject().put("id",id).put("owner_id",owner).put("title",title).put("kind",kind.name.lowercase()).put("notes",notes)
    .put("due",due?.toString() ?: "").put("weekdays",JSONArray(weekdays.sorted())).put("visibility",if(shared) "partner" else "private")
    .put("pair_id",pairId).put("archived",archived).put("deleted",deleted).put("revision",revision)
internal fun taskFromJson(j:JSONObject)=TodoTask(id=j.getString("id"),owner=j.getString("owner_id"),title=j.getString("title"),kind=TodoKind.valueOf(j.getString("kind").uppercase()),notes=j.str("notes"),due=j.str("due").takeIf{it.isNotBlank()}?.let(LocalDate::parse),weekdays=j.getJSONArray("weekdays").let {a->(0 until a.length()).map {a.getInt(it)}.toSet()},shared=j.str("visibility")=="partner",pairId=j.str("pair_id"),archived=j.optBoolean("archived"),deleted=j.optBoolean("deleted"),revision=j.optLong("revision"))
internal fun TodoCheck.json()=JSONObject().put("task_id",taskId).put("user_id",user).put("day",day.toString()).put("done",done).put("revision",revision).put("completed_on",completedOn?.toString())
internal fun checkFromJson(j:JSONObject)=TodoCheck(j.getString("task_id"),j.getString("user_id"),LocalDate.parse(j.getString("day")),j.getBoolean("done"),j.optLong("revision"),j.str("completed_on").takeIf {it.isNotBlank()}?.let(LocalDate::parse) ?: j.str("updated_at").takeIf {it.isNotBlank()}?.let {java.time.OffsetDateTime.parse(it).atZoneSameInstant(TodoRules.zone).toLocalDate()})
internal fun inviteFromJson(j:JSONObject)=PairInvite(j.getString("id"),j.getString("owner_id"),j.str("candidate_id"),j.str("name"),j.str("expires_at"))
internal fun TodoBook.json()=JSONObject().put("schema",1).put("uid",uid).put("pair_id",pairId).put("partner_id",partnerId).put("partner_name",partnerName)
    .put("memories",JSONArray(memories.map {it.json()})).put("share_jobs",JSONArray(shareJobs.map {it.json()}))
    .put("tasks",JSONArray(tasks.map {it.json()})).put("checks",JSONArray(checks.map {it.json()}))
    .put("pending",JSONArray(pending.map {JSONObject().put("id",it.id).put("task_id",it.taskId).put("body",it.body)}))
    .put("synced_at",syncedAt).put("task_cursor",taskCursor).put("check_cursor",checkCursor).put("conflicts",JSONArray(conflicts))
internal fun bookFromJson(j:JSONObject):TodoBook {
    require(j.getInt("schema")==1)
    return TodoBook(uid=j.getString("uid"),pairId=j.str("pair_id"),partnerId=j.str("partner_id"),partnerName=j.str("partner_name","伙伴"),
        memories=j.optJSONArray("memories")?.mapObjects(::sharedMemoryFromJson) ?: emptyList(),shareJobs=j.optJSONArray("share_jobs")?.mapObjects(::shareJobFromJson) ?: emptyList(),
        tasks=j.getJSONArray("tasks").mapObjects(::taskFromJson),checks=j.getJSONArray("checks").mapObjects(::checkFromJson),
        pending=j.getJSONArray("pending").mapObjects {TodoOperation(it.getString("id"),it.getString("task_id"),it.getString("body"))},
        syncedAt=j.optLong("synced_at"),taskCursor=j.optLong("task_cursor"),checkCursor=if(j.has("synced_at")) j.optLong("check_cursor") else 0,conflicts=j.optJSONArray("conflicts")?.let {a->(0 until a.length()).map {a.getString(it)}} ?: emptyList())
}
class TodoStore(context:Context) {
    private val directory=File(context.filesDir,"todos/accounts").apply {mkdirs()}
    private fun file(uid:String):AtomicFile {
        require(uid.isNotBlank())
        val hash=MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") {"%02x".format(it)}
        return AtomicFile(File(directory,"$hash.json"))
    }
    fun read(uid:String):TodoBook=synchronized(lock) {
        val f=file(uid)
        if(!f.baseFile.exists() && !File(f.baseFile.path+".bak").exists()) TodoBook(uid)
        else bookFromJson(JSONObject(f.openRead().bufferedReader().use {it.readText()})).also {require(it.uid==uid)}
    }
    fun write(book:TodoBook)=synchronized(lock) {writeAtomic(file(book.uid),book.json().toString().toByteArray());Unit}
    fun sharedOwners(id:String):Set<String> = synchronized(lock) {
        directory.listFiles()?.filter {it.extension=="json"}?.map {f->bookFromJson(JSONObject(AtomicFile(f).openRead().bufferedReader().use {it.readText()}))}?.filter {b->
            b.memories.any {it.id==id && it.owner==b.uid && it.active} || b.shareJobs.any {it.id==id && it.active}
        }?.map {it.uid}?.toSet() ?: emptySet()
    }
    companion object { private val lock=Any() }
}
internal fun writeAtomic(file:AtomicFile, bytes:ByteArray) {
    val stream=file.startWrite()
    try {stream.write(bytes);file.finishWrite(stream)} catch(e:Exception) {file.failWrite(stream);throw e}
}
data class TodoSession(val uid:String,val name:String,val access:String,val refresh:String,val expiresAt:Long,val admitted:Boolean=true) {
    fun json()=JSONObject().put("uid",uid).put("name",name).put("access",access).put("refresh",refresh).put("expires",expiresAt).put("admitted",admitted)
}
/** Tokens stay in app-private storage encrypted by a non-exportable Android Keystore key. */
class TodoSessionStore(context:Context) {
    private val file=AtomicFile(File(context.filesDir,"todos/session.enc").apply {parentFile!!.mkdirs()})
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (ks.getKey("withyou-todo-session",null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("withyou-todo-session",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(session:TodoSession?) = synchronized(sessionLock) {
        if(session==null) {file.delete();return@synchronized}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        val bytes=cipher.doFinal(session.json().toString().toByteArray())
        writeAtomic(file,byteArrayOf(cipher.iv.size.toByte())+cipher.iv+bytes)
    }
    fun read():TodoSession? = synchronized(sessionLock) {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return@synchronized null
        val bytes=file.openRead().use {it.readBytes()}; val n=bytes[0].toInt()
        require(n in 12..16 && bytes.size>n+1)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,n+1)))}
        val j=JSONObject(String(cipher.doFinal(bytes.copyOfRange(n+1,bytes.size)),Charsets.UTF_8))
        TodoSession(j.getString("uid"),j.getString("name"),j.getString("access"),j.getString("refresh"),j.getLong("expires"),j.optBoolean("admitted",true))
    }
    fun replaceIfCurrent(expected:TodoSession,next:TodoSession):Boolean = synchronized(sessionLock) {
        val saved=read()
        if(saved?.uid!=expected.uid || saved.refresh!=expected.refresh) false
        else {save(next);true}
    }
    companion object {private val sessionLock=Any()}

}
