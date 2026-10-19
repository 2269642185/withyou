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
class TodoUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun offlineCreateEditArchiveRestartAndDifferentAccount() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val sessions=TodoSessionStore(context);val previous=sessions.read()
        val uid="local-ui-"+UUID.randomUUID()
        fun shot(name:String) {
            compose.waitForIdle()
            Thread.sleep(400)
            inst.waitForIdleSync()
            android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/withyou-todo-$name.png")).use {it.readBytes()}
        }
        fun shell(command:String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).use {it.readBytes()}}
        fun waitEnabled(text:String) {compose.waitUntil(20000) {try {compose.onNodeWithText(text).assertIsEnabled();true} catch(_:AssertionError) {false}}}
        shell("svc wifi disable");shell("svc data disable")
        try {
            sessions.save(TodoSession(uid,"离线验证","invalid-local-test-token","invalid-local-test-refresh",System.currentTimeMillis()+7200000))
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                compose.waitUntil(10000) {compose.onAllNodesWithText("打卡").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText("打卡").performClick();waitEnabled("新增事项")
                compose.onNodeWithText("新增事项").performClick()
                compose.onNode(hasSetTextAction() and hasText("事项标题")).performTextInput("今天的小进步")
                compose.onNode(hasSetTextAction() and hasText("备注（可选）")).performTextInput("离线先保存")
                scenario.recreate()
                compose.waitUntil(10000) {compose.onAllNodes(hasSetTextAction() and hasText("事项标题")).fetchSemanticsNodes().isNotEmpty()}
                compose.onNode(hasSetTextAction() and hasText("事项标题")).assertTextContains("今天的小进步")
                compose.onNode(hasSetTextAction() and hasText("备注（可选）")).assertTextContains("离线先保存")
                scenario.onActivity {activity ->
                    activity.currentFocus?.clearFocus()
                    androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                }
                compose.waitUntil(10000) {var hidden=false;scenario.onActivity {activity->hidden=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())!=true};hidden}
                shot("editor")
                compose.onNodeWithText("保存事项").performScrollTo().performClick()
                compose.waitUntil(10000) {TodoStore(context).read(uid).tasks.any {it.title=="今天的小进步"}}
                assertEquals(1,TodoStore(context).read(uid).pending.size)
                compose.waitUntil(10000) {compose.onAllNodesWithText("新增事项").fetchSemanticsNodes().isNotEmpty()}
                shot("offline-list")
                compose.onNodeWithContentDescription("事项操作：今天的小进步").performClick()
                compose.onNodeWithText("编辑事项").performClick()
                compose.onNode(hasSetTextAction() and hasText("事项标题")).performTextReplacement("今天更好一点")
                scenario.onActivity {activity ->
                    activity.currentFocus?.clearFocus()
                    androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                }
                compose.waitUntil(10000) {var hidden=false;scenario.onActivity {activity->hidden=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())!=true};hidden}
                compose.onNodeWithText("保存事项").performScrollTo().performClick()
                compose.waitUntil(10000) {TodoStore(context).read(uid).tasks.single().title=="今天更好一点"}
                scenario.recreate()
                compose.waitUntil(10000) {compose.onAllNodesWithText("今天更好一点").fetchSemanticsNodes().isNotEmpty()}
                waitEnabled("新增事项")
                shot("restart-list")
                compose.onNodeWithContentDescription("事项操作：今天更好一点").performClick()
                compose.onNodeWithText("归档事项").performClick()
                compose.waitUntil(10000) {TodoStore(context).read(uid).tasks.single().archived}
                assertEquals(3,TodoStore(context).read(uid).pending.size)
            }
            val second="local-ui-"+UUID.randomUUID()
            sessions.save(TodoSession(second,"另一个账号","invalid-other-token","invalid-other-refresh",System.currentTimeMillis()+7200000))
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10000) {compose.onAllNodesWithText("打卡").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText("打卡").performClick()
                compose.waitUntil(10000) {compose.onAllNodesWithText("从一件小事开始").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText("今天更好一点").assertDoesNotExist()
                assertTrue(TodoStore(context).read(second).pending.isEmpty())
                shot("isolated-account")
            }
        } finally {sessions.save(previous);shell("svc wifi enable");shell("svc data enable")}
    }
}
