package com.withyou.anniversary

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CompactTodoLayoutTest {
    @get:Rule val compose=createComposeRule()

    private fun layout(scale:Float,dark:Boolean) {
        val date=TodoRules.today()
        val task=TodoTask(owner="self",title="一起读十分钟",shared=true,pairId="pair")
        val book=TodoBook("self",pairId="pair",partnerId="peer",partnerName="xiaohui",tasks=listOf(task))
        var menu by mutableStateOf(false)
        var selected by mutableStateOf("")
        var peer by mutableStateOf(false)
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,scale),LocalMotionEnabled provides false) {
                MaterialTheme(colorScheme=withYouColors(dark)) {
                    Surface(Modifier.fillMaxSize()) {Box {Column(Modifier.width(320.dp).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        TodoProgressPanel(book,"小鱼",date,false)
                        CompactTodoCard(if(peer) task.copy(owner="peer") else task,book,"小鱼",date,false,
                            menu,{menu=true},{menu=false},{},{selected="history";menu=false},
                            {selected="edit";menu=false},{selected="archive";menu=false},{selected="delete";menu=false})
                    }}}
                }
            }
        }
        compose.onNodeWithTag("progress_name_self").assertTextEquals("小鱼")
        compose.onNodeWithTag("progress_name_partner").assertTextEquals("xiaohui")
        compose.onNodeWithText("我 · 小鱼").assertDoesNotExist()
        compose.onNodeWithText("没有截止日期").assertDoesNotExist()
        fun aligned(key:String) {
            val name=compose.onNodeWithTag("progress_name_$key").fetchSemanticsNode().boundsInRoot
            val count=compose.onNodeWithTag("progress_count_$key").fetchSemanticsNode().boundsInRoot
            assertTrue("Name and count must share a row",kotlin.math.abs(name.center.y-count.center.y)<4f)
        }
        aligned("self");aligned("partner")
        if(scale==1f) {val bounds=compose.onNodeWithTag("todo_card_${task.id}").getUnclippedBoundsInRoot();assertTrue(bounds.bottom-bounds.top<=148.dp)}
        val inst=InstrumentationRegistry.getInstrumentation()
        fun shot(suffix:String) {
            compose.waitForIdle()
            Thread.sleep(400)
            inst.waitForIdleSync()
            val file=File(inst.targetContext.getExternalFilesDir(null),"v161-$suffix.png")
            inst.uiAutomation.takeScreenshot().useBitmap {bitmap ->
                file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/${file.name}")).use {it.readBytes()}
        }
        shot("layout-$scale-$dark")
        compose.onNodeWithContentDescription("事项操作：${task.title}").performClick()
        val menuBounds=compose.onNodeWithTag("todo_action_menu").getUnclippedBoundsInRoot()
        assertTrue(menuBounds.bottom-menuBounds.top<=320.dp);assertTrue(menuBounds.right-menuBounds.left<=248.dp)
        compose.onNodeWithText("删除事项").assertIsDisplayed()
        shot("menu-$scale-$dark")
        compose.onNodeWithText("编辑事项").performClick()
        compose.runOnIdle {assertEquals("edit",selected);peer=true}
        compose.onNodeWithContentDescription("事项操作：${task.title}").performClick()
        compose.onNodeWithText("编辑事项").assertDoesNotExist()
        compose.onNodeWithText("删除事项").assertDoesNotExist()
        compose.onNodeWithText("查看打卡记录").performClick()
        compose.runOnIdle {assertEquals("history",selected)}
    }
    @Test fun compactNormalAndOwnerActions()=layout(1f,false)
    @Test fun narrowLargeFontHasAlignedProgressAndReachableMenu()=layout(1.6f,false)
    @Test fun darkExtraLargeFont()=layout(2f,true)
}

private inline fun Bitmap.useBitmap(block:(Bitmap)->Unit) {try {block(this)} finally {recycle()}}
