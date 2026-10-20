package com.withyou.anniversary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.AtomicFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A single atomic snapshot keeps record changes and preferences consistent across process restarts. */
class Repository(private val context: Context) {
    private val mutex = Mutex()
    private val root = context.filesDir
    private val snapshot = AtomicFile(File(root, "memories.json"))
    val photos = File(root, "photos").apply { mkdirs() }

    fun read(): AppData = synchronized(fileLock) {
        if (!snapshot.baseFile.exists() && !File(root, "memories.json.bak").exists()) AppData()
        else decode(snapshot.openRead().bufferedReader().use { it.readText() })
    }

    private fun write(data: AppData) = synchronized(fileLock) {
        val stream = snapshot.startWrite()
        try {
            stream.write(encode(data).toByteArray(Charsets.UTF_8))
            snapshot.finishWrite(stream)
        } catch (e: Exception) { snapshot.failWrite(stream); throw e }
        // Only run after the snapshot is committed; unreferenced imports from an open editor
        // retain a one-day grace period, while deleted record photos can be reclaimed later.
        val keep = data.records.mapNotNull { it.photo }.toSet()
        photos.listFiles()?.filter { it.name !in keep && it.lastModified() < System.currentTimeMillis() - 86_400_000 }?.forEach { it.delete() }
    }
    suspend fun save(data: AppData) = withContext(Dispatchers.IO) { mutex.withLock {
        write(data)
        AnniversaryWidget.refresh(context)
    } }

    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) {
        // Cache the provider stream once. Never retain a transient content URI.
        val temp = File.createTempFile("import", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法打开照片" }
                temp.outputStream().use { out -> copyLimited(input, out, 30L * 1024 * 1024) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "请选择有效的图片" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
            val bitmap = requireNotNull(BitmapFactory.decodeFile(temp.path, BitmapFactory.Options().apply { inSampleSize = sample })) { "照片解码失败" }
            val matrix = Matrix()
            val orientation = runCatching { ExifInterface(temp.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f,1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f,-1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f,1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f,1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            }
            val oriented = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
            val name = "${UUID.randomUUID()}.jpg"
            try { File(photos, name).outputStream().use { check(oriented.compress(Bitmap.CompressFormat.JPEG, 88, it)) } }
            finally { if (oriented !== bitmap) oriented.recycle(); bitmap.recycle() }
            name
        } finally { temp.delete() }
    }

    suspend fun backup(uri: Uri, data: AppData) = withContext(Dispatchers.IO) { mutex.withLock {
        ZipOutputStream(requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))).use { zip ->
            zip.putNextEntry(ZipEntry("memories.json")); zip.write(encode(data).toByteArray(Charsets.UTF_8)); zip.closeEntry()
            data.records.mapNotNull { it.photo }.distinct().forEach { name ->
                val file = File(photos, name)
                require(file.isFile) { "照片缺失，请重新导入后备份" }
                zip.putNextEntry(ZipEntry("photos/$name")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    } }

    suspend fun restore(uri: Uri): AppData = withContext(Dispatchers.IO) { mutex.withLock {
        val stage = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var total = 0L
            val seen = mutableSetOf<String>()
            ZipInputStream(requireNotNull(context.contentResolver.openInputStream(uri))).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    require(!entry.isDirectory && (name == "memories.json" || name.matches(Regex("photos/[a-f0-9-]{36}\\.jpg")))) { "备份中有不支持的文件" }
                    require(seen.add(name) && seen.size <= 501) { "备份文件重复或数量过多" }
                    val target = File(stage, name).apply { parentFile?.mkdirs() }
                    target.outputStream().use { total += copyLimited(zip, it, if (name == "memories.json") 2L*1024*1024 else 12L*1024*1024) }
                    require(total <= 150L*1024*1024) { "备份超过 150 MB" }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
            val decoded = decode(File(stage, "memories.json").readText())
            // New names prevent overwriting any photo referenced by the current, still-valid snapshot.
            val migrated = decoded.copy(records = decoded.records.map { record ->
                record.photo?.let { old ->
                    val source = File(stage, "photos/$old")
                    require(source.isFile) { "备份缺少照片" }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(source.path, bounds)
                    require(bounds.outWidth in 1..10000 && bounds.outHeight in 1..10000) { "备份照片无效" }
                    val fresh = "${UUID.randomUUID()}.jpg"
                    source.copyTo(File(photos, fresh))
                    record.copy(photo = fresh)
                } ?: record
            })
            write(migrated)
            AnniversaryWidget.refresh(context)
            migrated
        } finally { stage.deleteRecursively() }
    } }

    companion object {
        // AtomicFile handles crashes, but is not a concurrency primitive: openRead may delete
        // an in-progress .new file. Widget and activity repositories share this process lock.
        private val fileLock = Any()
        fun encode(data: AppData): String = JSONObject().apply {
            put("version", 3)
            put("theme", data.preferences.theme.name); put("defaultMode", data.preferences.defaultMode.name)
            put("animations", data.preferences.animations); put("palette",data.preferences.palette.name)
            put("categories", JSONArray().apply { data.categories.forEach { c -> put(JSONObject().apply {
                put("id",c.id);put("name",c.name);put("icon",c.icon);put("color",c.color)
            }) } })
            put("records", JSONArray().apply { data.records.forEach { r -> put(JSONObject().apply {
                put("id", r.id); put("title", r.title); put("date", r.date.toString()); put("nickname", r.nickname)
                put("template", r.template.key); put("mode", r.mode.name); put("color", r.color); put("pinned", r.pinned); r.photo?.let { put("photo", it) }
                r.categoryId?.let { put("categoryId",it) }
                put("appearance",JSONObject().apply {
                    r.appearance.let { a ->
                        a.background?.let { put("background",it) }; put("tone",a.tone.name)
                        a.text?.let { put("text",it) };a.title?.let { put("title",it) };a.days?.let { put("days",it) };a.auxiliary?.let { put("auxiliary",it) }
                        put("overlay",a.overlay.toDouble())
                    }
                })
            }) } })
        }.toString(2)
        fun decode(text: String): AppData {
            val obj = JSONObject(text)
            val version = obj.getInt("version")
            require(version in 1..3) { "不支持的备份版本" }
            val cats = if(version == 1) JSONArray() else obj.getJSONArray("categories")
            require(cats.length() <= 50) { "最多支持 50 个分类" }
            val categories = (0 until cats.length()).map { i ->
                val c=cats.getJSONObject(i); val id=c.getString("id"); UUID.fromString(id)
                val name=c.getString("name").trim(); require(name.isNotEmpty() && name.length <= 20) { "分类名称应为 1 至 20 字" }
                require(name !in listOf("全部","未分类")) { "请使用其他分类名称" }
                val icon=c.getString("icon"); require(icon in CategoryIcons.keys) { "不支持的分类图标" }
                val color=c.getInt("color");require(color in ColorRules.swatches.indices)
                Category(id,name,icon,color)
            }
            require(categories.map { it.id }.distinct().size == categories.size) { "分类编号重复" }
            require(categories.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == categories.size) { "分类名称重复" }
            val array = obj.getJSONArray("records")
            require(array.length() <= 500) { "最多支持 500 条纪念日" }
            val records = (0 until array.length()).map { i ->
                val r = array.getJSONObject(i)
                val id = r.getString("id"); UUID.fromString(id)
                val title = r.getString("title").trim(); require(title.isNotEmpty() && title.length <= 60)
                val nick = r.getString("nickname"); require(nick.length <= 40)
                val date = LocalDate.parse(r.getString("date")); require(date.year in 1..9998)
                val photo = if (r.has("photo")) r.getString("photo") else null
                require(photo == null || photo.matches(Regex("[a-f0-9-]{36}\\.jpg")))
                val color = r.getInt("color"); require(color in ColorRules.swatches.indices)
                val a = if(version >= 2) r.getJSONObject("appearance") else JSONObject()
                fun hex(key: String): String? = if(!a.has(key)) null else ColorRules.normalize(a.getString(key)).also { requireNotNull(it) { "无效的颜色" } }
                val overlay=a.optDouble("overlay",.56).toFloat(); require(overlay.isFinite() && overlay in 0f..1f) { "遮罩强度无效" }
                val appearance=CardAppearance(hex("background"),TextTone.valueOf(a.optString("tone","AUTO")),hex("text"),hex("title"),hex("days"),hex("auxiliary"),overlay)
                val categoryId=if(r.has("categoryId")) r.getString("categoryId").takeIf { id -> categories.any { it.id == id } } else null
                MemoryDay(id, title, date, nick, CountMode.valueOf(r.getString("mode")), color, photo, r.getBoolean("pinned"),appearance,categoryId,CardTemplate.fromKey(r.optString("template","classic")))
            }
            require(records.map { it.id }.distinct().size == records.size) { "纪念日编号重复" }
            require(records.count { it.pinned } <= 1) { "只能置顶一个纪念日" }
            return AppData(records, Preferences(AppTheme.valueOf(obj.getString("theme")), CountMode.valueOf(obj.getString("defaultMode")),obj.optBoolean("animations",true),AppPalette.fromKey(obj.optString("palette","CREAM"))),categories)
        }
        private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, max: Long): Long {
            var size = 0L
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; size += n; require(size <= max) { "文件过大" }; output.write(buffer, 0, n) }
            return size
        }
    }
}
