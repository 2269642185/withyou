package com.withyou.anniversary

import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HomeCategoryPanelTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun waitText(text:String) {compose.waitUntil(10000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun seed(animations:Boolean=true) {
        val category=Category(UUID.randomUUID().toString(),"旅行","travel",2)
        val records=(0..12).map {i ->MemoryDay(UUID.randomUUID().toString(),if(i==0) "旅行纪念" else "记录 $i",LocalDate.now().minusDays((i+1).toLong()),categoryId=if(i==0) category.id else null,pinned=i==0)}
        runBlocking {Repository(context).save(AppData(records,Preferences(animations=animations),listOf(category)))}
    }
    private fun pull(distance:Float=600f) {
        compose.onNodeWithTag("home_list").performTouchInput {swipe(Offset(centerX,180f),Offset(centerX,180f+distance),700)}
        compose.waitForIdle()
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        shell("screencap -p /sdcard/Download/v111-$name.png")
    }
    private fun shell(command:String):String=ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use {it.bufferedReader().readText().trim()}

    @Test fun collapsedByDefaultThresholdSelectionAndReturnPreserveFilter() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("与你")
            compose.onNodeWithText("管理分类").assertDoesNotExist()
            compose.onNodeWithText("全部 13").assertDoesNotExist()
            compose.onNodeWithContentDescription("打开设置").assertDoesNotExist()
            shot("home-collapsed")
            pull(55f)
            compose.onNodeWithText("管理分类").assertDoesNotExist()
            pull();waitText("管理分类");shot("home-expanded")
            compose.onNodeWithText("旅行 1").performClick()
            compose.onNodeWithText("收起",substring=false).performClick()
            waitText("当前：旅行")
            compose.onNodeWithText("管理分类").assertDoesNotExist()
            compose.onNodeWithText("旅行纪念").performClick()
            waitText("日子的故事")
            compose.onNodeWithContentDescription("返回").performClick()
            waitText("当前：旅行")
            compose.onNodeWithText("管理分类").assertDoesNotExist()
            shot("filtered-collapsed")
            pull();compose.onNodeWithText("旅行 1").assertIsSelected()
            compose.onNodeWithTag("home_list").performTouchInput {swipe(Offset(centerX,700f),Offset(centerX,180f),700)}
            compose.waitForIdle()
            compose.onNodeWithText("管理分类").assertDoesNotExist()
            compose.onNodeWithText("当前：旅行").assertExists()
            compose.onNodeWithText("设置",substring=false).performClick()
            waitText("留给日常的偏爱")
            compose.onNodeWithText("纪念日",substring=false).performClick()
            waitText("当前：旅行")
            compose.onNodeWithText("管理分类").assertDoesNotExist()
        }
    }
    @Test fun arrivingAtTopDoesNotRevealUntilAnotherPullAndHorizontalChipsStillScroll() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitText("与你")
            compose.onNodeWithTag("home_list").performScrollToIndex(1)
            pull(800f)
            compose.onNodeWithText("管理分类").assertDoesNotExist()
            pull();waitText("管理分类")
            compose.onNodeWithTag("category_chips").performTouchInput {swipeLeft()}
            compose.onNodeWithText("管理分类").assertExists()
            compose.onNodeWithText("收起",substring=false).performClick()
            compose.onNodeWithTag("home_list").performScrollToIndex(7)
            pull(400f)
            compose.onNodeWithText("管理分类").assertDoesNotExist()
        }
    }
    @Test fun reducedMotionLargeFontSmallScreenAndEmptyList() {
        val font=shell("settings get system font_scale")
        val animation=shell("settings get global animator_duration_scale")
        try {
            shell("settings put system font_scale 1.6")
            shell("settings put global animator_duration_scale 0")
            shell("wm size 840x1500")
            runBlocking {Repository(context).save(AppData(preferences=Preferences(animations=false)))}
            ActivityScenario.launch(MainActivity::class.java).use {
                waitText("添加第一个纪念日")
                compose.onNodeWithText("管理分类").assertDoesNotExist()
                // The accessibility action also makes a hidden panel usable without a drag gesture.
                val actions=compose.onNodeWithTag("home_list").fetchSemanticsNode().config[SemanticsActions.CustomActions]
                compose.runOnUiThread {assertTrue(actions.single().action())}
                waitText("管理分类");shot("large-font-expanded")
                compose.onNodeWithText("未分类 0").performClick()
                compose.onNodeWithText("收起",substring=false).performClick()
                waitText("当前：未分类")
                compose.onNodeWithText("管理分类").assertDoesNotExist()
                compose.onNodeWithText("添加第一个纪念日").performScrollTo().assertIsDisplayed()
                shot("large-font-collapsed")
            }
        } finally {
            shell("wm size reset")
            shell("settings put system font_scale ${if(font=="null") "1.0" else font}")
            shell("settings put global animator_duration_scale ${if(animation=="null") "1.0" else animation}")
        }
    }
}
