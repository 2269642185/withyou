package com.withyou.anniversary

import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StatusFacesUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private fun shot(name:String) {
        compose.waitForIdle()
        val inst=InstrumentationRegistry.getInstrumentation()
        Thread.sleep(350);inst.waitForIdleSync()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/withyou-faces-$name.png")).use {it.readBytes()}
    }
    @Test fun customFacesAndDisabledChoiceSurviveRestartAndLegacyBackup()=runBlocking {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(base.cacheDir,"faces-${System.nanoTime()}").apply {mkdirs()}
        fun repo()=Repository(object:ContextWrapper(base) {override fun getFilesDir()=dir})
        val value=AppData(preferences=Preferences(statusFaces=StatusFaces(false,StatusFace.PARTY,StatusFace.SLEEPY,StatusFace.SAD)))
        repo().save(value);assertEquals(value,repo().read())
        assertEquals(value,Repository.decode(Repository.encode(value)))
        val legacy=JSONObject(Repository.encode(value)).apply {remove("statusFaces")}
        assertEquals(StatusFaces(),Repository.decode(legacy.toString()).preferences.statusFaces)
        legacy.put("statusFaces",JSONObject().put("enabled",false).put("OVERDUE","FUTURE"))
        val unknown=Repository.decode(legacy.toString()).preferences.statusFaces
        assertFalse(unknown.enabled);assertEquals(StatusFace.CRYING,unknown.overdue)
    }
    @Test fun pickerCancelConfirmToggleAndResetWorkAtLargeFont() {
        var value by mutableStateOf(StatusFaces());var dark by mutableStateOf(false)
        compose.setContent {
            val d=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(d,1.6f),LocalMotionEnabled provides false) {
                MaterialTheme(colorScheme=withYouColors(dark,AppPalette.MINT)) {
                    Surface(Modifier.width(320.dp).fillMaxHeight()) {
                        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp)) {StatusFaceSettings(value) {value=it}}
                    }
                }
            }
        }
        compose.onNodeWithTag("choose_face_OVERDUE").performScrollTo().performClick()
        compose.onNodeWithTag("pick_face_CRYING").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("pick_face_PARTY").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {assertEquals(StatusFace.CRYING,value.overdue)}
        compose.onNodeWithTag("choose_face_OVERDUE").performClick()
        compose.onNodeWithTag("pick_face_SAD").performScrollTo().performClick()
        shot("picker-large-mint")
        compose.onNodeWithTag("confirm_status_face").performClick()
        compose.runOnIdle {assertEquals(StatusFace.SAD,value.overdue);dark=true}
        compose.onNodeWithTag("status_faces_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("choose_face_OVERDUE").assertDoesNotExist()
        compose.runOnIdle {assertFalse(value.enabled);assertEquals(StatusFace.SAD,value.overdue)}
        compose.onNodeWithTag("status_faces_toggle").performClick()
        compose.onNodeWithTag("reset_status_faces").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(StatusFaces(),value)}
        shot("settings-large-dark")
    }
    @Test fun cardsShowActualOwnerStateAndHideOnlyFacesWhenDisabled() {
        val date=TodoRules.today()
        val pending=TodoTask(owner="self",title="读十分钟")
        val overdue=TodoTask(owner="self",title="给花浇水",due=date.minusDays(1))
        val peer=pending.copy(id="peer-task",owner="peer",title="伙伴的散步",shared=true,pairId="pair")
        val book=TodoBook("self",pairId="pair",partnerId="peer",partnerName="小鱼",checks=listOf(TodoCheck(peer.id,"peer",TodoRules.checkDay(peer,date),true)))
        var enabled by mutableStateOf(true);var dark by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalStatusFaces provides StatusFaces(enabled),LocalMotionEnabled provides false) {
                MaterialTheme(colorScheme=withYouColors(dark,AppPalette.MINT)) {
                    Surface(Modifier.fillMaxSize()) {Column(Modifier.width(360.dp).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("把小事，一起坚持",style=MaterialTheme.typography.titleLarge)
                        listOf(pending,overdue,peer).forEach {task ->CompactTodoCard(task,book,"小慧",date,false,false,{},{},{},{},{},{},{})}
                    }}
                }
            }
        }
        compose.onNodeWithTag("face_CALM",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("face_CRYING",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("face_HAPPY",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("已打卡 · 待办").assertIsDisplayed()
        shot("cards-mint")
        compose.runOnIdle {dark=true};shot("cards-dark")
        compose.runOnIdle {enabled=false}
        compose.onNodeWithTag("face_CRYING",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithText("已逾期",substring=true).assertIsDisplayed()
        compose.onNodeWithText("已打卡 · 待办").assertIsDisplayed()
    }
}
