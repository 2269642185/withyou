package com.withyou.anniversary

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TodoStorageTest {
    private fun isolated()=object:ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        private val root=File(baseContext.cacheDir,"todo-storage-"+UUID.randomUUID()).apply {mkdirs()}
        override fun getFilesDir()=root
    }
    @Test fun atomicQueueAndAccountsSurviveRestartWithoutSharingCaches() {
        val context=isolated();val store=TodoStore(context)
        val task=TodoTask(owner="account-a",title="离线读书",kind=TodoKind.HABIT)
        val book=TodoBook("account-a",tasks=listOf(task),checks=listOf(TodoCheck(task.id,"account-a",LocalDate.of(2026,10,19),true,1)),pending=listOf(TodoOperation(taskId=task.id,body="{\"type\":\"check\"}")))
        store.write(book)
        assertEquals(book,TodoStore(context).read("account-a"))
        assertTrue(store.read("account-b").tasks.isEmpty());assertTrue(store.read("account-b").pending.isEmpty())
        assertEquals(book,TodoStore(context).read("account-a"))
        assertFalse(book.json().toString().contains("access_token"))
    }
    @Test fun tokensAreEncryptedAndLogoutClearsOnlySession() {
        val context=isolated()
        val session=TodoSession("account-a","小杏","test-access-not-a-real-token","test-refresh-not-a-real-token",9999999999999)
        TodoSessionStore(context).save(session)
        val bytes=File(context.filesDir,"todos/session.enc").readBytes()
        assertFalse(String(bytes).contains(session.access));assertFalse(String(bytes).contains(session.refresh))
        assertEquals(session,TodoSessionStore(context).read())
        TodoStore(context).write(TodoBook("account-a",tasks=listOf(TodoTask(owner="account-a",title="保留个人记录"))))
        TodoSessionStore(context).save(null)
        assertNull(TodoSessionStore(context).read());assertEquals(1,TodoStore(context).read("account-a").tasks.size)
    }
    @Test fun invalidLocalSnapshotDoesNotSilentlyResetData() {
        val context=isolated();val store=TodoStore(context)
        store.write(TodoBook("account-a"))
        val file=File(context.filesDir,"todos/accounts").listFiles()!!.single()
        file.writeText("broken")
        try {store.read("account-a");fail("corrupt data silently reset")} catch(_:org.json.JSONException) {}
        assertEquals("broken",file.readText())
    }
}
