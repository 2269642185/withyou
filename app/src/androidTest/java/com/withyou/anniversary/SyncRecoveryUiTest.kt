package com.withyou.anniversary

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Rule

class SyncRecoveryUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun recoveryLoginAndSafeDiagnosticAreReachableAtLargeFont() {
        val inst=InstrumentationRegistry.getInstrumentation();val holder=ViewModelStore()
        lateinit var model:TodoViewModel
        compose.runOnUiThread {model=TodoViewModel(inst.targetContext.applicationContext as Application);holder.put("sync-ui",model)}
        var diagnostic by mutableStateOf(false)
        val state=TodoState(ready=true,session=TodoSession("ui-test","朋友","fake","fake",1),book=TodoBook("ui-test"),reauthRequired=true)
        val detail=syncDiagnostic(TodoCloudError(400,"",SyncStage.WRITE_CHECK,"PGRST102","12345678-1234-1234-1234-123456789012"),3,0)
        try {
            compose.setContent {
                val density=LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density,1.6f)) {
                    MaterialTheme(colorScheme=withYouColors(false,AppPalette.MINT)) {
                        Surface(Modifier.width(320.dp).fillMaxHeight()) {
                            if(diagnostic) Column {SyncDiagnosticButton(detail)} else TodoAccountContent(state,model,{},{})
                        }
                    }
                }
            }
            compose.onNodeWithText("导出当前账号打卡").assertIsDisplayed()
            compose.onNodeWithText("邮箱或原用户名").performScrollTo().assertIsDisplayed()
            compose.runOnIdle {diagnostic=true}
            compose.onNodeWithText("同步详情").performClick()
            compose.onNodeWithText("复制诊断信息").assertIsDisplayed()
            compose.waitForIdle()
            android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/withyou-v181-sync-diagnostic.png")).use {it.readBytes()}
            compose.onNodeWithText("复制诊断信息").performClick()
            compose.onNodeWithText("同步诊断").assertDoesNotExist()
        } finally {compose.runOnUiThread {holder.clear()}}
    }
}
