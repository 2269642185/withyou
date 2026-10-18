package com.withyou.anniversary

import android.content.Context
import android.graphics.*
import android.util.LruCache
import java.io.File
import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Only backgrounds are rasterized. Titles/counts remain real accessible RemoteViews text. */
object WidgetBackground {
    const val MAX_PIXELS=72_000
    private val backgrounds=object:LruCache<String,Bitmap>(4*1024*1024) {override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    private val photos=object:LruCache<String,Bitmap>(2*1024*1024) {override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}

    @Synchronized fun render(context:Context,style:WidgetAppearance,g:WidgetGeometry):Bitmap {
        val file=style.photoName?.let {File(Repository(context).photos,it)}
        val stamp=file?.let {"${it.absolutePath}:${it.lastModified()}:${it.length()}"} ?: "fallback"
        val key="$style:$g:$stamp"
        backgrounds.get(key)?.let {return it}
        val scale=minOf(1.6f,sqrt(MAX_PIXELS.toFloat()/(g.width*g.height)))
        val w=(g.width*scale).toInt().coerceAtLeast(1);val h=(g.height*scale).toInt().coerceAtLeast(1)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.scale(w/g.width,h/g.height)
        val bounds=RectF(0f,0f,g.width,g.height)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val outline=Path().apply {addRoundRect(bounds,if(style.template==CardTemplate.POLAROID || style.template==CardTemplate.NOTE) 6f else 22f,if(style.template==CardTemplate.POLAROID || style.template==CardTemplate.NOTE) 6f else 22f,Path.Direction.CW)}
        if(style.template==CardTemplate.TICKET) {
            val holes=Path().apply {addCircle(0f,g.height*.73f,8f,Path.Direction.CW);addCircle(g.width,g.height*.73f,8f,Path.Direction.CW)}
            outline.op(holes,Path.Op.DIFFERENCE)
        }
        canvas.clipPath(outline)
        canvas.drawColor(ColorRules.argb(style.background))
        val photo=if(style.photoBehindText || style.photoPanel) loadPhoto(file,stamp) else null
        if(style.photoBehindText) {
            drawPhoto(canvas,paint,photo,bounds,style.background)
            val whiteInk=ColorRules.contrast(style.ink,"#000000")>=ColorRules.contrast(style.ink,"#FFFFFF")
            val veil=if(whiteInk) Color.BLACK else Color.WHITE
            fun alpha(value:Float)=(veil and 0x00FFFFFF) or ((value.coerceIn(0f,1f)*255).roundToInt() shl 24)
            paint.shader=LinearGradient(0f,0f,0f,g.height,intArrayOf(alpha(style.overlay-.15f),alpha(style.overlay+.15f)),null,Shader.TileMode.CLAMP)
            canvas.drawRect(bounds,paint);paint.shader=null
        } else if(style.photoPanel) {
            val rect=if(g.kind==1) RectF(10f,10f,g.width*.38f,g.height-10f) else RectF(12f,12f,g.width-12f,g.height*.28f+10f)
            canvas.save();canvas.clipPath(Path().apply {addRoundRect(rect,3f,3f,Path.Direction.CW)})
            drawPhoto(canvas,paint,photo,rect,style.background);canvas.restore()
        } else {
            paint.color=ColorRules.argb(style.auxiliary)
            when(style.template) {
                CardTemplate.NOTE -> {
                    paint.alpha=16;paint.strokeWidth=.6f
                    var y=42f
                    while(y<g.height) {canvas.drawLine(10f,y,g.width-10f,y,paint);y+=27f}
                    canvas.drawLine(10f,0f,10f,g.height,paint)
                    paint.alpha=28;canvas.save();canvas.rotate(3f,g.width-50f,15f);canvas.drawRect(g.width-78f,if(g.kind==0) 5f else 8f,g.width-20f,if(g.kind==0) 11f else 18f,paint);canvas.restore()
                }
                CardTemplate.TICKET -> {
                    paint.alpha=60;paint.strokeWidth=1f;paint.pathEffect=DashPathEffect(floatArrayOf(4f,4f),0f)
                    canvas.drawLine(14f,g.height*.73f,g.width-14f,g.height*.73f,paint);paint.pathEffect=null
                    if(g.kind==2) {paint.alpha=45;for(i in 0..18) {val x=g.width-64f+i*2f;canvas.drawLine(x,g.height-60f,x,g.height-42f,paint)}}
                }
                CardTemplate.CLASSIC -> {
                    paint.color=if(ColorRules.contrast(style.ink,"#FFFFFF")>=ColorRules.contrast(style.ink,"#000000")) Color.WHITE else Color.BLACK
                    paint.alpha=30;canvas.drawCircle(g.width,g.height*.2f,g.width*.45f,paint)
                }
                else -> Unit
            }
        }
        backgrounds.put(key,bitmap)
        return bitmap
    }

    private fun loadPhoto(file:File?,stamp:String):Bitmap? {
        if(file?.isFile!=true) return null
        photos.get(stamp)?.let {return it}
        return runCatching {
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeFile(file.path,bounds)
            if(bounds.outWidth<=0 || bounds.outHeight<=0) return null
            var sample=1
            while((maxOf(bounds.outWidth,bounds.outHeight)+sample-1)/sample>512) sample*=2
            BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply {inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888})?.also {photos.put(stamp,it)}
        }.getOrNull()
    }

    private fun drawPhoto(canvas:Canvas,paint:Paint,photo:Bitmap?,rect:RectF,background:String) {
        paint.alpha=255
        if(photo!=null) {
            val scale=maxOf(rect.width()/photo.width,rect.height()/photo.height)
            val sw=rect.width()/scale;val sh=rect.height()/scale
            val sx=(photo.width-sw)/2;val sy=(photo.height-sh)/2
            canvas.drawBitmap(photo,Rect(sx.toInt(),sy.toInt(),(sx+sw).toInt(),(sy+sh).toInt()),rect,paint)
        } else {
            paint.shader=LinearGradient(rect.left,rect.top,rect.right,rect.bottom,ColorRules.argb(background),Color.rgb(169,183,160),Shader.TileMode.CLAMP)
            canvas.drawRect(rect,paint);paint.shader=null
            paint.color=Color.rgb(255,232,188);canvas.drawCircle(rect.left+rect.width()*.75f,rect.top+rect.height()*.26f,rect.width()*.1f,paint)
            paint.color=Color.argb(100,100,116,93)
            val hills=Path().apply {
                moveTo(rect.left,rect.top+rect.height()*.8f)
                quadTo(rect.left+rect.width()*.3f,rect.top+rect.height()*.25f,rect.left+rect.width()*.6f,rect.top+rect.height()*.75f)
                quadTo(rect.left+rect.width()*.8f,rect.top+rect.height()*.48f,rect.right,rect.top+rect.height()*.65f)
                lineTo(rect.right,rect.bottom);lineTo(rect.left,rect.bottom);close()
            }
            canvas.drawPath(hills,paint)
        }
    }
}
