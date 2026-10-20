package com.withyou.anniversary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Explicit opt-in only. Two disposable, user-authorized mailbox test accounts. */
class EmailCloudEvidenceTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun emailLoginPairSharedCheckinsAndRestartUseSameIdentity() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).use {String(it.readBytes())}
        val fixture=shell("cat /data/local/tmp/withyou-email-qa.json")
        assumeTrue("Opt-in mailbox QA fixture required",fixture.trim().startsWith("{"))
        val accounts=JSONObject(fixture).getJSONArray("accounts");assertEquals(2,accounts.length())
        val first=accounts.getJSONObject(0);val second=accounts.getJSONObject(1)
        val sessions=TodoSessionStore(context);val previous=sessions.read()
        val cloud by lazy {TodoCloud(context.getSharedPreferences("todo_device",android.content.Context.MODE_PRIVATE).getString("id",null) ?: "email-android-evidence")}
        var scenario:ActivityScenario<MainActivity>?=null
        fun waitText(text:String)=compose.waitUntil(20000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
        fun waitEnabled(text:String)=compose.waitUntil(25000) {try {compose.onNodeWithText(text).assertIsEnabled();true}catch(_:AssertionError){false}}
        fun openAndLogin(account:JSONObject):ActivityScenario<MainActivity> {
            sessions.save(null)
            val activity=ActivityScenario.launch(MainActivity::class.java)
            waitText("打卡");compose.onNodeWithText("打卡").performClick();waitText("邮箱或原用户名")
            compose.onNode(hasSetTextAction() and hasText("邮箱或原用户名")).performTextInput(account.getString("email"))
            compose.onNode(hasSetTextAction() and hasText("密码")).performTextInput(account.getString("password"))
            activity.onActivity {a->a.currentFocus?.clearFocus();androidx.core.view.WindowCompat.getInsetsController(a.window,a.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
            compose.onNodeWithTag("email_login_submit").performScrollTo().performClick();waitEnabled("新增事项")
            return activity
        }
        try {
            scenario=openAndLogin(first)
            assertEquals(first.getString("uid"),sessions.read()!!.uid)
            runBlocking {
                try {cloud.request("/auth/v1/user/contact",JSONObject().put("email","friend@example.test"),"invalid",method="PATCH");fail("Unauthenticated patch accepted")}
                catch(e:TodoCloudError) {assertTrue(e.status in listOf(400,401,403))}
            }
            compose.onNodeWithText("新增事项").performClick()
            compose.onNode(hasSetTextAction() and hasText("事项标题")).performTextInput("邮箱升级后的私密小目标")
            scenario!!.onActivity {a->a.currentFocus?.clearFocus();androidx.core.view.WindowCompat.getInsetsController(a.window,a.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
            compose.onNodeWithText("保存事项").performScrollTo().performClick()
            compose.waitUntil(30000) {val b=TodoStore(context).read(first.getString("uid"));b.tasks.any {it.title=="邮箱升级后的私密小目标"}&&b.pending.isEmpty()}
            val owner=sessions.read()!!
            runBlocking {
                val peer=cloud.login(second.getString("email"),second.getString("password"))
                assertEquals(second.getString("uid"),peer.uid)
                val invite=cloud.pair(owner,"create","").getString("code")
                cloud.pair(peer,"join",invite)
                val pending=cloud.snapshot(owner,TodoBook(owner.uid)).getJSONArray("invites").getJSONObject(0).getString("id")
                val pair=cloud.pair(owner,"confirm",pending).getString("pair_id")
                val task=TodoTask(owner=owner.uid,title="邮箱验证共同小约定",kind=TodoKind.SHARED,shared=true,pairId=pair)
                cloud.apply(owner,TodoOperation(taskId=task.id,body=task.json().put("type","task").put("expected",0).toString()))
                val snap=cloud.snapshot(peer,TodoBook(peer.uid))
                assertTrue(snap.getJSONArray("tasks").mapObjects(::taskFromJson).any {it.id==task.id})
                assertFalse(snap.getJSONArray("tasks").mapObjects(::taskFromJson).any {it.title=="邮箱升级后的私密小目标"})
                for(session in listOf(owner,peer)) cloud.apply(session,TodoOperation(taskId=task.id,body=JSONObject().put("type","check").put("id",task.id).put("pair_id",pair).put("day",TodoRules.today().toString()).put("done",true).put("expected",0).toString()))
                assertEquals(2,cloud.snapshot(peer,TodoBook(peer.uid)).getJSONArray("checks").mapObjects(::checkFromJson).count {it.taskId==task.id&&it.done})
            }
            scenario!!.recreate()
            waitText("新增事项")
            compose.onNodeWithTag("todo_list").performScrollToNode(hasText("邮箱升级后的私密小目标"))
            assertEquals(first.getString("uid"),sessions.read()!!.uid)
            scenario!!.close();scenario=null
            scenario=openAndLogin(second)
            compose.waitUntil(30000) {TodoStore(context).read(second.getString("uid")).tasks.any {it.title=="邮箱验证共同小约定"}}
            compose.onNodeWithTag("todo_list").performScrollToNode(hasText("邮箱验证共同小约定"))
            compose.onNodeWithText("双方都完成了 ♡").assertExists()
            compose.waitForIdle();Thread.sleep(400);shell("screencap -p /sdcard/Download/withyou-v17-live-email-checkin.png")
            val old=sessions.read()!!
            runBlocking {
                cloud.request("/auth/v1/user/revoke/all",JSONObject(),old.access,method="DELETE")
                val cutoff=cloud.request("/v1/rdb/rest/rpc/wy_revoke_old_sessions",JSONObject(),old.access)
                kotlinx.coroutines.delay((cutoff.optLong("wait_seconds",1)+1)*1000)
                try {cloud.snapshot(old,TodoBook(old.uid));fail("Old business token accepted")}
                catch(e:TodoCloudError) {assertTrue(e.reason.contains("SESSION_REVOKED"))}
            }
            scenario!!.onActivity {a->androidx.lifecycle.ViewModelProvider(a)[TodoViewModel::class.java].sync(true)}
            compose.waitUntil(20000) {sessions.read()==null}
            assertTrue(TodoStore(context).read(second.getString("uid")).tasks.isNotEmpty())
            scenario!!.close();scenario=null
            scenario=openAndLogin(second)
            assertEquals(old.uid,sessions.read()!!.uid)
            compose.waitUntil(30000) {TodoStore(context).read(old.uid).checks.count {it.done}>=2}
        } finally {scenario?.close();sessions.save(previous)}
    }
}
