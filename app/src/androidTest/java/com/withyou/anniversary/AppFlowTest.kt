package com.withyou.anniversary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID
import java.io.File
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun waitText(text: String) { compose.waitUntil(10000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/withyou-$name.png")).use { it.readBytes() }
    }
    @Test fun createEditPinRestartAndDelete() {
        runBlocking { Repository(context).save(AppData()) }
        var scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText("添加第一个纪念日")
            screenshot("01-empty")
            compose.onNodeWithText("添加第一个纪念日").performClick()
            compose.onNode(hasSetTextAction() and hasText("纪念日标题")).performTextInput("我们在一起")
            compose.onNode(hasSetTextAction() and hasText("起始日期")).performTextReplacement(LocalDate.now().plusDays(3).toString())
            screenshot("06-editor")
            compose.onNodeWithText("保存纪念日").performClick()
            waitText("日子的故事")
            compose.onNodeWithText("3",useUnmergedTree=true).assertExists()
            compose.onNodeWithText("编辑",substring=false).performClick()
            compose.onNode(hasSetTextAction() and hasText("起始日期")).performTextReplacement(LocalDate.now().minusDays(1).toString())
            compose.onNodeWithText("保存纪念日").performClick()
            waitText("日子的故事")
            compose.onNodeWithText("2",useUnmergedTree=true).assertExists()
            compose.onNodeWithText("设为首页置顶").performScrollTo().performClick()
            compose.waitUntil(10000) { Repository(context).read().records.singleOrNull()?.pinned == true }
            scenario.close()
            scenario=ActivityScenario.launch(MainActivity::class.java)
            waitText("珍藏的日子  ·  1")
            compose.onNodeWithText("我们在一起").performClick()
            waitText("日子的故事")
            compose.onNodeWithText("2",useUnmergedTree=true).assertExists()
            compose.onNodeWithText("删除这个纪念日").performScrollTo().performClick()
            compose.onNodeWithText("再想想").performClick()
            assertEquals(1,Repository(context).read().records.size)
            compose.onNodeWithText("删除这个纪念日").performClick()
            compose.onNodeWithText("确认删除").performClick()
            waitText("添加第一个纪念日")
            assertTrue(Repository(context).read().records.isEmpty())
        } finally { scenario.close() }
    }
    @Test fun populatedHomeDetailsAndSettings() {
        val data=AppData(listOf(
            MemoryDay(UUID.randomUUID().toString(),"我们在一起",LocalDate.now().minusDays(365),"小杏",pinned=true),
            MemoryDay(UUID.randomUUID().toString(),"下一次旅行",LocalDate.now().plusDays(30),color=2),
            MemoryDay(UUID.randomUUID().toString(),"初次相遇",LocalDate.now().minusDays(730),color=1)
        ))
        runBlocking {Repository(context).save(data)}
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("珍藏的日子  ·  3")
            screenshot("02-home")
            compose.onNodeWithText("我们在一起").performClick()
            waitText("日子的故事");screenshot("03-detail")
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("设置",substring=false).performClick()
            waitText("留给日常的偏爱");screenshot("04-settings")
            compose.onNodeWithText("深色").performScrollTo().performClick()
            compose.waitUntil(10000) {Repository(context).read().preferences.theme==AppTheme.DARK}
            compose.waitForIdle()
            Thread.sleep(400) // Let the rendered frame catch up before the shell screenshot.
            screenshot("05-dark")
            compose.onNodeWithText("浅色").performScrollTo().performClick()
        }
    }
}
