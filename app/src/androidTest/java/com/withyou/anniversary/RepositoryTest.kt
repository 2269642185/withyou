package com.withyou.anniversary

import android.graphics.Bitmap
import android.net.Uri
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    // A separate test directory avoids touching any user records in the target app.
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(target) {
        override fun getFilesDir() = File(target.cacheDir,"repository-test-files").apply { mkdirs() }
        override fun getCacheDir() = File(target.cacheDir,"repository-test-cache").apply { mkdirs() }
    }
    @Test fun saveReloadEditBackupRestoreWithPhoto() = runBlocking {
        val repo=Repository(context)
        val source=File(context.cacheDir,"source.jpg")
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) };bitmap.recycle()
        val photo=repo.importPhoto(Uri.fromFile(source))
        source.delete() // Imported photo must survive loss of the source.
        val category=Category(UUID.randomUUID().toString(),"恋爱","heart",8)
        val appearance=CardAppearance("#F2E1D0",TextTone.CUSTOM,"#49392D","#345678","#6B4D25","#35564B",.73f)
        val record=MemoryDay(UUID.randomUUID().toString(),"在一起",LocalDate.of(2024,2,29),"小杏",photo=photo,pinned=true,appearance=appearance,categoryId=category.id,template=CardTemplate.POLAROID)
        val original=AppData(listOf(record),Preferences(AppTheme.DARK,CountMode.ELAPSED,false),listOf(category))
        repo.save(original)
        assertEquals(original,Repository(context).read())
        assertTrue(File(repo.photos,photo).isFile)
        val edited=original.copy(records=listOf(record.copy(date=LocalDate.of(2026,10,19))))
        repo.save(edited)
        assertEquals(3,DateRules.count(Repository(context).read().records.single().date,LocalDate.of(2026,10,16),CountMode.INCLUSIVE).days)
        val backup=File(context.cacheDir,"backup.zip")
        repo.backup(Uri.fromFile(backup),original)
        repo.save(AppData())
        val restored=repo.restore(Uri.fromFile(backup))
        assertEquals(record.copy(photo=restored.records.single().photo),restored.records.single())
        assertEquals(original.preferences,restored.preferences)
        assertEquals(original.categories,restored.categories)
        assertTrue(File(repo.photos,restored.records.single().photo!!).isFile)
        assertEquals(restored,Repository(context).read())
    }
    @Test fun malformedAndTraversalBackupPreserveExistingSnapshot() = runBlocking {
        val repo=Repository(context)
        val data=AppData(listOf(MemoryDay(UUID.randomUUID().toString(),"保留",LocalDate.of(2020,1,1))))
        repo.save(data)
        val zip=File(context.cacheDir,"bad.zip")
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("../memories.json"));it.write("bad".toByteArray());it.closeEntry() }
        assertTrue(runCatching { repo.restore(Uri.fromFile(zip)) }.isFailure)
        assertEquals(data,repo.read())
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("memories.json"));it.write("{}".toByteArray());it.closeEntry() }
        assertTrue(runCatching { repo.restore(Uri.fromFile(zip)) }.isFailure)
        assertEquals(data,repo.read())
    }
    @Test fun concurrentReadsNeverInvalidateAtomicSave() = runBlocking {
        val repo=Repository(context)
        repo.save(AppData())
        val reader=launch(Dispatchers.IO) { repeat(100) {Repository(context).read()} }
        val record=MemoryDay(UUID.randomUUID().toString(),"并发保存",LocalDate.of(2026,1,1))
        repeat(20) {repo.save(AppData(listOf(record.copy(title="并发保存 $it"))))}
        reader.join()
        assertEquals("并发保存 19",Repository(context).read().records.single().title)
    }
    @Test fun legacyV1MigratesAndRejectsCorruptV2() {
        val record=MemoryDay(UUID.randomUUID().toString(),"旧纪念",LocalDate.of(2020,2,29),color=4,pinned=true)
        val json=org.json.JSONObject(Repository.encode(AppData(listOf(record))))
        json.put("version",1);json.remove("categories");json.remove("animations")
        json.getJSONArray("records").getJSONObject(0).remove("appearance")
        val migrated=Repository.decode(json.toString())
        assertEquals(record,migrated.records.single());assertTrue(migrated.categories.isEmpty());assertTrue(migrated.preferences.animations)
        val next=org.json.JSONObject(Repository.encode(migrated))
        next.getJSONArray("records").getJSONObject(0).getJSONObject("appearance").put("background","invalid")
        assertTrue(runCatching {Repository.decode(next.toString())}.isFailure)
        val category=Category(UUID.randomUUID().toString(),"旅行")
        val full=AppData(listOf(record.copy(categoryId=category.id)),categories=listOf(category))
        val dangling=org.json.JSONObject(Repository.encode(full)).put("categories",org.json.JSONArray())
        assertNull(Repository.decode(dangling.toString()).records.single().categoryId)
    }
    @Test fun celebrationLedgerClaimsOncePerDateAcrossInstances() {
        val id=UUID.randomUUID().toString();val day=LocalDate.of(2026,10,16)
        assertTrue(CelebrationLedger(context).claim(id,day))
        assertFalse(CelebrationLedger(context).claim(id,day))
        assertTrue(CelebrationLedger(context).claim(id,day.plusYears(1)))
    }

    @Test fun allTemplatesSurviveReloadBackupAndLegacyFallback() = runBlocking {
        val repo=Repository(context)
        val records=CardTemplate.entries.map {MemoryDay(UUID.randomUUID().toString(),it.label,LocalDate.of(2024,2,29),template=it)}
        val data=AppData(records)
        repo.save(data)
        assertEquals(data,Repository(context).read())
        val backup=File(context.cacheDir,"templates.zip")
        repo.backup(Uri.fromFile(backup),data)
        repo.save(AppData())
        assertEquals(data,repo.restore(Uri.fromFile(backup)))
        val json=org.json.JSONObject(Repository.encode(data))
        json.put("version",2)
        val array=json.getJSONArray("records")
        for(i in 0 until array.length()) array.getJSONObject(i).remove("template")
        assertTrue(Repository.decode(json.toString()).records.all {it.template==CardTemplate.CLASSIC})
        array.getJSONObject(0).put("template","future-template")
        assertEquals(CardTemplate.CLASSIC,Repository.decode(json.toString()).records.first().template)
        val old=File(context.cacheDir,"legacy-v2.zip")
        ZipOutputStream(old.outputStream()).use {it.putNextEntry(ZipEntry("memories.json"));it.write(json.toString().toByteArray());it.closeEntry()}
        repo.restore(Uri.fromFile(old))
        assertTrue(Repository(context).read().records.all {it.template==CardTemplate.CLASSIC})
    }
}
