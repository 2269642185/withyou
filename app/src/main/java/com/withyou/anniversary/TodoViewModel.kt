package com.withyou.anniversary

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.LocalDate

data class TodoState(val ready:Boolean=false,val session:TodoSession?=null,val book:TodoBook?=null,val busy:Boolean=false,
    val photoVersion:Long=0,val message:String="",val syncLabel:String="尚未登录",val invitationCode:String="",val failed:Boolean=false)

class TodoViewModel(app:Application):AndroidViewModel(app) {
    private val store=TodoStore(app)
    val sharedPhotos=SharedPhotoStore(app)
    private val sessions=TodoSessionStore(app)
    private val prefs=app.getSharedPreferences("todo_device",Context.MODE_PRIVATE)
    private val device=prefs.getString("id",null) ?: TodoCloud.newDeviceId().also {prefs.edit().putString("id",it).apply()}
    private val cloud=TodoCloud(device)
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(TodoState())
    val state=mutable.asStateFlow()
    private var nextAttempt=0L
    private var failures=0
    private var networkValidated=false
    private var networkRetries=0
    private val connectivity=app.getSystemService(android.net.ConnectivityManager::class.java)
    private val networkCallback=object:android.net.ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network:android.net.Network,cap:android.net.NetworkCapabilities) {
            if(cap.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) viewModelScope.launch {
                if(!networkValidated) {
                    networkValidated=true;networkRetries=2;nextAttempt=0
                    delay(1200)
                    if(!mutable.value.busy) sync(false)
                }
            }
        }
        override fun onLost(network:android.net.Network) {viewModelScope.launch {networkValidated=false;networkRetries=0}}
    }
    override fun onCleared() {runCatching {connectivity.unregisterNetworkCallback(networkCallback)};super.onCleared()}
    private var syncJob:Job?=null
    init {connectivity.registerDefaultNetworkCallback(networkCallback);viewModelScope.launch {withContext(Dispatchers.IO) {
        try {
            val session=sessions.read()
            val book=session?.let {store.read(it.uid)}
            mutable.value=TodoState(ready=true,session=session,book=book,syncLabel=if(session==null) "尚未登录" else "等待同步")
        } catch(_:Exception) {mutable.value=TodoState(ready=true,message="本机打卡数据或登录凭据暂时无法读取，请保留应用并检查备份",failed=true)}
    }}}
    private fun act(block:suspend ()->Unit) {viewModelScope.launch {mutex.withLock {
        mutable.value=mutable.value.copy(busy=true,message="")
        try {block()} catch(e:CancellationException) {throw e}
        catch(e:Exception) {mutable.value=mutable.value.copy(message=if(e is TodoCloudError || e is IllegalArgumentException) e.message.orEmpty() else "操作未完成，请检查网络后重试；已保存的记录仍在本机")}
        finally {mutable.value=mutable.value.copy(busy=false)}
    }}}
    private suspend fun save(book:TodoBook) {
        withContext(Dispatchers.IO) {store.write(book)}
        if(failures==0 && (book.pending.isNotEmpty() || book.shareJobs.isNotEmpty())) nextAttempt=0
        mutable.value=mutable.value.copy(book=book,syncLabel=if(book.pending.isEmpty() && book.shareJobs.isEmpty()) "等待同步" else "等待同步 · ${book.pending.size+book.shareJobs.size} 项")
    }
    fun login(username:String,password:String) = act {
        require(username.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{1,47}"))) {"用户名使用 2～48 位字母、数字、点、下划线或短横线"}
        require(password.isNotBlank()) {"请输入密码"}
        val session=cloud.login(username.trim(),password)
        val book=withContext(Dispatchers.IO) {store.read(session.uid).also {sessions.save(session)}}
        mutable.value=TodoState(ready=true,session=session,book=book,busy=true,syncLabel="等待同步")
        failures=0;nextAttempt=0
    }
    fun register(username:String,password:String,nickname:String,code:String)=act {
        require(nickname.trim().length in 2..32) {"昵称需要 2～32 个字"}
        require(username.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{1,47}"))) {"用户名使用 2～48 位字母、数字、点、下划线或短横线"}
        require(password.length in 8..32 && password.first().isLetterOrDigit() && listOf(password.any(Char::isUpperCase),password.any(Char::isLowerCase),password.any(Char::isDigit),password.any {it in "()!@#$%^&*|?><_-"}).count {it}>=3) {"密码为 8～32 位，以字母或数字开头，包含大小写字母、数字、符号中的至少三类"}
        cloud.register(username.trim(),password,nickname.trim(),code.trim())
        mutable.value=mutable.value.copy(message="注册成功，请登录")
    }
    fun logout()=act {
        val old=mutable.value.session
        withContext(Dispatchers.IO) {sessions.save(null);old?.let {sharedPhotos.clear(it.uid)}}
        mutable.value=TodoState(ready=true,busy=true,message="已退出。本机个人记录和待同步操作按账号保留。")
        if(old!=null) runCatching {cloud.signOut(old)}
    }
    fun saveTask(task:TodoTask,onSaved:()->Unit)=act {
        val book=mutable.value.book ?: error("尚未登录")
        require(task.owner==book.uid) {"只能编辑自己的事项"}
        TodoRules.validate(task)
        require(book.tasks.count {!it.deleted}<500 || book.tasks.any {it.id==task.id}) {"最多保存 500 个未删除事项"}
        val old=book.tasks.find {it.id==task.id}
        require(old==null || !old.deleted && old.kind==task.kind) {"已删除事项不能恢复，事项类型创建后不可更改"}
        val body=task.json().put("type","task").put("expected",old?.revision ?: 0)
        val local=task.copy(revision=(old?.revision ?: 0)+1)
        save(book.copy(tasks=book.tasks.filterNot {it.id==task.id}+local,pending=book.pending+TodoOperation(taskId=task.id,body=body.toString())))
        onSaved()
    }
    fun toggle(task:TodoTask,date:LocalDate)=act {
        val book=mutable.value.book ?: return@act
        require(date==TodoRules.today() && TodoRules.canCheck(task,book.uid,book.pairId,date)) {"今天不需要打卡，或配对关系已变化"}
        val day=TodoRules.checkDay(task,date)
        val old=book.checks.find {it.taskId==task.id && it.user==book.uid && it.day==day}
        val value=TodoCheck(task.id,book.uid,day,!(old?.done ?: false),(old?.revision ?: 0)+1,date)
        val body=JSONObject().put("type","check").put("id",task.id).put("expected",old?.revision ?: 0).put("pair_id",task.pairId).put("day",day.toString()).put("completed_on",date.toString()).put("done",value.done)
        save(book.copy(checks=book.checks.filterNot {it.taskId==task.id && it.user==book.uid && it.day==day}+value,pending=book.pending+TodoOperation(taskId=task.id,body=body.toString())))
    }
    fun acknowledge()=act {mutable.value.book?.let {save(it.copy(conflicts=emptyList()))}}
    fun pair(action:String,value:String="")=act {
        val session=freshSession()
        val result=cloud.pair(session,action,value)
        if(action=="unlink") {
            val book=mutable.value.book!!
            val own=TodoRules.prune(book,"",emptySet())
            val stopped=own.pending.filter {JSONObject(it.body).str("pair_id").isNotBlank()}.map {op->
                val draft=JSONObject(op.body)
                "解除配对，已停止共享操作："+draft.str("title").ifBlank {book.tasks.find {it.id==op.taskId}?.title ?: "打卡"}+"\n"+draft.str("notes")
            }
            sharedPhotos.clear(book.uid)
            save(own.copy(memories=own.memories.filter {it.owner==book.uid},shareJobs=emptyList(),pairId="",partnerId="",partnerName="伙伴",invites=emptyList(),pending=own.pending.filter {JSONObject(it.body).str("pair_id").isBlank()},taskCursor=0,checkCursor=0,conflicts=(own.conflicts+stopped).takeLast(20)))
        }
        mutable.value=mutable.value.copy(invitationCode=result.str("code"),message=when(action) {"create"->"邀请码 10 分钟内有效，请私下发送给伙伴";"join"->"申请已发送，等待对方确认";"confirm"->"已成为彼此的伙伴";"unlink"->"已解除配对";else->"申请已取消"})
        nextAttempt=0
    }
    private suspend fun freshSession():TodoSession {
        var session=mutable.value.session ?: throw IllegalArgumentException("请先登录")
        if(session.expiresAt<System.currentTimeMillis()+60000) {
            session=cloud.refresh(session)
            withContext(Dispatchers.IO) {sessions.save(session)}
            mutable.value=mutable.value.copy(session=session)
        }
        return session
    }
    fun sync(manual:Boolean=false) {
        if(syncJob?.isActive==true || mutable.value.busy || mutable.value.session==null || mutable.value.failed || !manual && System.currentTimeMillis()<nextAttempt) return
        syncJob=viewModelScope.launch {mutex.withLock {
            mutable.value=mutable.value.copy(busy=true,syncLabel="正在同步…")
            try {
                val session=freshSession()
                var book=mutable.value.book!!
                // Check membership first. Never send an old pair's queue before checking revocation.
                val snapshot=cloud.snapshot(session,book)
                val pair=snapshot.str("pair_id")
                val visible=snapshot.getJSONArray("visible_ids").let {a->(0 until a.length()).map {a.getString(it)}.toSet()}
                val safePending=book.pending.filter {op ->
                    val body=JSONObject(op.body);val opPair=body.str("pair_id")
                    (opPair.isBlank() || opPair==pair && pair.isNotBlank()) && (body.str("type")=="task" || body.str("day")=="1970-01-01" || body.str("day")==snapshot.str("server_day"))
                }
                val dropped=book.pending.size-safePending.size
                val stoppedDrafts=book.pending.filterNot {it in safePending}.map {op ->
                    val draft=JSONObject(op.body)
                    "旧配对或过期日期操作已停止："+(draft.str("title").ifBlank {book.tasks.find {it.id==op.taskId}?.title ?: "打卡"})+"\n"+draft.str("notes")
                }
                val expiredShares=book.shareJobs.filter {it.active && it.pairId!=pair}
                if(expiredShares.isNotEmpty()) book=book.copy(shareJobs=book.shareJobs-expiredShares.toSet(),conflicts=(book.conflicts+expiredShares.map {"原配对的纪念日共享已停止："+memoryFromPayload(it.payload).title}).takeLast(20))
                book=book.copy(memories=book.memories.filter {it.owner==book.uid || it.pairId==pair && pair==book.pairId && pair.isNotBlank()})
                sharedPhotos.retain(book.uid,book.memories.filter {it.active}.map {it.photoPath}.filter {it.isNotBlank()}.toSet())
                book=TodoRules.prune(book,pair,visible).copy(pairId=pair,partnerId=snapshot.str("partner_id"),partnerName=snapshot.str("partner_name","伙伴"),pending=safePending,
                    conflicts=(book.conflicts+stoppedDrafts).takeLast(20))
                save(book)
                // Failed/conflicting operations retain a human-readable local draft, not a destructive retry loop.
                val conflicts=book.conflicts.toMutableList()
                var failedTasks=emptySet<String>()
                for(op in safePending) {
                    if(op.taskId in failedTasks) {book=book.copy(pending=book.pending.filterNot {it.id==op.id});continue}
                    try {
                        val result=cloud.apply(session,op)
                        if(result.optBoolean("conflict")) {
                            val draft=JSONObject(op.body)
                            conflicts.add(result.str("message","其他设备已修改")+draft.str("title").takeIf {it.isNotBlank()}?.let {"；本机草稿：$it\n${draft.str("notes")}"}.orEmpty())
                            failedTasks=failedTasks+op.taskId
                        }
                    } catch(e:TodoCloudError) {
                        if(e.status !in listOf(400,403) || !(e.reason.contains("PAIR_CHANGED") || e.reason.contains("DAY_CHANGED") || e.reason.contains("FORBIDDEN"))) throw e
                        conflicts.add(e.message.orEmpty());failedTasks=failedTasks+op.taskId
                    }
                    book=book.copy(pending=book.pending.filterNot {it.id==op.id},conflicts=conflicts.takeLast(20))
                    save(book)
                }
                // Force full data refresh after optimistic local writes, since dropped drafts may share server revisions.
                val refreshed=if(safePending.isEmpty() && dropped==0) snapshot else cloud.snapshot(session,book.copy(taskCursor=0,checkCursor=0))
                val remoteTasks=refreshed.getJSONArray("tasks").mapObjects(::taskFromJson)
                val remoteChecks=refreshed.getJSONArray("checks").mapObjects(::checkFromJson)
                val allIds=refreshed.getJSONArray("visible_ids").let {a->(0 until a.length()).map {a.getString(it)}.toSet()}
                if(refreshed.str("pair_id")!=book.pairId) book=TodoRules.prune(book,refreshed.str("pair_id"),allIds)
                val merged=book.copy(tasks=(book.tasks.filter {it.id in allIds && it.id !in remoteTasks.map(TodoTask::id).toSet()}+remoteTasks),checks=(book.checks.filter {old->old.taskId in allIds && remoteChecks.none {it.taskId==old.taskId && it.user==old.user && it.day==old.day}}+remoteChecks),
                    pairId=refreshed.str("pair_id"),partnerId=refreshed.str("partner_id"),partnerName=refreshed.str("partner_name","伙伴"),invites=refreshed.getJSONArray("invites").mapObjects(::inviteFromJson),taskCursor=refreshed.getLong("task_cursor"),checkCursor=refreshed.getLong("check_cursor"),syncedAt=System.currentTimeMillis(),conflicts=conflicts.takeLast(20))
                save(merged)
                syncMemories(session)
                failures=0;networkRetries=0;nextAttempt=System.currentTimeMillis()+30000
                mutable.value=mutable.value.copy(syncLabel="已同步 · 上海时间",message=if(manual) "同步完成" else "")
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                failures=(failures+1).coerceAtMost(5);nextAttempt=System.currentTimeMillis()+(30000L shl failures).coerceAtMost(600000)
                mutable.value=mutable.value.copy(syncLabel="同步失败 · 本机记录已保留",message=when {e is TodoCloudError || e is IllegalArgumentException->e.message.orEmpty();manual->"网络暂时不可用，已保存到本机，恢复连接后会继续同步";else->mutable.value.message})
            } finally {mutable.value=mutable.value.copy(busy=false);if(networkRetries>0 && failures>0) {nextAttempt=System.currentTimeMillis()+4000;networkRetries--}}
        }}
    }
    private fun outbox(uid:String)=java.io.File(getApplication<Application>().filesDir,"shared-upload/"+sha256(uid.toByteArray())).apply {mkdirs()}
    fun shareMemory(record:MemoryDay,active:Boolean=true,onSaved:()->Unit={},expectedPair:String?=null)=act {
        require(expectedPair==null || mutable.value.book?.pairId==expectedPair) {"伙伴已变化，请重新确认共享对象"}
        enqueueMemory(record,active,onSaved)
    }
    private suspend fun enqueueMemory(record:MemoryDay,active:Boolean,onSaved:()->Unit) {
        val book=mutable.value.book ?: throw IllegalArgumentException("请先在打卡页登录并配对")
        if(active) require(book.pairId.isNotBlank()) {"请先在打卡页邀请伙伴"}
        val old=book.memories.find {it.id==record.id && it.owner==book.uid}
        val previous=book.shareJobs.find {it.id==record.id}
        if(!active && old==null && previous==null) {onSaved();return}
        val photo=if(active) record.photo?.let {name->
            require(name.matches(Regex("[a-f0-9-]{36}\\.jpg")))
            withContext(Dispatchers.IO) {
                val bytes=sharedPhotos.prepare(java.io.File(getApplication<Application>().filesDir,"photos/$name"))
                val file=java.io.File(outbox(book.uid),java.util.UUID.randomUUID().toString()+".jpg")
                writeAtomic(android.util.AtomicFile(file),bytes);file.name
            }
        } else null
        val job=ShareJob(id=record.id,pairId=if(active) book.pairId else old?.pairId ?: previous!!.pairId,payload=memoryPayload(record),photo=photo,expected=previous?.expected ?: old?.revision ?: 0,active=active)
        save(book.copy(shareJobs=book.shareJobs.filterNot {it.id==record.id}+job))
        mutable.value=mutable.value.copy(message=if(active) "共享已保存到本机，等待同步" else "已停止本机共享，云端撤回待同步")
        nextAttempt=0
        onSaved()
    }
    fun sharedEdit(record:MemoryDay) {
        val book=mutable.value.book ?: return
        val old=book.memories.find {it.id==record.id && it.owner==book.uid && it.active && it.pairId==book.pairId}
        val job=book.shareJobs.find {it.id==record.id && it.active && it.pairId==book.pairId}
        if(old!=null || job!=null) act {
            require(mutable.value.book?.uid==book.uid && mutable.value.book?.pairId==book.pairId) {"配对已变化，未向新的伙伴共享这份纪念"}
            enqueueMemory(record,true,{})
        }
    }
    fun sharedDelete(record:MemoryDay,done:()->Unit)=act {
        val owners=withContext(Dispatchers.IO) {store.sharedOwners(record.id)}
        require(owners.all {it==mutable.value.session?.uid}) {"这份纪念曾由其他登录账号共享。请先登录原账号并停止共享，再删除本机记录。"}
        if(mutable.value.book==null) done() else enqueueMemory(record,false,done)
    }
    private suspend fun syncMemories(session:TodoSession) {
        var book=mutable.value.book ?: return
        var remote=cloud.memorySnapshot(session)
        // Persist revocation before downloads, even when a later upload or request fails.
        sharedPhotos.retain(book.uid,remote.filter {it.active}.map {it.photoPath}.filter {it.isNotBlank()}.toSet())
        book=book.copy(memories=remote);save(book)
        for(job in book.shareJobs) {
            if(job.active && job.pairId!=book.pairId) continue
            val path=if(job.photo!=null && job.active) {
                val bytes=withContext(Dispatchers.IO) {java.io.File(outbox(book.uid),job.photo).readBytes()}
                val path="${book.uid}/${job.id}/${sha256(bytes)}.jpg"
                cloud.photo(session,path,bytes);path
            } else ""
            val oldPath=remote.find {it.owner==book.uid && it.id==job.id}?.photoPath.orEmpty()
            val applying=if(!job.active) job.copy(expected=remote.find {it.owner==book.uid && it.id==job.id}?.revision ?: job.expected) else job
            val result=cloud.memoryApply(session,applying,path)
            require(!result.optBoolean("conflict")) {"纪念日共享版本有变化。本机修改已保留，请先停止共享，再重新分享。"}
            book=book.copy(shareJobs=book.shareJobs.filterNot {it.op==job.op});save(book)
            remote=cloud.memorySnapshot(session)
            book=book.copy(memories=remote);save(book)
            if(oldPath.isNotBlank() && oldPath!=path) runCatching {cloud.photo(session,oldPath,delete=true)}
        }
        val valid=remote.filter {it.active && it.photoPath.isNotBlank()}
        sharedPhotos.retain(book.uid,valid.map {it.photoPath}.toSet())
        for(item in valid) if(!sharedPhotos.file(book.uid,item.photoPath).exists()) {
            val bytes=cloud.photo(session,item.photoPath)
            withContext(Dispatchers.IO) {sharedPhotos.store(book.uid,item.photoPath,bytes)}
        }
        withContext(Dispatchers.IO) {outbox(book.uid).listFiles()?.filter {file->book.shareJobs.none {it.photo==file.name}}?.forEach {it.delete()}}
        mutable.value=mutable.value.copy(photoVersion=mutable.value.photoVersion+1)
    }
    fun export(uri:Uri)=act {
        val book=mutable.value.book ?: error("请先登录")
        withContext(Dispatchers.IO) {getApplication<Application>().contentResolver.openOutputStream(uri,"wt").use {out->requireNotNull(out).write(book.json().toString(2).toByteArray())}}
        mutable.value=mutable.value.copy(message="打卡数据已导出，不含密码和登录令牌")
    }
}
