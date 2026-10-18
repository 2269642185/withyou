package com.withyou.anniversary

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import android.view.Gravity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class WidgetChoice(val recordId: String, val followColors: Boolean = true, val background: String = "#F0DCC8", val text: String? = null)
class WidgetStore(context: Context) {
    private val prefs=context.getSharedPreferences("widget_choices",Context.MODE_PRIVATE)
    fun get(id: Int): WidgetChoice? = prefs.getString("${id}_record",null)?.let {WidgetChoice(it,prefs.getBoolean("${id}_follow",true),prefs.getString("${id}_background","#F0DCC8")!!,prefs.getString("${id}_text",null))}
    fun save(id: Int, choice: WidgetChoice) {
        require(ColorRules.normalize(choice.background)!=null && (choice.text==null || ColorRules.normalize(choice.text)!=null))
        check(prefs.edit().putString("${id}_record",choice.recordId).putBoolean("${id}_follow",choice.followColors)
            .putString("${id}_background",choice.background).putString("${id}_text",choice.text).commit()) {"小组件配置保存失败"}
    }
    fun delete(id: Int) {prefs.edit().remove("${id}_record").remove("${id}_follow").remove("${id}_background").remove("${id}_text").apply()}
}

class SmallAnniversaryWidget : AnniversaryWidget()
class LargeAnniversaryWidget : AnniversaryWidget()

