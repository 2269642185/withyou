package com.withyou.anniversary

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class EmailUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val store=ViewModelStore()
    private fun vm():TodoViewModel {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        return ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[TodoViewModel::class.java]
    }
    @After fun clear()=store.clear()
    private fun shot(name:String) {
        compose.waitForIdle();Thread.sleep(400)
        val inst=InstrumentationRegistry.getInstrumentation()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/withyou-v17-$name.png")).use {it.readBytes()}
    }
    @Test fun smallWidthLargeFontRegistrationAndRecoveryRemainScrollable() {
        val model=vm()
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.6f)) {
                MaterialTheme(colorScheme=withYouColors(false)) {Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {Box {Box(Modifier.width(320.dp).windowInsetsPadding(WindowInsets.safeDrawing).clipToBounds()) {EmailLogin(TodoState(ready=true),model)}}}}
            }
        }
        compose.onNodeWithText("邮箱或原用户名").assertExists()
        shot("login-large")
        compose.onNodeWithText("邮箱注册").performClick()
        compose.onNodeWithText("注册激活码").assertDoesNotExist()
        compose.onNodeWithText("朋友的注册链接").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("确认密码").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("创建账号").performScrollTo().assertIsNotEnabled()
        shot("register-large")
        compose.onNodeWithText("登录",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("忘记密码").performScrollTo().performClick()
        compose.onNodeWithText("更新密码").performScrollTo().assertIsNotEnabled()
        shot("recovery-large")
    }
    @Test fun darkEmailBindingRequiresVerificationAndPreservesReadableActions() {
        val model=vm()
        compose.runOnUiThread {androidx.core.view.WindowCompat.getInsetsController(compose.activity.window,compose.activity.window.decorView).apply {isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false}}
        compose.setContent {MaterialTheme(colorScheme=withYouColors(true)) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).clipToBounds()) {
                EmailSecurity(TodoState(ready=true,contactEmail="friend@example.test"),model) {}
            }}
        }}
        compose.onNodeWithText("当前密码").assertExists()
        compose.onNodeWithText("确认绑定邮箱").performScrollTo().assertIsNotEnabled()
        shot("bind-dark")
    }
    @Test fun admissionPendingSessionSurvivesRestartWithoutGrantingAccess() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(base.cacheDir,"email-session-test-${System.nanoTime()}").apply {mkdirs()}
        val context=object:ContextWrapper(base) {override fun getFilesDir()=directory}
        val session=TodoSession("test-uid","朋友","test-access","test-refresh",10000,admitted=false)
        val saved=TodoSessionStore(context)
        saved.save(session)
        val restored=TodoSessionStore(context).read()!!
        assertFalse(restored.admitted);assertEquals(session.uid,restored.uid)
        saved.save(null)
    }
}
