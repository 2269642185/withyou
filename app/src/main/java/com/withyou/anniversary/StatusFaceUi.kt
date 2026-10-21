package com.withyou.anniversary

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Local vector drawing keeps the tiny faces consistent across manufacturers and offline. */
@Composable
internal fun SmallStatusFace(face:StatusFace,modifier:Modifier=Modifier) {
    Crossfade(face,animationSpec=tween(if(LocalMotionEnabled.current) 160 else 0),label="状态小表情",modifier=modifier.size(20.dp)) {value ->
        Canvas(Modifier.fillMaxSize().testTag("face_${value.name}")) {
            val unit=size.minDimension/24f
            fun p(x:Float,y:Float)=Offset(x*unit,y*unit)
            val ink=Color(0xFF5A473A)
            val bg=when(value) {
                StatusFace.HAPPY,StatusFace.SMILE,StatusFace.PARTY->Color(0xFFD4EBDD)
                StatusFace.CRYING,StatusFace.SAD,StatusFace.WORRIED->Color(0xFFF4DEB9)
                else->Color(0xFFF2E6D6)
            }
            drawCircle(bg,10.8f*unit,p(12f,12f))
            fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(ink,p(x,y),p(x2,y2),1.35f*unit,StrokeCap.Round)
            fun arc(y:Float,sad:Boolean=false)=drawArc(ink,if(sad) 200f else 20f,140f,false,p(8f,y),Size(8f*unit,5f*unit),style=Stroke(1.35f*unit,cap=StrokeCap.Round))
            when(value) {
                StatusFace.HAPPY,StatusFace.PARTY->{
                    drawArc(ink,200f,140f,false,p(6f,7f),Size(4f*unit,4f*unit),style=Stroke(1.35f*unit,cap=StrokeCap.Round))
                    drawArc(ink,200f,140f,false,p(14f,7f),Size(4f*unit,4f*unit),style=Stroke(1.35f*unit,cap=StrokeCap.Round))
                }
                StatusFace.SLEEPY->{line(6.5f,10f,9f,10f);line(15f,10f,17.5f,10f)}
                else->{drawCircle(ink,1f*unit,p(8f,10f));drawCircle(ink,1f*unit,p(16f,10f))}
            }
            when(value) {
                StatusFace.HAPPY,StatusFace.SMILE,StatusFace.PARTY->arc(12f)
                StatusFace.HOPEFUL,StatusFace.BLANK,StatusFace.SLEEPY->drawOval(ink,p(10.5f,14f),Size(3f*unit,3.4f*unit),style=Stroke(1.25f*unit))
                StatusFace.CALM->line(10f,15f,14f,15f)
                else->arc(14f,true)
            }
            if(value in listOf(StatusFace.WORRIED,StatusFace.SAD,StatusFace.CRYING)) {line(6.5f,7f,9f,6f);line(15f,6f,17.5f,7f)}
            if(value==StatusFace.CRYING) {
                val tear=Path().apply {moveTo(18f*unit,11f*unit);cubicTo(17f*unit,13f*unit,15f*unit,16f*unit,18f*unit,17f*unit);cubicTo(21f*unit,16f*unit,19f*unit,13f*unit,18f*unit,11f*unit);close()}
                drawPath(tear,Color(0xFF5A9FC7))
            }
            if(value==StatusFace.HOPEFUL) {line(6f,5f,6f,7f);line(5f,6f,7f,6f)}
            if(value==StatusFace.PARTY) {drawCircle(Color(0xFFCA847C),1.2f*unit,p(3f,4f));drawCircle(Color(0xFF7798BA),1f*unit,p(21f,6f))}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusFaceSettings(value:StatusFaces,enabled:Boolean=true,onChange:(StatusFaces)->Unit) {
    var choosing by rememberSaveable {mutableStateOf<CheckStatus?>(null)}
    var draft by rememberSaveable {mutableStateOf(StatusFace.CRYING)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("打卡状态表情",Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
            Switch(value.enabled,{onChange(value.copy(enabled=it))},enabled=enabled,modifier=Modifier.testTag("status_faces_toggle"))
        }
        Text("仅改变这台设备的表情，状态文字始终保留。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(value.enabled) {
            CheckStatus.entries.forEach {status ->
                Surface(onClick={draft=value.face(status);choosing=status},enabled=enabled,
                    modifier=Modifier.fillMaxWidth().testTag("choose_face_${status.name}"),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(status.label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        SmallStatusFace(value.face(status));Spacer(Modifier.width(8.dp))
                        Text(value.face(status).label,style=MaterialTheme.typography.labelMedium)
                        Icon(Icons.Outlined.ChevronRight,null,Modifier.size(20.dp))
                    }
                }
            }
            TextButton(onClick={onChange(StatusFaces())},enabled=enabled,modifier=Modifier.testTag("reset_status_faces")) {Text("恢复默认表情")}
        }
    }
    choosing?.let {status ->
        ModalBottomSheet(onDismissRequest={choosing=null},containerColor=MaterialTheme.colorScheme.surface,
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("选择${status.label}的表情",style=MaterialTheme.typography.titleLarge)
                Text("表情可自由选择，状态文字保持不变",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(GridCells.Adaptive(68.dp),Modifier.fillMaxWidth().heightIn(max=300.dp).testTag("status_face_grid"),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(StatusFace.entries,key={it.name}) {face ->
                        Surface(Modifier.testTag("pick_face_${face.name}").selectable(draft==face,enabled=enabled,role=Role.RadioButton,onClick={draft=face}),
                            shape=RoundedCornerShape(12.dp),color=if(draft==face) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border=if(draft==face) BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null) {
                            Column(Modifier.padding(vertical=10.dp,horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                SmallStatusFace(face,Modifier.size(24.dp));Text(face.label,style=MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick={choosing=null}) {Text("取消")}
                    FilledTonalButton(onClick={onChange(value.select(status,draft));choosing=null},enabled=enabled,modifier=Modifier.testTag("confirm_status_face")) {Text("完成")}
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
