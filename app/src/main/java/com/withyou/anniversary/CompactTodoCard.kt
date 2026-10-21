package com.withyou.anniversary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** A short title and a useful second row; touch targets remain at least 48dp. */
@Composable
internal fun CompactTodoCard(task:TodoTask,book:TodoBook,name:String,date:LocalDate,busy:Boolean,
    menuOpen:Boolean,openMenu:()->Unit,closeMenu:()->Unit,toggle:()->Unit,history:()->Unit,
    edit:()->Unit,archive:()->Unit,delete:()->Unit,modifier:Modifier=Modifier) {
    val done=TodoRules.complete(book,task,book.uid,date)
    val partnerDone=TodoRules.complete(book,task,book.partnerId,date)
    val both=TodoRules.bothComplete(book,task,date)
    val mine=task.owner==book.uid
    val status=checkStatus(task,book,if(task.kind==TodoKind.SHARED) book.uid else task.owner,date)
    val faces=LocalStatusFaces.current
    val ink=MaterialTheme.colorScheme
    val color by animateColorAsState(if(done && (mine || task.kind==TodoKind.SHARED) || both) ink.secondaryContainer else ink.surfaceVariant,
        tween(if(LocalMotionEnabled.current) 220 else 0),label="打卡完成")
    Card(modifier.fillMaxWidth().testTag("todo_card_${task.id}"),colors=CardDefaults.cardColors(containerColor=color,contentColor=ink.onSurface),shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(task.title,Modifier.weight(1f),fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold)
                Box {
                    IconButton(onClick=openMenu,modifier=Modifier.size(48.dp)) {Icon(Icons.Outlined.MoreHoriz,"事项操作：${task.title}",Modifier.size(21.dp))}
                    DropdownMenu(expanded=menuOpen,onDismissRequest=closeMenu,
                        modifier=Modifier.widthIn(min=184.dp,max=248.dp).heightIn(max=320.dp).testTag("todo_action_menu"),
                        shape=RoundedCornerShape(16.dp),containerColor=ink.surface) {
                        DropdownMenuItem(text={Text("查看打卡记录")},onClick=history,leadingIcon={Icon(Icons.Outlined.History,null)})
                        if(mine) {
                            DropdownMenuItem(text={Text("编辑事项")},onClick=edit,enabled=!busy,leadingIcon={Icon(Icons.Outlined.Edit,null)})
                            DropdownMenuItem(text={Text("归档事项")},onClick=archive,enabled=!busy,leadingIcon={Icon(Icons.Outlined.Archive,null)})
                            DropdownMenuItem(text={Text("删除事项",color=ink.error)},onClick=delete,enabled=!busy,leadingIcon={Icon(Icons.Outlined.DeleteOutline,null,tint=ink.error)})
                        }
                    }
                }
            }
            if(task.notes.isNotBlank()) Text(task.notes,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis,color=ink.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(book.pairId.isNotBlank()) Surface(shape=RoundedCornerShape(7.dp),color=ink.primaryContainer,contentColor=ink.primary) {
                        Text(if(task.kind==TodoKind.SHARED) "共同事项" else if(mine) name else book.partnerName,
                            Modifier.padding(horizontal=7.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        if(faces.enabled && status!=null) SmallStatusFace(faces.face(status))
                        val summary=taskSummary(task,date,TodoRules.complete(book,task,task.owner,date),book.pairId.isNotBlank())
                        Text(if(status==CheckStatus.OVERDUE) summary else (status?.label?.plus(" · ") ?: "")+summary,
                            modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=ink.onSurfaceVariant)
                    }
                }
                if(mine || task.kind==TodoKind.SHARED) {
                    val interaction=remember(task.id) {MutableInteractionSource()}
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(if(pressed) .975f else 1f,tween(if(LocalMotionEnabled.current) 110 else 0),label="打卡轻按")
                    FilledTonalButton(onClick=toggle,enabled=!busy && TodoRules.canCheck(task,book.uid,book.pairId,date),
                        contentPadding=PaddingValues(horizontal=12.dp,vertical=6.dp),interactionSource=interaction,
                        modifier=Modifier.heightIn(min=48.dp).graphicsLayer {scaleX=scale;scaleY=scale}) {
                        Icon(if(done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if(!TodoRules.scheduled(task,date)) "今天休息" else if(task.shared && task.pairId!=book.pairId) "配对已结束" else if(done) "已完成" else "完成打卡",style=MaterialTheme.typography.labelLarge)
                    }
                } else Text(if(partnerDone) "已完成" else "未完成",style=MaterialTheme.typography.labelMedium,color=ink.primary)
            }
            if(task.kind==TodoKind.SHARED) {
                Text(if(both) "双方都完成了 ♡" else "$name ${if(done) "✓" else "待完成"} · ${book.partnerName} ${if(partnerDone) "✓" else "待完成"}",
                    style=MaterialTheme.typography.bodySmall,color=ink.onSurfaceVariant)
            }
            CheckTimes.displayCheck(task,book,date)?.let {CheckTimeLabel(it,date)}
        }
    }
}

private fun taskSummary(task:TodoTask,date:LocalDate,done:Boolean,paired:Boolean):String {
    val schedule=if(task.kind==TodoKind.TODO) task.due?.let {
        val label=if(it.year==date.year) "${it.monthValue}月${it.dayOfMonth}日" else "${it.year}.${it.monthValue}.${it.dayOfMonth}"
        if(it<date && !done) "已逾期 · $label" else "$label 到期"
    } ?: "待办" else when(task.weekdays) {
        (1..7).toSet()->"每天"
        (1..5).toSet()->"周一至周五"
        setOf(6,7)->"周末"
        else->"周"+task.weekdays.sorted().joinToString("、") {"一二三四五六日"[it-1].toString()}
    }
    return schedule+if(paired && !task.shared) " · 仅自己" else ""
}
