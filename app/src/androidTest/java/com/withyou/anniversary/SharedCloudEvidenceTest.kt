package com.withyou.anniversary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Explicit opt-in only. Uses generated disposable users; never embeds real credentials. */
@RunWith(AndroidJUnit4::class)
class SharedCloudEvidenceTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun nativeOfflinePhotoQueuePeerRestartAndRevocation()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("sharedCloudEvidence")=="true")
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val fixture=File(context.filesDir,"validation/shared-accounts.json")
        val raw=if(fixture.exists()) fixture.readText() else android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("cat /data/local/tmp/withyou-v16-qa.json")).bufferedReader().use {it.readText()}
        val accounts=JSONArray(raw)
        val cloud=TodoCloud("withyou-v16-instrumentation")
        val a=cloud.login(accounts.getJSONObject(0).getString("username"),accounts.getJSONObject(0).getString("password"))
        val b=cloud.login(accounts.getJSONObject(1).getString("username"),accounts.getJSONObject(1).getString("password"))
        val sessions=TodoSessionStore(context);val oldSession=sessions.read()
        val pair=cloud.snapshot(a,TodoBook(a.uid)).getString("pair_id")
        val id=UUID.randomUUID().toString();val photoName=UUID.randomUUID().toString()+".jpg"
        val photo=File(context.filesDir,"photos/$photoName");photo.parentFile!!.mkdirs()
        val bitmap=android.graphics.Bitmap.createBitmap(200,280,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(197,160,126));photo.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,it)};bitmap.recycle()
        val record=MemoryDay(id,"把平凡日子一起收藏 · "+id.take(4),LocalDate.of(2024,2,29),"小杏与阿与你",CountMode.INCLUSIVE,0,photoName,appearance=CardAppearance(background="#F4E6D7",tone=TextTone.CUSTOM,text="#49392D",title="#604A3A",days="#734B3B",auxiliary="#644F43",overlay=.62f),template=CardTemplate.POLAROID)
        fun shell(s:String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(s)).use {it.readBytes()}}
        fun shot(name:String) {compose.waitForIdle();Thread.sleep(450);shell("screencap -p /sdcard/Download/withyou-v16-$name.png")}
        fun enabled(text:String) {compose.waitUntil(40000) {try {compose.onNodeWithText(text).assertIsEnabled();true}catch(_:AssertionError){false}}}
        try {
            sessions.save(a);TodoStore(context).write(TodoBook(a.uid))
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                lateinit var vm:TodoViewModel;scenario.onActivity {vm=ViewModelProvider(it)[TodoViewModel::class.java]}
                compose.waitUntil(45000) {vm.state.value.book?.pairId==pair && !vm.state.value.busy}
                shell("svc wifi disable");shell("svc data disable")
                scenario.onActivity {vm.shareMemory(record)}
                compose.waitUntil(10000) {TodoStore(context).read(a.uid).shareJobs.any {it.id==id}}
                val job=TodoStore(context).read(a.uid).shareJobs.single {it.id==id}
                assertNotNull(job.photo)
                assertTrue(File(context.filesDir,"shared-upload/${sha256(a.uid.toByteArray())}/${job.photo}").exists())
            }
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                lateinit var vm:TodoViewModel;scenario.onActivity {vm=ViewModelProvider(it)[TodoViewModel::class.java]}
                compose.waitUntil(10000) {vm.state.value.ready}
                assertTrue(TodoStore(context).read(a.uid).shareJobs.any {it.id==id})
                shell("svc wifi enable");shell("svc data enable")
                compose.waitUntil(40000) {val cm=context.getSystemService(android.net.ConnectivityManager::class.java);cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true}
                try {compose.waitUntil(40000) {TodoStore(context).read(a.uid).shareJobs.none {it.id==id} && TodoStore(context).read(a.uid).memories.any {it.id==id}}}
                catch(e:Throwable) {throw AssertionError("Native queued upload: ${vm.state.value.syncLabel}; ${vm.state.value.message}; pending=${vm.state.value.book?.shareJobs?.size}",e)}
                assertEquals(record.appearance,TodoStore(context).read(a.uid).memories.single {it.id==id}.record.appearance)
            }
            val remote=cloud.memorySnapshot(b).single {it.id==id};assertEquals(record.template,remote.record.template)
            // Populate both ownership labels and real independent daily check progress.
            for((owner,title,kind) in listOf(Triple(a,"给花浇一点水",TodoKind.HABIT),Triple(b,"读二十分钟的书",TodoKind.HABIT),Triple(a,"晚饭后一起散步",TodoKind.SHARED))) {
                val task=TodoTask(owner=owner.uid,title=title,kind=kind,shared=true,pairId=pair)
                cloud.apply(owner,TodoOperation(taskId=task.id,body=task.json().put("type","task").put("expected",0).toString()))
            }
            sessions.save(b);TodoStore(context).write(TodoBook(b.uid))
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                lateinit var vm:TodoViewModel;scenario.onActivity {vm=ViewModelProvider(it)[TodoViewModel::class.java]}
                compose.waitUntil(50000) {vm.state.value.book?.memories?.any {it.id==id}==true && vm.sharedPhotos.file(b.uid,remote.photoPath).exists() && !vm.state.value.busy}
                compose.onNodeWithText("我们的共享").performScrollTo().performClick()
                compose.onNodeWithTag("shared_memories_list").performScrollToNode(hasText(record.title));compose.onNodeWithText(record.title).performClick()
                compose.onNodeWithText("起始日期：${record.date.label()}\n${record.mode.label}\n共享日子统一按上海日期计算，2 月 29 日在非闰年以 2 月 28 日作为周年日。").assertExists()
                shot("shared-photo-detail")
                shell("svc wifi disable");shell("svc data disable")
                scenario.recreate()
                compose.waitUntil(10000) {compose.onAllNodesWithText(record.title).fetchSemanticsNodes().isNotEmpty()}
                assertTrue(vm.sharedPhotos.file(b.uid,remote.photoPath).exists())
                shot("shared-photo-offline-restart")
                shell("svc wifi enable");shell("svc data enable")
                compose.onNodeWithText("返回共享纪念日").performScrollTo().performClick()
                compose.onNodeWithText("返回纪念日").performScrollTo().performClick()
                compose.onNodeWithText("打卡").performClick()
                enabled("新增事项");shot("unified-progress")
                compose.onNodeWithText("我的").assertDoesNotExist();compose.onNodeWithText("一起完成").assertDoesNotExist()
                // Stop sharing remotely, then refresh; text and private photo cache must both disappear.
                cloud.memoryApply(a,ShareJob(id=id,pairId=pair,payload=remote.payload,expected=remote.revision,active=false),"")
                compose.waitUntil(30000) {!vm.state.value.busy};scenario.onActivity {vm.sync(true)}
                compose.waitUntil(40000) {vm.state.value.book?.memories?.none {it.id==id}==true && !vm.sharedPhotos.file(b.uid,remote.photoPath).exists() && !vm.state.value.busy}
                assertFalse(vm.sharedPhotos.file(b.uid,remote.photoPath).exists())
                // Pair revocation also removes all other peer anniversaries and cached photos.
                cloud.pair(a,"unlink",pair)
                scenario.onActivity {vm.sync(true)}
                compose.waitUntil(40000) {vm.state.value.book?.pairId=="" && !vm.state.value.busy}
                assertTrue(vm.state.value.book!!.memories.all {it.owner==b.uid})
                assertTrue(vm.sharedPhotos.directory(b.uid).listFiles().orEmpty().isEmpty())
            }
        } finally {shell("svc wifi enable");shell("svc data enable");sessions.save(oldSession);photo.delete();cloud.signOut(a);cloud.signOut(b)}
    }
}
