package com.withyou.anniversary

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
class UpgradeFlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun waitText(text:String) {compose.waitUntil(10000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun revealCategories() {
        compose.onNodeWithTag("home_list").performTouchInput {swipe(start=androidx.compose.ui.geometry.Offset(centerX,200f),end=androidx.compose.ui.geometry.Offset(centerX,800f),durationMillis=700)}
        waitText("管理分类")
    }
    private fun shot(name:String) {compose.waitForIdle();android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/v11-$name.png")).use {it.readBytes()}}
    @Test fun createCategoryCustomColorsRenameAndDeleteWithoutLosingRecords() {
        val repo=Repository(context)
        runBlocking {repo.save(AppData(listOf(MemoryDay(UUID.randomUUID().toString(),"旧的日子",LocalDate.now().minusDays(10)))))}
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            waitText("与你");revealCategories();compose.onNodeWithText("管理分类").performClick()
            compose.onNodeWithText("创建分类").performClick()
            compose.onNode(hasSetTextAction() and hasText("分类名称")).performTextInput("旅行")
            shot("category-create")
            compose.onNodeWithText("保存分类").performClick()
            compose.waitUntil(10000) {repo.read().categories.size==1}
            compose.onNodeWithContentDescription("返回").performClick()
            revealCategories()
            compose.onNodeWithText("旅行 0").performClick()
            compose.onNodeWithText("这个分类还没有纪念日").assertExists()
            compose.onNodeWithText("新增纪念日").performClick()
            compose.onNode(hasSetTextAction() and hasText("纪念日标题")).performTextInput("出发的日子")
            // Native IME transitions are not synchronized by Compose's test clock. Hide it and
            // await its inset before scrolling/tapping a chip at the edge of the viewport.
            scenario.onActivity {activity ->androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
            compose.waitUntil(5000) {
                var visible=false
                scenario.onActivity {activity ->visible=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true}
                !visible
            }
            compose.onNodeWithText("旅行",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("旅行",substring=false).assertIsSelected()
            compose.onNodeWithText("高级配色",substring=false).performScrollTo().performClick()
            val bg=hasSetTextAction() and hasText("自定义背景 HEX")
            compose.onNode(bg).performScrollTo().performTextReplacement("#GGGGGG")
            compose.onNodeWithText("保存纪念日").assertIsNotEnabled()
            compose.onNode(bg).performTextReplacement("#E1F2E7")
            compose.onNode(hasSetTextAction() and hasText("天数颜色 HEX")).performScrollTo().performTextReplacement("#35597F")
            shot("advanced-colors")
            compose.onNodeWithText("保存纪念日").assertIsEnabled().performClick()
            waitText("日子的故事")
            val saved=repo.read().records.first {it.title=="出发的日子"}
            assertEquals("#E1F2E7",saved.appearance.background);assertEquals("#35597F",saved.appearance.days)
            assertEquals(repo.read().categories.single().id,saved.categoryId)
            shot("colored-detail")
            compose.onNodeWithContentDescription("返回").performClick()
            revealCategories()
            compose.onNodeWithText("管理分类").performClick()
            compose.onNodeWithText("编辑",substring=false).performClick()
            compose.onNode(hasSetTextAction() and hasText("分类名称")).performTextReplacement("出游")
            compose.onNodeWithText("保存分类").performClick()
            compose.waitUntil(10000) {repo.read().categories.single().name=="出游"}
            compose.onNodeWithContentDescription("返回").performClick()
            revealCategories()
            compose.onNodeWithText("出游 1").performClick()
            compose.onNodeWithText("出发的日子").assertExists()
            shot("filtered-home")
            compose.onNodeWithText("管理分类").performClick()
            compose.onNodeWithText("删除",substring=false).performClick()
            compose.onNodeWithText("分类中的纪念日会保留，并转入“未分类”。").assertExists()
            compose.onNodeWithText("删除分类",substring=false).performClick()
            compose.waitUntil(10000) {repo.read().categories.isEmpty()}
            assertEquals(2,repo.read().records.size)
            assertTrue(repo.read().records.all {it.categoryId==null})
        }
    }
}
