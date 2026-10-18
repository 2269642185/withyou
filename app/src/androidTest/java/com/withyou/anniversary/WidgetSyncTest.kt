package com.withyou.anniversary

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WidgetSyncTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private fun shell(cmd:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use {it.readBytes()}
    private fun photo(color:Int):String {
        val name="${UUID.randomUUID()}.jpg";val bitmap=Bitmap.createBitmap(900,600,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        File(Repository(context).photos,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle();return name
    }
    private fun background(view:View)=(view.findViewById<ImageView>(R.id.widget_background).drawable as BitmapDrawable).bitmap
    private fun pixel(view:View):Int=background(view).let {it.getPixel(it.width/2,it.height/2)}
    private fun waitFor(block:()->Boolean) {val until=System.currentTimeMillis()+10000;while(System.currentTimeMillis()<until) {var good=false;inst.runOnMainSync {good=block()};if(good)return;Thread.sleep(50)};fail("Widget host did not update")}

    @Test fun realHostsFollowPhotoEditsWhileIndependentAndOtherRecordsStayUnchanged() {
        val repo=Repository(context);val before=repo.read()
        val red=photo(Color.RED);val blue=photo(Color.BLUE)
        val a=MemoryDay(UUID.randomUUID().toString(),"桌面同步相遇",LocalDate.now().minusDays(9),photo=red,template=CardTemplate.PHOTO)
        val b=a.copy(id=UUID.randomUUID().toString(),title="另一份纪念",photo=blue)
        runBlocking {repo.save(AppData(listOf(a,b)))}
        shell("appwidget grantbind --package ${context.packageName}")
        val host=AppWidgetHost(context,1300);val manager=AppWidgetManager.getInstance(context)
        val ids=mutableListOf<Int>();val views=mutableListOf<AppWidgetHostView>()
        try {
            host.startListening()
            repeat(5) {index ->
                val id=host.allocateAppWidgetId();ids+=id
                val cls=when(index) {0 ->SmallAnniversaryWidget::class.java;2 ->LargeAnniversaryWidget::class.java;else ->AnniversaryWidget::class.java}
                assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(context,cls)))
                val kind=if(index<3)index else 1;val g=WidgetGeometry.defaults(kind)
                manager.updateAppWidgetOptions(id,Bundle().apply {putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(g.width,g.height)))})
                WidgetStore(context).save(id,when(index) {3 ->WidgetChoice(a.id,false,"#394052","#FFFFFF");4 ->WidgetChoice(b.id);else ->WidgetChoice(a.id)})
                AnniversaryWidget.refresh(context)
                inst.runOnMainSync {views+=host.createView(context,id,manager.getAppWidgetInfo(id))}
            }
            waitFor {views.all {it.findViewById<TextView>(R.id.widget_days)!=null}}
            val old=views.map {pixel(it)}
            val edited=a.copy(photo=blue,title="已经换成蓝色",date=LocalDate.now().plusDays(4),appearance=CardAppearance(overlay=.85f))
            runBlocking {repo.save(AppData(listOf(edited,b)))} // save itself must update every bound host.
            waitFor {(0..3).all {views[it].findViewById<TextView>(R.id.widget_title).text.toString()==edited.title}}
            repeat(3) {assertNotEquals(old[it],pixel(views[it]));assertEquals("4 天",views[it].findViewById<TextView>(R.id.widget_days).text.toString())}
            assertEquals(old[3],pixel(views[3]));assertEquals(old[4],pixel(views[4]))
            assertEquals(Color.WHITE,views[3].findViewById<TextView>(R.id.widget_days).currentTextColor)
            // Switch back to follow, without altering the stored independent colors.
            val independent=WidgetStore(context).get(ids[3])!!
            WidgetStore(context).save(ids[3],independent.copy(followColors=true));AnniversaryWidget.refresh(context)
            waitFor {pixel(views[3])!=old[3]}
            val withoutPhoto=edited.copy(photo=null)
            runBlocking {repo.save(AppData(listOf(withoutPhoto,b)))}
            waitFor {pixel(views[0])!=pixel(views[4])}
            val minimal=edited.copy(template=CardTemplate.MINIMAL)
            runBlocking {repo.save(AppData(listOf(minimal,b)))}
            waitFor {views[0].findViewById<TextView>(R.id.widget_days).currentTextColor==ColorRules.argb(ColorRules.autoInk(ColorRules.background(minimal)))}
            // Backup restoration also refreshes host content; bindings are local and retained.
            val zip=File(context.cacheDir,"widget-sync-backup.zip")
            runBlocking {repo.backup(Uri.fromFile(zip),AppData(listOf(a,b)));repo.restore(Uri.fromFile(zip))}
            waitFor {views[0].findViewById<TextView>(R.id.widget_title).text.toString()==a.title}
            assertEquals(a.id,WidgetStore(context).get(ids[0])?.recordId)
            assertTrue(File(repo.photos,Repository(context).read().records.first().photo!!).isFile)
            manager.updateAppWidgetOptions(ids[0],Bundle().apply {putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(280f,280f)))})
            AnniversaryWidget.refresh(context)
            waitFor {views[0].findViewById<TextView>(R.id.widget_next)!=null}
            assertEquals(a.id,WidgetStore(context).get(ids[0])?.recordId)
            manager.updateAppWidgetOptions(ids[0],Bundle().apply {putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(110f,110f)))})
            AnniversaryWidget.refresh(context)
            waitFor {views[0].findViewById<TextView>(R.id.widget_next)==null}
            runBlocking {repo.save(AppData(listOf(b)))}
            waitFor {views[0].findViewById<TextView>(R.id.widget_title).text.toString()=="这份纪念已移除"}
        } finally {
            host.stopListening();ids.forEach {host.deleteAppWidgetId(it);WidgetStore(context).delete(it)}
            shell("appwidget revokebind --package ${context.packageName}")
            runBlocking {repo.save(before)}
        }
    }

    @Test fun everyTemplateAllSizesBoundedImagesMissingPhotosAndLargeFonts() {
        val configuration=Configuration(context.resources.configuration).apply {fontScale=1.6f}
        val largeContext=context.createConfigurationContext(configuration)
        val source=photo(Color.rgb(210,180,160))
        val record=MemoryDay(UUID.randomUUID().toString(),"很长的纪念日标题需要保证天数依然清晰",LocalDate.of(1,1,1),"小杏",photo=source)
        for(template in CardTemplate.entries) for(kind in 0..2) {
            val r=record.copy(template=template)
            val geometry=when(kind) {0 ->WidgetGeometry(110f,110f,0);2 ->WidgetGeometry(240f,250f,2);else ->WidgetGeometry(240f,130f,1)}
            val style=WidgetAppearance.resolve(r,null,kind)
            val bitmap=WidgetBackground.render(context,style,geometry)
            assertTrue(bitmap.width*bitmap.height<=WidgetBackground.MAX_PIXELS)
            // A retained photo must not influence paper template backgrounds.
            if(template in listOf(CardTemplate.MINIMAL,CardTemplate.TICKET,CardTemplate.NOTE)) {
                assertTrue(bitmap.sameAs(WidgetBackground.render(context,WidgetAppearance.resolve(r.copy(photo="missing.jpg"),null,kind),geometry)))
            }
            val missing=WidgetBackground.render(context,WidgetAppearance.resolve(r.copy(photo="missing.jpg"),null,kind),geometry)
            assertTrue(missing.width>0)
            inst.runOnMainSync {
                val view=AnniversaryWidget.render(largeContext,1301,r,kind,null,width=geometry.width,height=geometry.height).apply(largeContext,FrameLayout(largeContext))
                val d=context.resources.displayMetrics.density;val w=(geometry.width*d).toInt();val h=(geometry.height*d).toInt()
                // TextView auto-size requests another traversal when it changes text size.
                // This detached harness must perform the passes a real launcher schedules.
                repeat(3) {view.forceLayout();view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)}
                val days=view.findViewById<TextView>(R.id.widget_days)
                assertTrue("$template/$kind count visible",days.height>0)
                assertTrue("$template/$kind count fits vertically: ${days.layout.height}/${days.height}, textPx=${days.textSize}",days.layout.height+days.compoundPaddingTop<=days.height)
                assertEquals(1,days.layout.lineCount);assertEquals(0,days.layout.getEllipsisCount(0))
                assertTrue(days.text.toString().endsWith(" 天"))
                if(template==CardTemplate.PHOTO && kind==1) {
                    val bg=view.findViewById<ImageView>(R.id.widget_background)
                    bg.setColorFilter(Color.MAGENTA)
                    AnniversaryWidget.render(largeContext,1301,r,kind,null,width=geometry.width,height=geometry.height).reapply(largeContext,view)
                    val testImage=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);bg.draw(Canvas(testImage))
                    assertTrue("legacy tint must not recolor the photo",Color.green(testImage.getPixel(w/2,h/2))>10)
                    testImage.recycle()
                }
                val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(Canvas(image))
                File(context.getExternalFilesDir(null),"v130-$template-$kind-large-font.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            }
        }
    }
}
