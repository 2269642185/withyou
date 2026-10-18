package com.withyou.anniversary

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun MemoryCard(record: MemoryDay, today: LocalDate, dir: File, hero: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    val motion = LocalMotionEnabled.current
    AnimatedContent(
        targetState = record.template,
        transitionSpec = { fadeIn(tween(if(motion) 180 else 0)) togetherWith fadeOut(tween(if(motion) 120 else 0)) },
        modifier = modifier.fillMaxWidth(), label = "卡片模板"
    ) { template ->
        val current = record.copy(template = template)
        if(template == CardTemplate.CLASSIC) ClassicMemoryCard(current,today,dir,hero,click=click)
        else DesignedCard(current,today,dir,hero,click=click)
    }
}

/** Text stays in normal document flow; only the image window has a fixed aspect ratio. */
@Composable
internal fun DesignedCard(record: MemoryDay, today: LocalDate, dir: File, hero: Boolean, thumbnail: Boolean = false, click: () -> Unit) {
    val motion = LocalMotionEnabled.current && !thumbnail
    val duration = if(motion) 200 else 0
    val fullPhoto = record.template == CardTemplate.PHOTO || (record.template == CardTemplate.CLASSIC && record.photo != null)
    // A stored photo must not make text white on the paper portion of a polaroid/ticket.
    val colors = record.templateColors()
    val background by animateColorAsState(Color(ColorRules.argb(ColorRules.background(colors))),tween(duration),label="模板背景")
    val ink by animateColorAsState(Color(ColorRules.argb(ColorRules.ink(colors))),tween(duration),label="模板文字")
    val titleInk = record.appearance.title?.let { Color(ColorRules.argb(it)) } ?: ink
    val daysInk = record.appearance.days?.let { Color(ColorRules.argb(it)) } ?: ink
    val auxInk = record.appearance.auxiliary?.let { Color(ColorRules.argb(it)) } ?: ink
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if(motion && pressed) .985f else 1f,tween(if(motion) 100 else 0),label="模板按压")
    val radius = when(record.template) { CardTemplate.POLAROID -> 8.dp; CardTemplate.TICKET -> 16.dp; CardTemplate.NOTE -> 6.dp; else -> if(hero) 30.dp else 24.dp }
    val notch=with(LocalDensity.current) {(if(thumbnail) 5.dp else 9.dp).toPx()}
    val shape=if(record.template==CardTemplate.TICKET) remember(notch) {GenericShape {size,_ ->
        val paper=Path().apply {addRoundRect(RoundRect(Rect(Offset.Zero,size),CornerRadius(notch*1.8f)))}
        val holes=Path().apply {
            addOval(Rect(-notch,size.height*.73f-notch,notch,size.height*.73f+notch))
            addOval(Rect(size.width-notch,size.height*.73f-notch,size.width+notch,size.height*.73f+notch))
        }
        op(paper,holes,PathOperation.Difference)
    }} else RoundedCornerShape(radius)
    Surface(onClick=click, interactionSource=source, color=background, shape=shape,
        modifier=Modifier.fillMaxWidth().graphicsLayer {scaleX=scale;scaleY=scale}.testTag("card_${record.template.key}")) {
        Box(Modifier.fillMaxWidth().then(if(motion) Modifier.animateContentSize(tween(200)) else Modifier)) {
            if(fullPhoto) {
                TemplatePhoto(record,dir,Modifier.matchParentSize())
                val veil = if(ColorRules.contrast(ColorRules.ink(colors),"#000000") >= ColorRules.contrast(ColorRules.ink(colors),"#FFFFFF")) Color.Black else Color.White
                val strength = if(record.appearance.tone==TextTone.AUTO) maxOf(.72f,record.appearance.overlay) else maxOf(.25f,record.appearance.overlay)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(veil.copy(alpha=(strength-.18f).coerceIn(0f,1f)),veil.copy(alpha=(strength+.15f).coerceIn(0f,1f))))))
            }
            if(record.template == CardTemplate.NOTE) NotePaper(auxInk,Modifier.matchParentSize())
            val pad = if(thumbnail) 12.dp else if(hero) 28.dp else 22.dp
            Column(Modifier.fillMaxWidth().padding(pad),verticalArrangement=Arrangement.spacedBy(if(thumbnail) 7.dp else 14.dp)) {
                when(record.template) {
                    CardTemplate.POLAROID -> {
                        Box(Modifier.fillMaxWidth().aspectRatio(if(thumbnail) 1.8f else 1.45f).clip(RoundedCornerShape(3.dp))) {
                            TemplatePhoto(record,dir,Modifier.matchParentSize())
                            // The photo itself has no overlaid text, so its paper caption stays legible.
                        }
                        TemplateTitle(record,titleInk,hero,thumbnail)
                        DaysBlock(record,today,daysInk,auxInk,hero,thumbnail)
                        TemplateFooter(record,today,auxInk,thumbnail)
                    }
                    CardTemplate.PHOTO -> {
                        TemplateEyebrow(record,auxInk,thumbnail,"珍藏的瞬间")
                        Spacer(Modifier.height(if(thumbnail) 14.dp else if(hero) 80.dp else 42.dp))
                        TemplateTitle(record,titleInk,hero,thumbnail)
                        DaysBlock(record,today,daysInk,auxInk,hero,thumbnail)
                        TemplateFooter(record,today,auxInk,thumbnail)
                    }
                    CardTemplate.MINIMAL -> {
                        TemplateEyebrow(record,auxInk,thumbnail,"日子 · 慢慢积攒")
                        Spacer(Modifier.height(if(thumbnail) 0.dp else 8.dp))
                        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                            DaysBlock(record,today,daysInk,auxInk,hero,thumbnail,center=true)
                        }
                        HorizontalDivider(Modifier.width(32.dp).align(Alignment.CenterHorizontally),color=auxInk.copy(alpha=.45f))
                        TemplateTitle(record,titleInk,hero,thumbnail,center=true)
                        TemplateFooter(record,today,auxInk,thumbnail,center=true)
                    }
                    CardTemplate.TICKET -> {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ConfirmationNumber,null,Modifier.size(if(thumbnail) 14.dp else 22.dp),tint=auxInk)
                            Text("  与你同行",Modifier.weight(1f),color=auxInk,fontSize=if(thumbnail) 10.sp else 12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            if(!thumbnail && LocalDensity.current.fontScale<1.4f) Text("ADMIT TWO",color=auxInk,fontSize=10.sp,letterSpacing=1.sp)
                        }
                        TicketRule(auxInk)
                        TemplateTitle(record,titleInk,hero,thumbnail)
                        DaysBlock(record,today,daysInk,auxInk,hero,thumbnail)
                        TicketRule(auxInk)
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {TemplateFooter(record,today,auxInk,thumbnail)}
                            Canvas(Modifier.width(if(thumbnail) 22.dp else 48.dp).height(if(thumbnail) 18.dp else 28.dp)) {
                                for(i in 0..19) {val x=i*size.width/20; drawLine(auxInk.copy(alpha=.65f),Offset(x,0f),Offset(x,size.height),if(i%3==0) 3f else 1f)}
                            }
                        }
                    }
                    CardTemplate.NOTE -> {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            Box(Modifier.width(if(thumbnail) 44.dp else 74.dp).height(if(thumbnail) 10.dp else 16.dp).graphicsLayer {rotationZ=3f}.background(auxInk.copy(alpha=.12f)))
                        }
                        Text(if(thumbnail) "写给日常" else "写给我们的约定",color=auxInk,fontSize=if(thumbnail) 9.sp else 12.sp)
                        TemplateTitle(record,titleInk,hero,thumbnail)
                        DaysBlock(record,today,daysInk,auxInk,hero,thumbnail)
                        TemplateFooter(record,today,auxInk,thumbnail)
                        if(!thumbnail) Text("平凡的一天，也值得好好记住。",color=auxInk,fontSize=12.sp,fontFamily=FontFamily.Serif)
                    }
                    CardTemplate.CLASSIC -> {
                        TemplateEyebrow(record,auxInk,thumbnail,"我们的纪念")
                        TemplateTitle(record,titleInk,hero,thumbnail)
                        DaysBlock(record,today,daysInk,auxInk,hero,thumbnail)
                        TemplateFooter(record,today,auxInk,thumbnail)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateTitle(record: MemoryDay, ink: Color, hero: Boolean, small: Boolean, center: Boolean=false) {
    Column(Modifier.fillMaxWidth(),horizontalAlignment=if(center) Alignment.CenterHorizontally else Alignment.Start,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(record.title,color=ink,fontSize=if(small) 13.sp else if(hero) 26.sp else 22.sp,
            fontWeight=if(record.template==CardTemplate.NOTE) FontWeight.Medium else FontWeight.SemiBold,
            fontFamily=if(record.template==CardTemplate.NOTE) FontFamily.Serif else FontFamily.Default,
            textAlign=if(center) TextAlign.Center else TextAlign.Start,
            maxLines=if(small) 2 else Int.MAX_VALUE,overflow=TextOverflow.Ellipsis)
        if(!small && record.nickname.isNotBlank()) Text("和 ${record.nickname}",color=ink,fontSize=14.sp,textAlign=if(center) TextAlign.Center else TextAlign.Start)
    }
}

@Composable
private fun TemplateEyebrow(record: MemoryDay, ink: Color, small: Boolean, label: String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Icon(if(record.pinned) Icons.Outlined.PushPin else Icons.Outlined.FavoriteBorder,null,Modifier.size(if(small) 12.dp else 16.dp),tint=ink)
        Text(if(record.pinned) "置顶 · 最特别的日子" else label,color=ink,fontSize=if(small) 9.sp else 12.sp,modifier=Modifier.weight(1f))
        if(!small) Icon(Icons.Outlined.ArrowOutward,"查看详情",Modifier.size(18.dp),tint=ink)
    }
}

