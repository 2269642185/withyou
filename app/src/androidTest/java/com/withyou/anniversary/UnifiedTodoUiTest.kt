package com.withyou.anniversary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UnifiedTodoUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun unifiedOwnershipDefaultSharingAndPrivateMigration() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val repository=Repository(context)
        val oldData=kotlinx.coroutines.runBlocking {repository.read()}
        kotlinx.coroutines.runBlocking {repository.save(oldData.copy(preferences=oldData.preferences.copy(theme=AppTheme.SYSTEM)))}
        val sessions=TodoSessionStore(context);val previous=sessions.read();val uid="local-unified-"+UUID.randomUUID()
        fun shell(s:String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(s)).use {it.readBytes()}}
        shell("svc wifi disable");shell("svc data disable")
        val mine=TodoTask(owner=uid,title="我的小目标")
        val peer=TodoTask(owner="peer",title="伙伴的小目标",shared=true,pairId="pair")
        val common=TodoTask(owner="peer",title="一起散步",kind=TodoKind.SHARED,shared=true,pairId="pair")
        TodoStore(context).write(TodoBook(uid,pairId="pair",partnerId="peer",partnerName="小杏",tasks=listOf(mine,peer,common),checks=listOf(TodoCheck(peer.id,"peer",TodoRules.checkDay(peer,TodoRules.today()),true,1,TodoRules.today()),TodoCheck(common.id,uid,TodoRules.today(),true))))
        try {
            sessions.save(TodoSession(uid,"小与你","invalid-test-token","invalid-test-refresh",System.currentTimeMillis()+7200000))
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                compose.waitUntil(15000) {compose.onAllNodesWithText("打卡").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText("打卡").performClick()
                compose.waitUntil(20000) {try {compose.onNodeWithText("新增事项").assertIsEnabled();true}catch(_:AssertionError){false}}
                compose.onNodeWithText("我的").assertDoesNotExist();compose.onNodeWithText("对方的").assertDoesNotExist();compose.onNodeWithText("一起完成").assertDoesNotExist()
                compose.onNodeWithText("今日进度").assertExists()
                compose.waitForIdle();Thread.sleep(400);shell("screencap -p /sdcard/Download/withyou-v16-unified-local.png")
                compose.onNodeWithText("新增事项").performClick()
                compose.onNode(hasSetTextAction() and hasText("事项标题")).performTextInput("默认让你看见")
                scenario.onActivity {a->a.currentFocus?.clearFocus();androidx.core.view.WindowCompat.getInsetsController(a.window,a.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
                compose.onNodeWithText("♡ 这件小事默认让伙伴看见").performScrollTo().assertExists()
                compose.onNodeWithText("保存事项").performScrollTo().performClick()
                compose.waitUntil(10000) {TodoStore(context).read(uid).tasks.any {it.title=="默认让你看见"}}
                val book=TodoStore(context).read(uid)
                assertTrue(book.tasks.single {it.title=="默认让你看见"}.shared)
                assertFalse(book.tasks.single {it.id==mine.id}.shared)
                assertEquals(1,book.pending.size)
                compose.onNodeWithTag("todo_list").performScrollToNode(hasText("一起散步"));compose.onNodeWithText("一起散步").assertExists()
                compose.onNodeWithTag("todo_list").performScrollToNode(hasText("伙伴的小目标"));compose.onNodeWithText("伙伴的小目标").assertExists()
                compose.onNodeWithTag("todo_list").performScrollToNode(hasText("我的小目标"));compose.onNodeWithText("我的小目标").assertExists()
            }
        } finally {kotlinx.coroutines.runBlocking {repository.save(oldData)};sessions.save(previous);shell("svc wifi enable");shell("svc data enable")}
    }
}
