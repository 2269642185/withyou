package com.withyou.anniversary

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionPolicyTest {
    @get:Rule val compose=createComposeRule()
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use {it.bufferedReader().readText().trim()}
    @Test fun appAndSystemAnimationSwitchesAreRespected() {
        val before=shell("settings get global animator_duration_scale")
        val preference=mutableStateOf(true)
        try {
            shell("settings put global animator_duration_scale 1")
            compose.setContent {Text(if(rememberMotionEnabled(preference.value)) "动画开启" else "动画关闭")}
            compose.onNodeWithText("动画开启").assertExists()
            shell("settings put global animator_duration_scale 0")
            compose.waitUntil(10000) {compose.onAllNodesWithText("动画关闭").fetchSemanticsNodes().isNotEmpty()}
            shell("settings put global animator_duration_scale 1")
            compose.waitUntil(10000) {compose.onAllNodesWithText("动画开启").fetchSemanticsNodes().isNotEmpty()}
            compose.runOnIdle {preference.value=false}
            compose.onNodeWithText("动画关闭").assertExists()
        } finally {shell("settings put global animator_duration_scale ${if(before=="null") "1" else before}")}
    }
}
