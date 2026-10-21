package com.withyou.anniversary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Refresh tokens rotate. Every Activity/ViewModel in this process must share this lock. */
internal class SessionRefresher(private val store:TodoSessionStore,
    private val refresh:suspend (TodoSession)->TodoSession,
    private val now:()->Long={System.currentTimeMillis()}) {
    companion object {private val mutex=Mutex()}
    suspend fun fresh(current:TodoSession,force:Boolean=false):TodoSession=mutex.withLock {
        val saved=withContext(Dispatchers.IO) {store.read()}
        if(saved==null || saved.uid!=current.uid) throw TodoCloudError(401,"SESSION_CHANGED",SyncStage.SESSION)
        // A different window may already have rotated and persisted this token.
        if(saved.expiresAt>=now()+60000 && (!force || saved.access!=current.access)) return@withLock saved
        val next=refresh(saved)
        require(next.uid==saved.uid) {"登录身份变化，请重新登录"}
        // Logout or login in another window must win over an in-flight refresh.
        if(!withContext(Dispatchers.IO) {store.replaceIfCurrent(saved,next)})
            throw TodoCloudError(401,"SESSION_CHANGED",SyncStage.SESSION)
        next
    }
}