@Composable
private fun DaysBlock(record: MemoryDay, today: LocalDate, ink: Color, aux: Color, hero: Boolean, small: Boolean, center: Boolean=false) {
    val count=DateRules.count(record.date,today,record.mode)
    val duration=if(LocalMotionEnabled.current && !small) 180 else 0
    Column(horizontalAlignment=if(center) Alignment.CenterHorizontally else Alignment.Start,verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(count.caption,color=aux,fontSize=if(small) 10.sp else 14.sp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale=LocalDensity.current.fontScale
            val base=if(small) 30f else if(record.template==CardTemplate.MINIMAL) (if(hero) 96f else 76f) else if(hero) 72f else 54f
            var size by remember(count.days,maxWidth,fontScale,base) {mutableFloatStateOf(base)}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=if(center) Arrangement.Center else Arrangement.Start) {
                AnimatedContent(targetState=count.days,transitionSpec={fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))},
                    modifier=Modifier.weight(1f,fill=false),label="模板天数") {days ->
                    Text(days.toString(),color=ink,fontSize=size.sp,fontWeight=FontWeight.Light,letterSpacing=(-1).sp,
                        maxLines=1,softWrap=false,onTextLayout={if(it.hasVisualOverflow && size>12f) size=(size*.9f).coerceAtLeast(12f)})
                }
                Text("天",color=ink,fontSize=if(small) 10.sp else 16.sp,modifier=Modifier.padding(start=6.dp))
            }
        }
    }
}

