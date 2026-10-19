package com.withyou.anniversary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in actual CloudBase integration using host-created temporary QA accounts. No embedded credentials. */
@RunWith(AndroidJUnit4::class)
class TodoCloudEvidenceTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun actualCloudPairChecksOfflineRestartAndAccountIsolation()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("todoCloudEvidence")=="true")
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val accounts=JSONArray(File(context.filesDir,"validation/todo-accounts.json").readText())
        val cloud=TodoCloud("withyou-instrumentation")
        val a=cloud.login(accounts.getJSONObject(0).getString("username"),accounts.getJSONObject(0).getString("password"))
        val b=cloud.login(accounts.getJSONObject(1).getString("username"),accounts.getJSONObject(1).getString("password"))
        val c=cloud.login(accounts.getJSONObject(2).getString("username"),accounts.getJSONObject(2).getString("password"))
        assertEquals(0,cloud.snapshot(c,TodoBook(c.uid)).getJSONArray("tasks").length())
        var pair=cloud.snapshot(a,TodoBook(a.uid)).getString("pair_id")
        if(pair.isBlank()) {
            val invite=cloud.pair(a,"create","").getString("code")
            cloud.pair(b,"join",invite)
            val pending=cloud.snapshot(a,TodoBook(a.uid)).getJSONArray("invites").getJSONObject(0)
            pair=cloud.pair(a,"confirm",pending.getString("id")).getString("pair_id")
        }
        val task=TodoTask(owner=a.uid,title="云端共同喝水"+java.util.UUID.randomUUID().toString().take(5),kind=TodoKind.SHARED,shared=true,pairId=pair)
        val body=task.json().put("type","task").put("expected",0)
        cloud.apply(a,TodoOperation(taskId=task.id,body=body.toString()))
        assertTrue(cloud.snapshot(b,TodoBook(b.uid)).getJSONArray("tasks").mapObjects(::taskFromJson).any {it.id==task.id})
        TodoSessionStore(context).save(a)
        TodoStore(context).write(TodoBook(a.uid))
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            compose.waitUntil(15000) {compose.onAllNodesWithText("打卡").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("打卡").performClick()
            compose.waitUntil(20000) {TodoStore(context).read(a.uid).tasks.any {it.id==task.id}}
            compose.waitUntil(10000) {compose.onAllNodesWithText(task.title).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(task.title).assertExists()
            // Restrict network at the shell, preserving the actual login and snapshot already cached.
            fun shell(command:String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).use {it.readBytes()}}
            shell("svc wifi disable");shell("svc data disable")
            try {
                compose.onAllNodesWithText("完成打卡").onLast().performScrollTo().performClick()
                compose.waitUntil(10000) {TodoStore(context).read(a.uid).pending.isNotEmpty()}
                assertTrue(TodoRules.complete(TodoStore(context).read(a.uid),task,a.uid,TodoRules.today()))
                scenario.recreate()
                compose.waitUntil(10000) {TodoRules.complete(TodoStore(context).read(a.uid),task,a.uid,TodoRules.today())}
                assertTrue(TodoStore(context).read(b.uid).pending.isEmpty())
            } finally {shell("svc wifi enable");shell("svc data enable")}
        }
        // Release the queued request through the real endpoint, then check both views independently.
        val offline=TodoStore(context).read(a.uid)
        for(op in offline.pending) cloud.apply(a,op)
        val remote=cloud.snapshot(b,TodoBook(b.uid))
        assertTrue(remote.getJSONArray("checks").mapObjects(::checkFromJson).any {it.taskId==task.id && it.user==a.uid && it.done})
        assertTrue(cloud.snapshot(c,TodoBook(c.uid)).getJSONArray("tasks").mapObjects(::taskFromJson).none {it.id==task.id})
        TodoSessionStore(context).save(null)
        cloud.signOut(a);cloud.signOut(b);cloud.signOut(c)
    }
}
