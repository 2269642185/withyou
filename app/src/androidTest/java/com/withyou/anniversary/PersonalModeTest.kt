package com.withyou.anniversary

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class PersonalModeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private fun waitText(text:String) {compose.waitUntil(15000) {compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun shell(cmd:String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use {it.readBytes()}}
    @Test fun guestCanCheckWithoutLoginAndRestartKeepsDataAndPalette() {
        val sessions=TodoSessionStore(context);val oldSession=sessions.read();val store=TodoStore(context);val oldBook=store.read(LOCAL_TODO_UID)
        val repo=Repository(context);val oldData=repo.read()
        try {
            sessions.save(null);store.write(TodoBook(LOCAL_TODO_UID))
            runBlocking {repo.save(AppData())}
            ActivityScenario.launch(MainActivity::class.java).use {scenario ->
                waitText("与你");compose.onNodeWithText("我们的共享").assertDoesNotExist()
                compose.onNodeWithText("打卡").performClick();waitText("把小事，慢慢做好")
                compose.onNodeWithText("邮箱或原用户名").assertDoesNotExist()
                compose.onNodeWithContentDescription("账号与伙伴").assertDoesNotExist()
                compose.onNodeWithText("新增事项").performClick()
                compose.onNode(hasSetTextAction() and hasText("事项标题")).performTextInput("照顾今天的自己")
                scenario.onActivity {a -> androidx.core.view.WindowCompat.getInsetsController(a.window,a.window.decorView).hide(androidx.core.view.WindowInsetsCompat.Type.ime())}
                compose.waitUntil(5000) {var hidden=false;scenario.onActivity {a->hidden=androidx.core.view.ViewCompat.getRootWindowInsets(a.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())!=true};hidden}
                compose.onNodeWithText("隐私设置").assertDoesNotExist();compose.onNodeWithText("共同事项").assertDoesNotExist()
                compose.onNodeWithText("保存事项").performScrollTo().performClick();waitText("照顾今天的自己")
                compose.onNodeWithText("完成打卡").performClick()
                compose.waitUntil(10000) {store.read(LOCAL_TODO_UID).checks.any {it.done}}
                assertTrue(store.read(LOCAL_TODO_UID).pending.isEmpty())
                compose.onNodeWithText("设置",substring=false).performClick();waitText("留给日常的偏爱")
                compose.onNodeWithTag("palette_MINT").performScrollTo().performClick()
                compose.waitUntil(10000) {repo.read().preferences.palette==AppPalette.MINT}
                scenario.recreate();waitText("留给日常的偏爱")
                compose.onNodeWithTag("palette_MINT").performScrollTo().assertIsSelected()
                compose.onNodeWithText("打卡").performClick();waitText("照顾今天的自己")
                compose.onNodeWithText("自己",substring=false).assertExists() // Progress row only, no duplicate owner chip.
                compose.onNodeWithText("伙伴",substring=true).assertDoesNotExist()
                compose.onNodeWithText("我们的共享").assertDoesNotExist()
                shell("screencap -p /sdcard/Download/withyou-v18-personal-mint.png")
            }
        } finally {sessions.save(oldSession);store.write(oldBook);runBlocking {repo.save(oldData)}}
    }
    @Test fun cachedConfirmedPairShowsSharedElementsEvenWhenOffline() {
        val sessions=TodoSessionStore(context);val old=sessions.read();val uid="v18-pair-"+UUID.randomUUID()
        val task=TodoTask(owner=uid,title="一起散步",shared=true,pairId="confirmed-pair")
        shell("svc wifi disable");shell("svc data disable")
        try {
            sessions.save(TodoSession(uid,"小鱼","invalid-test-token","invalid-test-refresh",System.currentTimeMillis()+7200000))
            TodoStore(context).write(TodoBook(uid,pairId="confirmed-pair",partnerId="peer",partnerName="小杏",tasks=listOf(task)))
            ActivityScenario.launch(MainActivity::class.java).use {scenario ->
                waitText("我们的共享");compose.onNodeWithText("打卡").performClick();waitText("把小事，一起坚持")
                compose.onNodeWithTag("progress_name_partner").assertTextEquals("小杏")
                scenario.recreate();waitText("把小事，一起坚持")
                compose.onNodeWithTag("progress_name_partner").assertExists()
                assertTrue(context.getSharedPreferences("launcher_pair_state",0).getBoolean("paired",false))
                shell("screencap -p /sdcard/Download/withyou-v18-paired.png")
            }
        } finally {sessions.save(old);LauncherIcons(context).logout();shell("svc wifi enable");shell("svc data enable")}
    }
    @Test fun aliasesHaveExactlyOneLaunchEntryAndRestoreConfirmedState() {
        val manager=LauncherIcons(context)
        fun entry():String {
            val entries=context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),0)
            assertEquals(1,entries.size)
            assertTrue(context.packageManager.getActivityInfo(android.content.ComponentName(context,MainActivity::class.java),0).enabled)
            return entries.single().activityInfo.name
        }
        try {
            manager.confirm("icon-test",false);assertTrue(entry().endsWith("PersonalLauncher"))
            manager.confirm("icon-test",true);assertTrue(entry().endsWith("TogetherLauncher"))
            manager.restore();assertTrue(entry().endsWith("TogetherLauncher"))
            manager.forAccount("different-unpaired-test");assertTrue(entry().endsWith("PersonalLauncher"))
            manager.forAccount("icon-test");assertTrue(entry().endsWith("TogetherLauncher"))
            manager.confirm("icon-test",false);assertTrue(entry().endsWith("PersonalLauncher"))
            val deep=context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("withyou://register?invite="+"ab".repeat(16))).setPackage(context.packageName),PackageManager.MATCH_DEFAULT_ONLY)
            assertNotNull(deep)
        } finally {manager.logout()}
    }
    @Test fun nativeAdaptiveAndMonochromeIconsRenderWithSafeMargins() {
        val image=android.graphics.Bitmap.createBitmap(720,600,android.graphics.Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(image);canvas.drawColor(android.graphics.Color.rgb(245,242,237))
        listOf(R.mipmap.ic_personal,R.mipmap.ic_together).forEachIndexed {i,id ->
            val drawable=context.getDrawable(id)!!
            assertTrue(drawable is android.graphics.drawable.AdaptiveIconDrawable)
            drawable.setBounds(32+i*350,30,332+i*350,330);drawable.draw(canvas)
            if(android.os.Build.VERSION.SDK_INT>=33) assertNotNull((drawable as android.graphics.drawable.AdaptiveIconDrawable).monochrome)
            drawable.setBounds(140+i*350,390,212+i*350,462);drawable.draw(canvas)
        }
        val file=java.io.File(context.getExternalFilesDir(null),"withyou-v18-native-icons.png")
        file.outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        shell("cp ${file.absolutePath} /sdcard/Download/withyou-v18-native-icons.png")
        image.recycle()
    }
    @Test fun launcherSwitchWaitsForBackgroundAndDoesNotClosePairingPage() {
        val sessions=TodoSessionStore(context);val previous=sessions.read();val uid="v18-alias-"+UUID.randomUUID()
        val manager=LauncherIcons(context)
        shell("svc wifi disable");shell("svc data disable")
        try {
            sessions.save(TodoSession(uid,"个人验证","invalid-test-token","invalid-test-refresh",System.currentTimeMillis()+7200000))
            TodoStore(context).write(TodoBook(uid));LauncherIcons.activityVisible=false;manager.confirm(uid,false)
            val intent=Intent().setComponent(android.content.ComponentName(context.packageName,"com.withyou.anniversary.PersonalLauncher"))
            ActivityScenario.launch<MainActivity>(intent).use {scenario ->
                waitText("与你");manager.confirm(uid,true)
                scenario.onActivity {assertFalse(it.isFinishing)}
                val before=context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),0)
                assertTrue(before.single().activityInfo.name.endsWith("PersonalLauncher"))
                shell("input keyevent KEYCODE_HOME")
                compose.waitUntil(10000) {!LauncherIcons.activityVisible}
                compose.waitUntil(10000) {
                    val entries=context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),0)
                    entries.size==1 && entries.single().activityInfo.name.endsWith("TogetherLauncher")
                }
                val after=context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),0)
                assertEquals(1,after.size);assertTrue(after.single().activityInfo.name.endsWith("TogetherLauncher"))
            }
            ActivityScenario.launch<MainActivity>(Intent().setComponent(android.content.ComponentName(context.packageName,"com.withyou.anniversary.TogetherLauncher"))).use {
                waitText("与你");compose.onNodeWithText("设置",substring=false).assertExists()
            }
        } finally {sessions.save(previous);LauncherIcons.activityVisible=false;manager.logout();shell("svc wifi enable");shell("svc data enable")}
    }
}