@Composable
private fun TemplateFooter(record: MemoryDay, today: LocalDate, ink: Color, small: Boolean, center: Boolean=false) {
    Text(if(small) record.date.toString() else "${record.date.label()}  ·  ${if(record.date==today) "今天值得庆祝" else if(record.date>today) "期待故事开始" else "故事的开始"}",
        modifier=Modifier.fillMaxWidth(),color=ink,fontSize=if(small) 9.sp else 12.sp,textAlign=if(center) TextAlign.Center else TextAlign.Start)
}

@Composable
private fun TicketRule(ink: Color) {
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        drawLine(ink.copy(alpha=.35f),Offset(0f,size.height/2),Offset(size.width,size.height/2),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),4.dp.toPx())))
        drawCircle(ink.copy(alpha=.12f),5.dp.toPx(),Offset(0f,size.height/2))
        drawCircle(ink.copy(alpha=.12f),5.dp.toPx(),Offset(size.width,size.height/2))
    }
}

@Composable
private fun NotePaper(ink: Color, modifier: Modifier) {
    Canvas(modifier) {
        val step=32.dp.toPx(); var y=64.dp.toPx()
        while(y<size.height) {drawLine(ink.copy(alpha=.055f),Offset(12.dp.toPx(),y),Offset(size.width-12.dp.toPx(),y),1.dp.toPx());y+=step}
        drawLine(ink.copy(alpha=.08f),Offset(12.dp.toPx(),0f),Offset(12.dp.toPx(),size.height),1.dp.toPx())
    }
}

