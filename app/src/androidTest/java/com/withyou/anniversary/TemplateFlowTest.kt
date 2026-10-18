package com.withyou.anniversary

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.ParcelFileDescriptor
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
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TemplateFlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun waitText(text:String) {compose.waitUntil(10000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun shell(command:String):String=ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use {it.bufferedReader().readText().trim()}
    private fun shot(name:String) {compose.waitForIdle();Thread.sleep(300);shell("screencap -p /sdcard/Download/v120-$name.png")}
    private fun choose(template:CardTemplate) {
        compose.onNodeWithTag("template_chooser").performScrollTo().performClick()
        compose.onNodeWithTag("template_grid").performScrollToIndex(template.ordinal)
        compose.onNodeWithTag("choose_${template.key}").performClick()
        compose.onNodeWithText("完成",substring=false).performClick()
        compose.waitForIdle()
    }
    private fun fixturePhoto():String {
        val repo=Repository(context)
        val name="${UUID.randomUUID()}.jpg"
        val bitmap=Bitmap.createBitmap(900,700,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader=LinearGradient(0f,0f,0f,700f,intArrayOf(0xFFEBCBA8.toInt(),0xFFA6B9B0.toInt()),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,900f,700f,paint);paint.shader=null
        paint.color=0xFFFFEBC3.toInt();canvas.drawCircle(680f,150f,70f,paint)
        paint.color=0xFF7C998B.toInt();canvas.drawOval(-150f,330f,600f,1100f,paint)
        paint.color=0xFF4F7169.toInt();canvas.drawOval(350f,420f,1150f,1050f,paint)
        File(repo.photos,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
        return name
    }

    @Test fun selectCancelSaveSwitchBackAndRestartKeepsPhotoAndAppearance() {
        val repo=Repository(context)
        val original=MemoryDay(UUID.randomUUID().toString(),"我们的山海之约",LocalDate.now().minusDays(99),"小杏",photo=fixturePhoto(),appearance=CardAppearance(background="#DAEDE2"))
        runBlocking {repo.save(AppData(listOf(original),Preferences(animations=true)))}
        var scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            waitText(original.title);compose.onNodeWithText(original.title).performClick();waitText("日子的故事")
            compose.onNodeWithText("编辑",substring=false).performClick()
            choose(CardTemplate.TICKET)
            assertEquals(original,repo.read().records.single())
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("放弃修改",substring=false).performClick();waitText("日子的故事")
            assertEquals(original,repo.read().records.single())
            compose.onNodeWithText("编辑",substring=false).performClick()
            choose(CardTemplate.POLAROID)
            shot("editor-polaroid")
            compose.onNodeWithText("保存纪念日").performClick();waitText("日子的故事")
            compose.waitUntil(10000) {repo.read().records.single().template==CardTemplate.POLAROID}
            assertEquals(original.copy(template=CardTemplate.POLAROID),repo.read().records.single())
            compose.onNodeWithText("编辑",substring=false).performClick()
            choose(CardTemplate.MINIMAL);choose(CardTemplate.POLAROID)
            compose.onNodeWithText("保存纪念日").performClick();waitText("日子的故事")
            scenario.close();scenario=ActivityScenario.launch(MainActivity::class.java)
            waitText(original.title);compose.onNodeWithText(original.title).performClick();waitText("日子的故事")
            compose.onNodeWithTag("card_polaroid").assertExists()
            assertEquals(original.copy(template=CardTemplate.POLAROID),Repository(context).read().records.single())
            shot("restart-polaroid")
        } finally {scenario.close()}
    }

    @Test fun allSixTemplatesRenderActualHomeAndDetail() {
        val repo=Repository(context);val photo=fixturePhoto()
        for(template in CardTemplate.entries) {
            val record=MemoryDay(UUID.randomUUID().toString(),"一起去看山和海",LocalDate.now().minusDays(365),"小杏",photo=if(template.photoFocused) photo else null,pinned=true,template=template)
            runBlocking {repo.save(AppData(listOf(record),Preferences(animations=false)))}
            ActivityScenario.launch(MainActivity::class.java).use {
                waitText(record.title);shot("home-${template.key}")
                compose.onNodeWithText(record.title).performClick();waitText("日子的故事")
                compose.onNodeWithText("366",useUnmergedTree=true).assertExists()
                shot("detail-${template.key}")
                if(template==CardTemplate.CLASSIC) {
                    compose.onNodeWithText("编辑",substring=false).performClick()
                    compose.onNodeWithTag("template_chooser").performScrollTo().performClick()
                    shot("picker-top")
                    compose.onNodeWithTag("template_grid").performScrollToIndex(4);shot("picker-bottom")
                }
            }
        }
    }

    @Test fun largeFontDarkModeLongTitlesAndMissingPhotosRemainScrollable() {
        val previousFont=shell("settings get system font_scale")
        val previousMotion=shell("settings get global animator_duration_scale")
        shell("settings put system font_scale 1.6");shell("settings put global animator_duration_scale 0");shell("wm size 840x1500")
        try {
            val repo=Repository(context)
            for(template in CardTemplate.entries) {
                val record=MemoryDay(UUID.randomUUID().toString(),"和你走过的每一段旅程都值得记住，这是一段很长很长的纪念日标题",LocalDate.of(1,1,1),"这是一个较长的昵称用于检查换行",photo=if(template.photoFocused) "${UUID.randomUUID()}.jpg" else null,template=template)
                runBlocking {repo.save(AppData(listOf(record),Preferences(AppTheme.DARK,animations=false)))}
                ActivityScenario.launch(MainActivity::class.java).use {
                    waitText(record.title);compose.onNodeWithText(record.title).performClick();waitText("日子的故事")
                    compose.onNodeWithText(record.title,useUnmergedTree=true).assertExists()
                    compose.onNodeWithText("设为首页置顶").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText(record.title).performScrollTo();shot("large-dark-${template.key}")
                    if(template==CardTemplate.PHOTO) {
                        compose.onNodeWithText("编辑",substring=false).performClick()
                        compose.onNodeWithTag("template_chooser").performScrollTo().performClick()
                        compose.onNodeWithTag("template_grid").performScrollToIndex(5)
                        compose.onNodeWithTag("choose_note").assertIsDisplayed();shot("picker-large-dark")
                        compose.onNodeWithText("完成",substring=false).performClick()
                        compose.onNodeWithText("保存纪念日").assertIsDisplayed()
                    }
                }
            }
            // Future/today states and photo-free saving use the same production editor.
            val emptyPhoto=MemoryDay(UUID.randomUUID().toString(),"明天见",LocalDate.now().plusDays(1),template=CardTemplate.PHOTO)
            runBlocking {repo.save(AppData(listOf(emptyPhoto),Preferences(animations=false)))}
            ActivityScenario.launch(MainActivity::class.java).use {
                waitText("明天见");compose.onNodeWithText("明天见").performClick();waitText("日子的故事")
                compose.onNodeWithText("还有",useUnmergedTree=true).assertExists()
                compose.onNodeWithText("编辑",substring=false).performClick()
                compose.onNodeWithText("添加照片",substring=false).performScrollTo().assertIsDisplayed()
                compose.onNode(hasSetTextAction() and hasText("起始日期")).performScrollTo().performTextReplacement(LocalDate.now().toString())
                compose.onNodeWithText("保存纪念日").performClick();waitText("日子的故事")
                compose.onNodeWithText("就是今天",useUnmergedTree=true).assertExists()
                assertNull(repo.read().records.single().photo)
            }
        } finally {
            shell("wm size reset")
            shell("settings put system font_scale ${previousFont.toFloatOrNull() ?: 1f}")
            shell("settings put global animator_duration_scale ${previousMotion.toFloatOrNull() ?: 1f}")
        }
    }
}
