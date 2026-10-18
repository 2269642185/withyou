package com.withyou.anniversary

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.net.*
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class UpdateTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val bytes="test package".toByteArray()
    private fun update(data: ByteArray=bytes)=AppUpdate(7,"1.4.1","测试更新","https://github.com/test/download/releases/download/v1.4.1/withyou.apk",data.size.toLong(),MessageDigest.getInstance("SHA-256").digest(data).joinToString("") {"%02x".format(it)},26)
    private fun connection(data: ByteArray): HttpURLConnection=object:HttpURLConnection(URL("https://github.com/test")) {
        override fun connect() {} ; override fun disconnect() {}; override fun usingProxy()=false
        override fun getInputStream(): InputStream=ByteArrayInputStream(data)
    }
    @Test fun downloadChecksIntegrityAndCanRetryAfterFailure()=runBlocking {
        val file=File(context.cacheDir,"update-network-test.apk")
        try {
            val progress=mutableListOf<Int>()
            UpdateNetwork.download(update(),file,{connection(bytes)}) {progress.add(it)}
            assertArrayEquals(bytes,file.readBytes()); assertEquals(100,progress.last())
            try {UpdateNetwork.download(update(),file,{connection("damaged".toByteArray())}) {}; fail("corruption accepted")} catch(_:IllegalArgumentException) {}
            assertFalse(file.exists())
            try {UpdateNetwork.download(update(),file,{throw SocketTimeoutException()}) {}; fail("timeout accepted")} catch(_:SocketTimeoutException) {}
            UpdateNetwork.download(update(),file,{connection(bytes)}) {}
            assertArrayEquals(bytes,file.readBytes())
        } finally {file.delete()}
    }
    @Test fun cancellationDeletesPartialPackage()=runBlocking {
        val file=File(context.cacheDir,"update-cancel-test.apk")
        val large=ByteArray(500000)
        val job=launch {UpdateNetwork.download(update(large),file,{connection(large)}) { if(it>0) cancel() }}
        job.join(); assertTrue(job.isCancelled); assertFalse(file.exists())
    }
    @Test fun manifestRejectsMalformedDataAndOffline()=runBlocking {
        try {UpdateNetwork.manifest("test/download") {throw UnknownHostException()}; fail("offline accepted")} catch(_:UnknownHostException) {}
        listOf("{}","x".repeat(70000)).forEach { text ->
            try {UpdateNetwork.manifest("test/download") {connection(text.toByteArray())}; fail("bad manifest accepted")} catch(_:Exception) {}
        }
    }
    @Test fun installerOnlySharesUpdateDirectoryAndRejectsInvalidPackages() {
        val file=File(context.filesDir,"updates/test-invalid.apk").apply {parentFile!!.mkdirs();writeBytes(bytes)}
        try {
            val intent=UpdateInstaller.intent(context,file)
            assertEquals("content",intent.data!!.scheme)
            assertEquals(context.packageName+".updates",intent.data!!.authority)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            try {UpdateInstaller.verify(context,file,update());fail("invalid apk accepted")} catch(_:Exception) {}
            try {UpdateInstaller.intent(context,File(context.filesDir,"memories.json"));fail("private data exposed")} catch(_:IllegalArgumentException) {}
        } finally {file.delete()}
    }
    @Test fun updatesPageShowsUnconfiguredStateAndSavesRepository() {
        val prefs=context.getSharedPreferences("app_updates",0)
        prefs.edit().clear().putString("repository","").commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10000) {compose.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText("设置").performClick()
                compose.onNodeWithText("版本与更新").performClick()
                compose.onNodeWithText("尚未连接更新仓库").assertExists()
                compose.onNodeWithText("检查更新").assertIsNotEnabled()
                compose.onNode(hasSetTextAction() and hasText("GitHub 仓库地址")).performScrollTo().performTextInput("https://github.com/test/download")
                androidx.test.espresso.Espresso.closeSoftKeyboard()
                compose.waitForIdle()
                compose.onNodeWithText("保存更新地址").performScrollTo().performClick()
                compose.waitUntil(5000) {prefs.getString("repository","")=="test/download"}
                it.recreate()
                compose.waitUntil(10000) {compose.onAllNodesWithText("版本与更新").fetchSemanticsNodes().isNotEmpty()}
                assertEquals("test/download",prefs.getString("repository",""))
            }
        } finally {prefs.edit().clear().commit()}
    }
}