@Composable
private fun TemplatePhoto(record: MemoryDay, dir: File, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null,record.photo,dir) {
        value=withContext(Dispatchers.IO) {record.photo?.let {name -> runCatching { BitmapFactory.decodeFile(File(dir,name).path)?.asImageBitmap() }.getOrNull()}}
    }
    val base=Color(ColorRules.argb(ColorRules.background(record)))
    Box(modifier.background(Brush.linearGradient(listOf(base,Color(0xFFA9B7A0))))) {
        bitmap?.let {Image(it,null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)} ?: Canvas(Modifier.matchParentSize()) {
            // A calm landscape, also used when the private photo file cannot be decoded.
            drawCircle(Color(0xFFFFE8BC),size.width*.1f,Offset(size.width*.75f,size.height*.26f))
            val hills=Path().apply {moveTo(0f,size.height*.8f);quadraticTo(size.width*.3f,size.height*.25f,size.width*.6f,size.height*.75f);quadraticTo(size.width*.8f,size.height*.48f,size.width,size.height*.65f);lineTo(size.width,size.height);lineTo(0f,size.height);close()}
            drawPath(hills,Color(0xFF64745D).copy(alpha=.4f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateChooser(record: MemoryDay, today: LocalDate, dir: File, choose: (CardTemplate) -> Unit) {
    var open by remember {mutableStateOf(false)}
    Surface(onClick={open=true},shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f),modifier=Modifier.fillMaxWidth().testTag("template_chooser")) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            TemplateMark(record.template,Modifier.size(48.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("卡片模板",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(record.template.label,style=MaterialTheme.typography.titleMedium)
                Text("6 种风格 · 点击选择",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight,null)
        }
    }
    if(open) ModalBottomSheet(onDismissRequest={open=false},containerColor=MaterialTheme.colorScheme.surface,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text("选一种心动的样子",style=MaterialTheme.typography.titleLarge);Text("使用当前内容预览，保存纪念日后生效",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                TextButton(onClick={open=false}) {Text("完成")}
            }
            LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.weight(1f).testTag("template_grid")) {
                items(CardTemplate.entries,key={it.key}) {template ->
                    val chosen=record.template==template
                    Surface(onClick={choose(template)},shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,
                        border=BorderStroke(if(chosen) 2.dp else 1.dp,if(chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha=.25f)),
                        modifier=Modifier.testTag("choose_${template.key}").semantics {selected=chosen;contentDescription="${template.label}模板"}) {
                        Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            // Cards use a short thumbnail layout while full previews retain every field.
                            DesignedCard(record.copy(template=template),today,dir,false,thumbnail=true) {choose(template)}
                            Row(Modifier.padding(horizontal=3.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(template.label,Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                                if(chosen) Icon(Icons.Outlined.CheckCircle,"已选择",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small layout drawing keeps the collapsed chooser lightweight. */
@Composable
private fun TemplateMark(template: CardTemplate, modifier: Modifier) {
    val ink=MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w=size.width; val h=size.height
        drawRoundRect(ink.copy(alpha=.12f),cornerRadius=CornerRadius(8.dp.toPx()))
        when(template) {
            CardTemplate.POLAROID -> {drawRect(ink.copy(alpha=.3f),Offset(w*.16f,h*.12f),Size(w*.68f,h*.48f));drawLine(ink,Offset(w*.22f,h*.78f),Offset(w*.7f,h*.78f),2.dp.toPx())}
            CardTemplate.PHOTO -> {drawCircle(ink.copy(alpha=.5f),w*.11f,Offset(w*.7f,h*.3f));drawLine(ink,Offset(w*.16f,h*.75f),Offset(w*.75f,h*.75f),3.dp.toPx())}
            CardTemplate.TICKET -> {drawLine(ink,Offset(w*.15f,h*.6f),Offset(w*.85f,h*.6f),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),2.dp.toPx())));drawRect(ink,Offset(w*.2f,h*.2f),Size(w*.18f,h*.22f))}
            CardTemplate.NOTE -> {for(i in 1..3) drawLine(ink.copy(alpha=.5f),Offset(w*.2f,h*i/4),Offset(w*.8f,h*i/4),1.dp.toPx())}
            else -> {drawRoundRect(ink,Offset(w*.25f,h*.2f),Size(w*.2f,h*.45f),CornerRadius(3.dp.toPx()));drawLine(ink.copy(alpha=.5f),Offset(w*.2f,h*.8f),Offset(w*.8f,h*.8f),1.dp.toPx())}
        }
    }
}
