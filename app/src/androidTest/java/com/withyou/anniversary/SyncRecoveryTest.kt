package com.withyou.anniversary

import android.app.Application
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SyncRecoveryTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private fun isolatedStore():TodoSessionStore {
        val directory=File(context.cacheDir,"refresh-${UUID.randomUUID()}").apply {mkdirs()}
        return TodoSessionStore(object:ContextWrapper(context) {override fun getFilesDir()=directory})
    }
    private fun session()=TodoSession("test-user","朋友","test-access-0","test-refresh-0",System.currentTimeMillis()-1)
    @Test fun concurrentWindowsReuseRotatedTokenInsteadOfReusingOldOne()=runBlocking {
        val store=isolatedStore();val old=session();store.save(old);var requests=0
        val server:suspend (TodoSession)->TodoSession={s->
            assertEquals("test-refresh-0",s.refresh);requests++;delay(100)
            s.copy(access="test-access-1",refresh="test-refresh-1",expiresAt=System.currentTimeMillis()+7200000)
        }
        val a=SessionRefresher(store,server);val b=SessionRefresher(store,server)
        val values=awaitAll(async(Dispatchers.Default) {a.fresh(old)},async(Dispatchers.Default) {b.fresh(old)})
        assertEquals(1,requests);assertEquals(values[0],values[1]);assertEquals("test-refresh-1",store.read()!!.refresh)
        assertEquals(values[0],a.fresh(old,force=true));assertEquals(1,requests)
    }
    @Test fun refreshCannotResurrectLogoutOrOverwriteDifferentAccount()=runBlocking {
        val store=isolatedStore();val old=session();store.save(old)
        val entered=CompletableDeferred<Unit>();val resume=CompletableDeferred<Unit>()
        val refresher=SessionRefresher(store,{s->entered.complete(Unit);resume.await();s.copy(refresh="rotated")})
        val job=async(Dispatchers.Default) {runCatching {refresher.fresh(old)}}
        entered.await();store.save(null);resume.complete(Unit)
        assertTrue(job.await().isFailure);assertNull(store.read())
        val other=old.copy(uid="other-account");store.save(other)
        try {refresher.fresh(old);fail("Old window cannot renew another account")} catch(e:TodoCloudError) {assertTrue(e.requiresLogin)}
        assertEquals(other,store.read())
    }
    @Test fun networkFailureKeepsRefreshCredentialAndAllowsLaterRetry()=runBlocking {
        val store=isolatedStore();val old=session();store.save(old);var offline=true
        val refresher=SessionRefresher(store,{s->if(offline) throw TodoCloudError(0,"",SyncStage.SESSION,"TIMEOUT") else s.copy(refresh="new",expiresAt=System.currentTimeMillis()+7200000)})
        try {refresher.fresh(old);fail()} catch(e:TodoCloudError) {assertFalse(e.requiresLogin)}
        assertEquals(old,store.read());offline=false;assertEquals("new",refresher.fresh(old).refresh)
    }
    private fun shell(value:String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(value)).use {it.readBytes()}}
    private suspend fun exercise(mode:String) {
        require(context.packageName.endsWith(".validation"))
        shell("svc wifi disable");shell("svc data disable")
        val sessions=TodoSessionStore(context);val previous=sessions.read();val uid="sync-${UUID.randomUUID()}"
        val old=session().copy(uid=uid,expiresAt=if(mode=="invalid-grant") 0 else System.currentTimeMillis()+7200000)
        val task=TodoTask(owner=uid,title="待同步记录保持原样",revision=1)
        val op=TodoOperation(taskId=task.id,body=task.json().put("type","task").put("expected",0).toString())
        val book=TodoBook(uid,tasks=listOf(task),pending=listOf(op));val store=TodoStore(context)
        var allow=false;var applied=false;var refreshes=0;var reads=0;val opIds=mutableListOf<String>()
        val transport=TodoTransport {path,body,token,_ -> when(path) {
            "/auth/v1/token" -> {
                refreshes++
                if(mode=="invalid-grant" && !allow) throw TodoCloudError(400,"invalid_grant refresh_token已过期")
                JSONObject().put("sub",uid).put("access_token","fresh-access").put("refresh_token","fresh-refresh").put("expires_in",7200)
            }
            "/auth/v1/signin" -> JSONObject().put("sub",uid).put("access_token","fresh-access").put("refresh_token","fresh-refresh").put("expires_in",7200)
            "/auth/v1/user/me" -> JSONObject().put("sub",uid).put("name","朋友")
            "/v1/rdb/rest/rpc/wy_account_status" -> JSONObject().put("admitted",true).put("nickname","朋友")
            "/v1/rdb/rest/rpc/wy_snapshot" -> {
                reads++
                if(mode=="expired-access" && token!="fresh-access") throw TodoCloudError(401,"JWT expired")
                JSONObject().put("pair_id","").put("partner_id","").put("partner_name","").put("server_day",TodoRules.today().toString())
                    .put("visible_ids",JSONArray(if(applied) listOf(task.id) else emptyList<String>()))
                    .put("tasks",JSONArray(if(applied) listOf(task.json()) else emptyList<JSONObject>())).put("checks",JSONArray()).put("invites",JSONArray())
                    .put("task_cursor",if(applied) 1 else 0).put("check_cursor",0)
            }
            "/v1/rdb/rest/rpc/wy_apply" -> {
                opIds.add(body.getString("op_id"))
                if(mode=="business-400" && !allow) throw TodoCloudError(400,"23514 private-title private-password",code="23514")
                applied=true;JSONObject().put("ok",true)
            }
            "/v1/rdb/rest/rpc/wy_memory_snapshot" -> JSONObject().put("memories",JSONArray())
            else -> error("Unexpected test endpoint")
        }}
        val holder=ViewModelStore();lateinit var model:TodoViewModel
        try {
            sessions.save(old);store.write(book)
            inst.runOnMainSync {model=TodoViewModel(context.applicationContext as Application,TodoCloud("isolated-test",transport=transport));holder.put("test",model)}
            withTimeout(15000) {model.state.first {it.ready}}
            model.sync(true)
            if(mode=="expired-access") {
                withTimeout(15000) {model.state.first {it.syncLabel.startsWith("已同步") && !it.busy}}
                assertEquals(1,refreshes);assertTrue(reads>=2);assertEquals(listOf(op.id),opIds)
            } else {
                withTimeout(15000) {model.state.first {it.syncDetail.isNotBlank() && !it.busy}}
                assertEquals(listOf(op),store.read(uid).pending);assertEquals(task,store.read(uid).tasks.single())
                assertEquals(mode=="invalid-grant",model.state.value.reauthRequired)
                assertFalse(model.state.value.syncDetail.contains("private-password"))
                assertFalse(model.state.value.syncDetail.contains("private-title"))
                if(mode=="invalid-grant") {
                    val count=refreshes;model.sync(true);delay(150);assertEquals(count,refreshes)
                    allow=true;model.login("friend-user","test-password")
                    withTimeout(15000) {model.state.first {it.session?.access=="fresh-access" && !it.busy}}
                } else allow=true
                model.sync(true)
                withTimeout(15000) {model.state.first {it.syncLabel.startsWith("已同步") && !it.busy}}
                assertTrue(opIds.all {it==op.id})
            }
            assertTrue(store.read(uid).pending.isEmpty());assertTrue(applied);assertEquals(uid,model.state.value.session!!.uid)
        } finally {inst.runOnMainSync {holder.clear()};sessions.save(previous);shell("svc wifi enable");shell("svc data enable")}
    }
    @Test fun invalidRefreshStopsRetryAndSameAccountLoginResumesPending()=runBlocking {exercise("invalid-grant")}
    @Test fun business400IsNotLogoutAndSameOperationRetriesWithoutDataLoss()=runBlocking {exercise("business-400")}
    @Test fun access401RefreshesOnceAndReplaysSameIdempotentOperation()=runBlocking {exercise("expired-access")}
    @Test fun nestedErrorsHaveSafeStageCodeAndRequestIdWithoutBodyLeak() {
        val id="12345678-1234-1234-1234-123456789012"
        val error=SyncErrorParser.parse(400,JSONObject().put("error",JSONObject().put("code","PGRST102").put("message","private password jwt secret")).put("requestId",id),SyncStage.WRITE_CHECK)
        val diagnostic=syncDiagnostic(error,2,1)
        assertTrue(diagnostic.contains("提交打卡"));assertTrue(diagnostic.contains("PGRST102"));assertTrue(diagnostic.contains(id))
        assertFalse(diagnostic.contains("jwt"));assertFalse(diagnostic.contains("secret"));assertFalse(error.requiresLogin)
        val expired=SyncErrorParser.parse(400,JSONObject().put("error","invalid_grant").put("error_code",4001),SyncStage.SESSION)
        assertTrue(expired.requiresLogin)
    }
}
