package com.withyou.anniversary

import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class CheckHistoryUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val date=LocalDate.of(2026,10,22)
    private val task=TodoTask(owner="a",title="去办公室")
    private val check=TodoCheck(task.id,"a",LocalDate.of(1970,1,1),true,1,date,Instant.parse("2026-10-22T00:19:32Z"))
    private fun shot(name:String) {
        compose.waitForIdle();Thread.sleep(350)
        val inst=InstrumentationRegistry.getInstrumentation();inst.waitForIdleSync()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/withyou-v182-$name.png")).use {it.readBytes()}
    }
    @Test fun oneRecordIsCompactAndTimeExpandsInPlace() {
        var open by mutableStateOf(true)
        compose.setContent {CompositionLocalProvider(LocalMotionEnabled provides false) {MaterialTheme(colorScheme=withYouColors(false,AppPalette.MINT)) {
            Surface(Modifier.fillMaxSize()) {if(open) CheckHistorySheet(task,TodoBook("a",checks=listOf(check)),"小慧",date) {open=false}}
        }}}
        compose.onNodeWithText("今天 08:19").assertIsDisplayed()
        val bounds=compose.onNodeWithTag("history_panel").getUnclippedBoundsInRoot();assertTrue(bounds.bottom-bounds.top<420.dp)
        shot("history-short")
        compose.onNodeWithText("今天 08:19").performClick()
        compose.onNodeWithText("2026年10月22日 08:19:32").assertIsDisplayed()
        compose.onNodeWithText("上海时间",substring=true).assertDoesNotExist()
        shot("history-detail")
        compose.onNodeWithText("2026年10月22日 08:19:32").performClick()
        compose.onNodeWithText("今天 08:19").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭打卡记录").performClick()
        compose.onNodeWithText("打卡记录").assertDoesNotExist()
    }
    @Test fun collectionStartsClosedAndUndoRestoresActiveTask() {
        var expanded by mutableStateOf(false)
        var book by mutableStateOf(TodoBook("a",checks=listOf(check.copy(completedOn=date.minusDays(1),completedAt=Instant.parse("2026-10-21T15:59:59Z")))))
        compose.setContent {CompositionLocalProvider(LocalMotionEnabled provides false) {MaterialTheme(colorScheme=withYouColors(true,AppPalette.MINT)) {
            Surface(Modifier.fillMaxSize()) {Column(Modifier.width(320.dp).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                if(CheckTimes.collected(task,book,date)) {
                    CompletedHeader(1,expanded) {expanded=!expanded}
                    if(expanded) CompletedTodoRow(task,book,"小慧",date,false,{}, {book=book.copy(checks=listOf(check.copy(done=false,completedOn=null,completedAt=null)))},{},{})
                } else Text("恢复到未完成列表")
            }}
        }}}
        compose.onNodeWithTag("collected_${task.id}").assertDoesNotExist()
        compose.onNodeWithTag("completed_group_toggle").performClick()
        compose.onNodeWithTag("collected_${task.id}").assertIsDisplayed()
        compose.onNodeWithText("昨天 23:59").assertIsDisplayed();shot("collected-dark")
        compose.onNodeWithContentDescription("已完成事项操作：去办公室").performClick()
        compose.onNodeWithText("撤销完成").performClick()
        compose.onNodeWithText("恢复到未完成列表").assertIsDisplayed()
    }
    @Test fun timestampQueueAndLegacyRecordsSurviveStoreRecreation() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(base.cacheDir,"checktime-${System.nanoTime()}").apply {mkdirs()}
        fun store()=TodoStore(object:ContextWrapper(base) {override fun getFilesDir()=dir})
        val op=TodoOperation(taskId=task.id,body=JSONObject().put("type","check").put("completed_at",check.completedAt.toString()).toString())
        val book=TodoBook("a",tasks=listOf(task),checks=listOf(check),pending=listOf(op));store().write(book)
        assertEquals(book,store().read("a"));assertEquals(check.completedAt,checkFromJson(check.json()).completedAt)
        val legacy=check.json().apply {remove("completed_at");put("updated_at","2026-10-23T00:00:00Z")}
        assertNull(checkFromJson(legacy).completedAt)
        assertTrue(CheckTimes.detail(checkFromJson(legacy)).endsWith("具体时间未记录"))
        assertEquals(check.completedOn,checkFromJson(legacy).completedOn)
    }
    @Test fun longSharedHistoryLargeFontScrollsAndCloseStaysVisible() {
        val t=task.copy(kind=TodoKind.SHARED,shared=true,pairId="pair",notes="较长的备注，".repeat(80))
        val rows=(0..20).flatMap {n->listOf("a","b").map {uid->check.copy(user=uid,day=date.minusDays(n.toLong()),completedOn=date.minusDays(n.toLong()),completedAt=check.completedAt!!.minusSeconds(n*86400L))}}
        compose.setContent {
            val d=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(d,1.6f),LocalMotionEnabled provides false) {MaterialTheme(colorScheme=withYouColors(true,AppPalette.MINT)) {
                Surface(Modifier.fillMaxSize()) {CheckHistorySheet(t,TodoBook("a",pairId="pair",partnerId="b",partnerName="小鱼",checks=rows),"小慧",date) {}}
            }}
        }
        compose.onNodeWithText("分别记录每个人的完成情况").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭打卡记录").assertIsDisplayed()
        compose.onNodeWithText("查看备注").performClick()
        compose.onNodeWithTag("check_history_list").performTouchInput {swipeUp()}
        compose.onNodeWithContentDescription("关闭打卡记录").assertIsDisplayed()
        shot("history-large-dark")
    }
}
