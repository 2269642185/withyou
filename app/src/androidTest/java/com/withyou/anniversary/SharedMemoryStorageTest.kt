package com.withyou.anniversary

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SharedMemoryStorageTest {
    private fun isolated()=object:ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        private val root=File(baseContext.cacheDir,"share-test-"+UUID.randomUUID()).apply {mkdirs()}
        override fun getFilesDir()=root
    }
    @Test fun completeAppearanceAndPendingShareSurviveRestartAndStayAccountScoped() {
        val context=isolated();val store=TodoStore(context)
        for(template in CardTemplate.entries) {
            val record=MemoryDay(UUID.randomUUID().toString(),"一起走过",LocalDate.of(2024,2,29),"小杏",CountMode.ELAPSED,11,UUID.randomUUID().toString()+".jpg",true,
                CardAppearance("#F1DBC7",TextTone.CUSTOM,"#49392D","#624D45","#644D48","#654D49",.72f),UUID.randomUUID().toString(),template)
            val payload=memoryPayload(record)
            assertFalse(org.json.JSONObject(payload).has("photo"));assertFalse(org.json.JSONObject(payload).has("categoryId"))
            assertEquals(record.copy(photo=null,pinned=false,categoryId=null),memoryFromPayload(payload))
            val share=SharedMemory("a","pair",payload,"",3)
            val job=ShareJob(id=record.id,pairId="pair",payload=payload,photo=record.photo,expected=3)
            val book=TodoBook("a",pairId="pair",memories=listOf(share),shareJobs=listOf(job))
            store.write(book);assertEquals(book,TodoStore(context).read("a"));assertTrue(store.read("b").memories.isEmpty())
        }
        // Migration reads absent new fields without sharing or changing old private tasks.
        val old=TodoBook("legacy",tasks=listOf(TodoTask(owner="legacy",title="私密旧事项")))
        val j=old.json();j.remove("memories");j.remove("share_jobs");j.remove("synced_at")
        j.put("check_cursor",42);val migrated=bookFromJson(j);assertEquals(0L,migrated.checkCursor);assertFalse(migrated.tasks.single().shared);assertTrue(migrated.shareJobs.isEmpty())
    }
    @Test fun photoIntegrityRestartPruningAndAccountIsolation() {
        val context=isolated();val cache=SharedPhotoStore(context)
        val bitmap=Bitmap.createBitmap(40,60,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.rgb(215,185,155))
        val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,85,out);bitmap.recycle();val bytes=out.toByteArray()
        val path="a/${UUID.randomUUID()}/${sha256(bytes)}.jpg"
        cache.store("a",path,bytes);assertTrue(SharedPhotoStore(context).file("a",path).exists());assertFalse(cache.file("b",path).exists())
        try {cache.store("b",path,bytes+1);fail("bad bytes accepted")} catch(_:IllegalArgumentException) {}
        cache.retain("a",emptySet());assertFalse(cache.file("a",path).exists())
    }
}
