package com.withyou.anniversary

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.TextView
import android.widget.FrameLayout
import android.view.View
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.SizeF
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WidgetTest {
    @Test fun bindAndRenderPinnedRecord() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        val repo=Repository(context)
        val before=repo.read()
        val record=MemoryDay(UUID.randomUUID().toString(),"桌面心动",LocalDate.now().minusDays(10),pinned=true)
        runBlocking {repo.save(AppData(listOf(record)))}
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("appwidget grantbind --package ${context.packageName}")).use {it.readBytes()}
        val host=AppWidgetHost(context,991)
        val id=host.allocateAppWidgetId()
        try {
            val manager=AppWidgetManager.getInstance(context)
            assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(context,AnniversaryWidget::class.java)))
            AnniversaryWidget.refresh(context)
            inst.waitForIdleSync()
            inst.runOnMainSync {
                val view=host.createView(context,id,manager.getAppWidgetInfo(id))
                assertEquals("桌面心动",view.findViewById<TextView>(R.id.widget_title).text.toString())
                assertEquals("11 天",view.findViewById<TextView>(R.id.widget_days).text.toString())
                assertEquals("已经",view.findViewById<TextView>(R.id.widget_caption).text.toString())
            }
        } finally {
            host.deleteAppWidgetId(id)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("appwidget revokebind --package ${context.packageName}")).use {it.readBytes()}
            runBlocking {repo.save(before)}
        }
    }
    @Test fun independentInstancesSizesDeletedReferencesAndReconfiguration() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val repo=Repository(context);val before=repo.read()
        val a=MemoryDay(UUID.randomUUID().toString(),"相遇的日子",LocalDate.now().minusDays(99),pinned=true)
        val b=MemoryDay(UUID.randomUUID().toString(),"一起去旅行",LocalDate.now().plusDays(20),color=17)
        runBlocking {repo.save(AppData(listOf(a,b)))}
        fun shell(command:String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(command)).use {it.readBytes()}}
        shell("appwidget grantbind --package ${context.packageName}")
        val host=AppWidgetHost(context,992);val manager=AppWidgetManager.getInstance(context);val ids=mutableListOf<Int>()
        try {
            listOf(SmallAnniversaryWidget::class.java,AnniversaryWidget::class.java,LargeAnniversaryWidget::class.java).forEachIndexed {kind,cls ->
                val id=host.allocateAppWidgetId();ids+=id
                assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(context,cls)))
                val choice=if(kind==1) WidgetChoice(b.id,false,"#394052","#FFFFFF") else WidgetChoice(a.id)
                WidgetStore(context).save(id,choice)
                assertEquals(choice,WidgetStore(context).get(id))
                val record=if(kind==1)b else a
                val w=if(kind==0)160 else 300;val h=if(kind==2)300 else 160
                val options=Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,w);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,w)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,h);putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,h)
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(w.toFloat(),h.toFloat())))
                }
                manager.updateAppWidgetOptions(id,options);AnniversaryWidget.refresh(context)
                inst.runOnMainSync {
                    val view=AnniversaryWidget.render(context,id,record,kind,choice).apply(context,FrameLayout(context))
                    assertEquals(record.title,view.findViewById<TextView>(R.id.widget_title).text.toString())
                    assertEquals(if(kind==1) "20 天" else "100 天",view.findViewById<TextView>(R.id.widget_days).text.toString())
                    assertEquals(if(kind==1) "还有" else "已经",view.findViewById<TextView>(R.id.widget_caption).text.toString())
                    if(kind==1)assertEquals(android.graphics.Color.WHITE,view.findViewById<TextView>(R.id.widget_days).currentTextColor)
                    if(kind==2) assertNotNull(view.findViewById<TextView>(R.id.widget_next))
                    val density=context.resources.displayMetrics.density
                    val width=(w*density).toInt();val height=(h*density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height)
                    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
                    File(context.getExternalFilesDir(null),"v11-widget-$kind.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                }
            }
            assertNotEquals(WidgetStore(context).get(ids[0])?.recordId,WidgetStore(context).get(ids[1])?.recordId)
            runBlocking {repo.save(AppData(listOf(a)))}
            assertEquals(b.id,WidgetStore(context).get(ids[1])?.recordId)
            inst.runOnMainSync {
                val missing=AnniversaryWidget.render(context,ids[1],null,1,WidgetStore(context).get(ids[1])).apply(context,FrameLayout(context))
                assertEquals("这份纪念已移除",missing.findViewById<TextView>(R.id.widget_title).text.toString())
                assertEquals("重新选择",missing.findViewById<TextView>(R.id.widget_days).text.toString())
            }
            WidgetStore(context).save(ids[1],WidgetChoice(a.id));AnniversaryWidget.refresh(context)
            inst.runOnMainSync {val v=host.createView(context,ids[1],manager.getAppWidgetInfo(ids[1]));assertEquals(a.title,v.findViewById<TextView>(R.id.widget_title).text.toString())}
        } finally {
            ids.forEach {host.deleteAppWidgetId(it);WidgetStore(context).delete(it)}
            shell("appwidget revokebind --package ${context.packageName}")
            runBlocking {repo.save(before)}
        }
    }
}