open class AnniversaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) = refresh(context)
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {appWidgetIds.forEach {WidgetStore(context).delete(it)}}
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (old,new) ->WidgetStore(context).get(old)?.let {WidgetStore(context).save(new,it)} }
    }
    override fun onReceive(context: Context, intent: Intent) {
        val action=intent.action ?: return
        if(action !in setOf(AppWidgetManager.ACTION_APPWIDGET_UPDATE,AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,
                AppWidgetManager.ACTION_APPWIDGET_DELETED,AppWidgetManager.ACTION_APPWIDGET_ENABLED,AppWidgetManager.ACTION_APPWIDGET_DISABLED,
                AppWidgetManager.ACTION_APPWIDGET_RESTORED,Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_DATE_CHANGED,Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,"com.withyou.anniversary.MIDNIGHT")) return
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {receiveNow(context,intent)}
            catch(e:Exception) {Log.e("WithYouWidget","Widget broadcast failed",e)}
            finally {pending.finish()}
        }
    }
    private fun receiveNow(context:Context,intent:Intent) {
        super.onReceive(context,intent)
        if(intent.action!=AppWidgetManager.ACTION_APPWIDGET_UPDATE && intent.action!=AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED) refresh(context)
    }
    override fun onDisabled(context: Context) {if(allIds(context).isEmpty()) context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))}
    companion object {
        fun allIds(context: Context): IntArray {
            val manager=AppWidgetManager.getInstance(context)
            return listOf(AnniversaryWidget::class.java,SmallAnniversaryWidget::class.java,LargeAnniversaryWidget::class.java)
                .flatMap {manager.getAppWidgetIds(ComponentName(context,it)).toList()}.distinct().toIntArray()
        }
        private fun tickIntent(context: Context) = PendingIntent.getBroadcast(context,19,
            Intent(context,AnniversaryWidget::class.java).setAction("com.withyou.anniversary.MIDNIGHT"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @Synchronized fun refresh(context: Context) {
            val ids=allIds(context)
            if(ids.isEmpty()) return
            val manager=AppWidgetManager.getInstance(context)
            val result=runCatching {Repository(context).read()}
            val data=result.getOrDefault(AppData())
            val store=WidgetStore(context)
            ids.forEach {id -> runCatching {
                val config=store.get(id)
                val record=if(config==null) data.ordered().firstOrNull() else data.records.firstOrNull {it.id==config.recordId}
                val opts=manager.getAppWidgetOptions(id)
                val provider=manager.getAppWidgetInfo(id)?.provider?.className
                val fallback=if(provider==LargeAnniversaryWidget::class.java.name) 280 else 140
                fun view(w:Float,h:Float):RemoteViews {
                    val width=w.coerceIn(80f,800f);val height=h.coerceIn(80f,800f)
                    return render(context,id,record,WidgetGeometry.kind(width,height),config,result.isFailure,width,height)
                }
                val views=if(Build.VERSION.SDK_INT>=31) {
                    val sizes=opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                        ?.filter {it.width.isFinite() && it.height.isFinite() && it.width>0 && it.height>0}?.distinct()?.take(2)
                    val responsive=if(sizes.isNullOrEmpty()) listOf(SizeF(110f,110f),SizeF(240f,130f),SizeF(240f,250f)) else sizes
                    RemoteViews(responsive.associateWith {view(it.width,it.height)})
                } else RemoteViews(
                    view(opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,280).toFloat(),opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,fallback).toFloat()),
                    view(opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,if(provider==SmallAnniversaryWidget::class.java.name)140 else 280).toFloat(),opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,fallback).toFloat()))
                manager.updateAppWidget(id,views)
            }.onFailure {Log.e("WithYouWidget","Widget update failed: $id",it)} }
            val now=LocalDate.now()
            context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC,
                now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),tickIntent(context))
        }
        fun render(context: Context, id: Int, record: MemoryDay?, kind: Int, config: WidgetChoice?, failed: Boolean = false,
                   width:Float=WidgetGeometry.defaults(kind).width,height:Float=WidgetGeometry.defaults(kind).height): RemoteViews {
            val style=WidgetAppearance.resolve(record,config,kind)
            val minimal=style.template==CardTemplate.MINIMAL
            val layout=when(kind) {
                0 ->if(minimal) R.layout.widget_small_minimal else R.layout.widget_small
                2 ->if(minimal) R.layout.widget_large_minimal else R.layout.widget_large
                else ->if(minimal) R.layout.widget_anniversary_minimal else R.layout.widget_anniversary
            }
            val views=RemoteViews(context.packageName,layout)
            val geometry=WidgetGeometry(width.coerceIn(80f,800f),height.coerceIn(80f,800f),kind)
            views.setImageViewBitmap(R.id.widget_background,WidgetBackground.render(context,style,geometry))
            // Older releases tinted this ImageView. Reapply may reuse it after an upgrade;
            // transparent SRC_ATOP is identity and removes the old opaque tint's effect.
            views.setInt(R.id.widget_background,"setColorFilter",android.graphics.Color.TRANSPARENT)
            val density=context.resources.displayMetrics.density
            views.setViewPadding(R.id.widget_content,(geometry.contentLeft(style)*density).toInt(),(geometry.contentTop(style)*density).toInt(),(geometry.left*density).toInt(),(32*density).toInt())
            views.setTextColor(R.id.widget_title,ColorRules.argb(style.title));views.setTextColor(R.id.widget_days,ColorRules.argb(style.days))
            views.setTextColor(R.id.widget_caption,ColorRules.argb(style.auxiliary));views.setTextColor(R.id.widget_config,ColorRules.argb(style.auxiliary))
            if(minimal) {
                views.setInt(R.id.widget_content,"setGravity",Gravity.CENTER_HORIZONTAL)
                listOf(R.id.widget_title,R.id.widget_caption,R.id.widget_days).forEach {views.setInt(it,"setGravity",Gravity.CENTER)}
            }
            views.setTextViewText(R.id.widget_title,record?.title ?: if(failed) "暂时无法读取记录" else if(config!=null) "这份纪念已移除" else "与你 · 纪念日")
            val now=LocalDate.now()
            val count=record?.let {DateRules.count(it.date,now,it.mode)}
            views.setTextViewText(R.id.widget_caption,count?.caption ?: "轻触设置")
            views.setTextViewText(R.id.widget_days,count?.let {"${it.days} 天"} ?: "重新选择")
            if(kind!=0) {
                views.setTextColor(R.id.widget_date,ColorRules.argb(style.auxiliary))
                views.setTextViewText(R.id.widget_date,record?.let {"${it.date} · ${it.nickname.ifBlank {"故事的开始"}}"} ?: "轻触设置，选择一个纪念日")
            }
            if(kind==2) {
                views.setTextColor(R.id.widget_next,ColorRules.argb(style.auxiliary))
                views.setTextViewText(R.id.widget_next,record?.let {val n=DateRules.untilAnniversary(it.date,now);"${if(n==0L) "今天就是纪念日" else "下次纪念 · 还有 $n 天"}\n${DateRules.nextAnniversary(it.date,now)}"} ?: "把心动的日子，放在桌面上")
            }
            val configure=Intent(context,WidgetConfigurationActivity::class.java).setData(Uri.parse("withyou://widget-config/$id"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val configurePending=PendingIntent.getActivity(context,id,configure,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_config,configurePending)
            val open=Intent(context,MainActivity::class.java).setData(Uri.parse("withyou://widget/$id/${record?.id}"))
                .putExtra("recordId",record?.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_root,if(record==null) configurePending else PendingIntent.getActivity(context,id,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return views
        }
    }
}
