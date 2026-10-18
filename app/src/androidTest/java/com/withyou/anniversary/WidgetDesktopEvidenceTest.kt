package com.withyou.anniversary

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in launcher operation, for a disposable emulator only; excluded from the normal suite. */
@RunWith(AndroidJUnit4::class)
class WidgetDesktopEvidenceTest {
    @Test fun pinLargeOnRealLauncher() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("desktopCapture")=="true")
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val manager=AppWidgetManager.getInstance(context)
        val provider=ComponentName(context,LargeAnniversaryWidget::class.java)
        val before=manager.getAppWidgetIds(provider).toSet()
        assertTrue(manager.isRequestPinAppWidgetSupported)
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            Thread.sleep(600)
            scenario.onActivity {assertTrue(manager.requestPinAppWidget(provider,Bundle(),null))}
            var added:Int?=null
            val deadline=System.currentTimeMillis()+15000
            while(System.currentTimeMillis()<deadline) {
                val root=inst.uiAutomation.rootInActiveWindow
                val candidates=listOf("Add","Add to Home screen","Add automatically")
                for(text in candidates) {
                    root?.findAccessibilityNodeInfosByText(text)?.filter {it.text?.toString().equals(text,true)}?.forEach {node ->
                        var target:AccessibilityNodeInfo?=node
                        repeat(4) {if(target?.isClickable!=true) target=target?.parent}
                        target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
                added=manager.getAppWidgetIds(provider).firstOrNull {it !in before}
                if(added!=null) break
                Thread.sleep(300)
            }
            assertNotNull("Launcher must bind the requested large widget",added)
            val record=Repository(context).read().ordered().first()
            WidgetStore(context).save(added!!,WidgetChoice(record.id))
            AnniversaryWidget.refresh(context)
            inst.sendStatus(0,Bundle().apply {putString("stream","Pinned desktop large widget id=$added\n")})
        }
    }
}
