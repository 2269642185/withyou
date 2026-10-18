package com.withyou.anniversary

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
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
class WidgetConfigurationTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun shell(command:String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use {it.readBytes()}
    @Test fun configureChooseRecordIndependentColorsAndReopen() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=Repository(context);val before=repo.read()
        val first=MemoryDay(UUID.randomUUID().toString(),"第一纪念",LocalDate.now().minusDays(10))
        val second=MemoryDay(UUID.randomUUID().toString(),"第二纪念",LocalDate.now().plusDays(10))
        runBlocking {repo.save(AppData(listOf(first,second),Preferences(theme=AppTheme.DARK)))}
        shell("appwidget grantbind --package ${context.packageName}")
        val host=AppWidgetHost(context,993);val id=host.allocateAppWidgetId()
        try {
            assertTrue(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id,ComponentName(context,SmallAnniversaryWidget::class.java)))
            val intent=Intent(context,WidgetConfigurationActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
            ActivityScenario.launch<WidgetConfigurationActivity>(intent).use {
                compose.waitUntil(10000) {compose.onAllNodesWithText("第二纪念 · ${second.date}").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText("第二纪念 · ${second.date}").performClick()
                compose.onNode(isToggleable()).performScrollTo().performClick()
                compose.onNodeWithContentDescription("可可 #73584A").performScrollTo().performClick()
                compose.onNodeWithContentDescription("文字色 #FFFFFF").performScrollTo().performClick()
                compose.waitForIdle()
                shell("screencap -p /sdcard/Download/v11-widget-config.png")
                compose.onNodeWithText("保存小组件").performClick()
                compose.waitUntil(10000) {WidgetStore(context).get(id)?.text=="#FFFFFF"}
            }
            assertEquals(WidgetChoice(second.id,false,"#73584A","#FFFFFF"),WidgetStore(context).get(id))
            ActivityScenario.launch<WidgetConfigurationActivity>(intent).use {
                compose.waitUntil(10000) {compose.onAllNodesWithText("第二纪念 · ${second.date}").fetchSemanticsNodes().isNotEmpty()}
                compose.onNode(isToggleable()).assertIsOff()
                assertEquals(second.id,WidgetStore(context).get(id)?.recordId)
                compose.onNode(isToggleable()).performScrollTo().performClick()
                compose.onNode(isToggleable()).assertIsOn()
                compose.onNodeWithText("大尺寸预览").performScrollTo().performClick()
                compose.onNodeWithTag("widget_preview").performScrollTo().assertIsDisplayed()
                shell("screencap -p /sdcard/Download/v130-widget-preview.png")
                compose.onNodeWithText("保存小组件").performClick()
                compose.waitUntil(10000) {WidgetStore(context).get(id)?.followColors==true}
                assertEquals(WidgetChoice(second.id,true,"#73584A","#FFFFFF"),WidgetStore(context).get(id))
            }
        } finally {
            host.deleteAppWidgetId(id);WidgetStore(context).delete(id)
            shell("appwidget revokebind --package ${context.packageName}")
            runBlocking {repo.save(before)}
        }
    }
}
