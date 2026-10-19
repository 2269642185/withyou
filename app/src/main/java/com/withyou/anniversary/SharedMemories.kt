package com.withyou.anniversary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
internal fun memoryPayload(record:MemoryDay):String {
    val r=JSONObject(Repository.encode(AppData(records=listOf(record.copy(photo=null,pinned=false,categoryId=null))))).getJSONArray("records").getJSONObject(0)
    return r.toString()
}
internal fun memoryFromPayload(payload:String):MemoryDay {
    val root=JSONObject(Repository.encode(AppData())).put("records",org.json.JSONArray().put(JSONObject(payload)))
    return Repository.decode(root.toString()).records.single()
}
data class SharedMemory(val owner:String,val pairId:String,val payload:String,val photoPath:String="",val revision:Long=0,val active:Boolean=true) {
    val record:MemoryDay by lazy {memoryFromPayload(payload)}
    val id get()=record.id
    fun json()=JSONObject().put("owner_id",owner).put("pair_id",pairId).put("payload",JSONObject(payload)).put("photo_path",photoPath).put("revision",revision).put("active",active)
}
internal fun sharedMemoryFromJson(j:JSONObject)=SharedMemory(j.getString("owner_id"),j.getString("pair_id"),j.getJSONObject("payload").toString(),j.str("photo_path"),j.getLong("revision"),j.getBoolean("active"))
data class ShareJob(val op:String=UUID.randomUUID().toString(),val id:String,val pairId:String,val payload:String,val photo:String?=null,val expected:Long=0,val active:Boolean=true) {
    fun json()=JSONObject().put("op",op).put("id",id).put("pair_id",pairId).put("payload",payload).put("photo",photo).put("expected",expected).put("active",active)
}
internal fun shareJobFromJson(j:JSONObject)=ShareJob(j.getString("op"),j.getString("id"),j.getString("pair_id"),j.getString("payload"),j.str("photo").takeIf {it.isNotBlank()},j.getLong("expected"),j.getBoolean("active"))

/** Account-scoped photo cache, separate from personal anniversary photos and backups. */
class SharedPhotoStore(context:Context) {
    private val root=File(context.filesDir,"shared-photos").apply {mkdirs()}
    fun directory(uid:String)=File(root,sha256(uid.toByteArray())).apply {mkdirs()}
    fun name(path:String):String {
        require(path.matches(Regex("[A-Za-z0-9._-]+/[a-f0-9-]{36}/[a-f0-9]{64}\\.jpg")))
        return UUID.nameUUIDFromBytes(path.toByteArray()).toString()+".jpg"
    }
    fun file(uid:String,path:String)=File(directory(uid),name(path))
    fun retain(uid:String,paths:Set<String>) {
        val keep=paths.map {name(it)}.toSet()
        directory(uid).listFiles()?.filter {it.name !in keep}?.forEach {it.delete()}
    }
    fun clear(uid:String)=retain(uid,emptySet())
    fun store(uid:String,path:String,bytes:ByteArray) {
        require(bytes.size<=786432 && sha256(bytes)==path.substringAfterLast('/').removeSuffix(".jpg")) {"共享照片校验失败"}
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth in 1..1800 && bounds.outHeight in 1..1800 && bounds.outMimeType=="image/jpeg") {"共享照片格式无效"}
        writeAtomic(AtomicFile(file(uid,path)),bytes)
    }
    fun prepare(source:File):ByteArray {
        require(source.exists()) {"原照片暂不可用，未完成共享"}
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(source.path,bounds)
        require(bounds.outWidth in 1..10000 && bounds.outHeight in 1..10000)
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1800) sample*=2
        val image=requireNotNull(BitmapFactory.decodeFile(source.path,BitmapFactory.Options().apply {inSampleSize=sample}))
        try {
            var quality=85
            while(true) {
                val out=ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,quality,out)
                if(out.size()<=786432) return out.toByteArray()
                quality-=15;require(quality>=25) {"照片过大，请选择较小的照片"}
            }
        } finally {image.recycle()}
    }
}
