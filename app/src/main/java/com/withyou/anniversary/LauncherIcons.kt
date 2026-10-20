package com.withyou.anniversary

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Only confirmed server snapshots change pair status; transient auth/network failures do not. */
internal class LauncherIcons(private val context:Context) {
    companion object { @Volatile internal var activityVisible=false }
    private val prefs=context.getSharedPreferences("launcher_pair_state",Context.MODE_PRIVATE)
    fun knows(uid:String)=prefs.contains("account_$uid")
    fun restore() { if(!activityVisible) apply(prefs.getBoolean("paired",false)) }
    fun forAccount(uid:String) { confirm(uid,prefs.getBoolean("account_$uid",false)) }
    fun confirm(uid:String,paired:Boolean) {
        if(!prefs.edit().putString("uid",uid).putBoolean("paired",paired).putBoolean("account_$uid",paired).commit()) return
        restore()
    }
    fun logout() {
        prefs.edit().putString("uid","").putBoolean("paired",false).commit()
        restore()
    }
    internal fun apply(paired:Boolean) {
        val pm=context.packageManager
        val personal=ComponentName(context.packageName,"com.withyou.anniversary.PersonalLauncher")
        val together=ComponentName(context.packageName,"com.withyou.anniversary.TogetherLauncher")
        fun enabled(component:ComponentName,default:Boolean):Boolean = when(pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> default
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }
        if(enabled(personal,true)==!paired && enabled(together,false)==paired) return
        val on=if(paired) together else personal
        val off=if(paired) personal else together
        // MainActivity stays enabled for widget PendingIntents, deep links and old pinned shortcuts.
        runCatching {
            if(Build.VERSION.SDK_INT>=33) pm.setComponentEnabledSettings(listOf(
                PackageManager.ComponentEnabledSetting(on,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP),
                PackageManager.ComponentEnabledSetting(off,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP)))
            else {
                pm.setComponentEnabledSetting(on,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(off,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP)
            }
        }
    }
}

class LauncherRestoreReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) { LauncherIcons(context).restore() }
}
