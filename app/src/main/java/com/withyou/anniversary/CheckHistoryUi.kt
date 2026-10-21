package com.withyou.anniversary

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
internal fun CheckTimeLabel(check:TodoCheck,today:LocalDate,modifier:Modifier=Modifier) {
    var detail by rememberSaveable(check.taskId,check.user,check.day.toString(),check.completedAt?.toString()) {mutableStateOf(false)}
    val motion=LocalMotionEnabled.current
    Surface(onClick={detail=!detail},modifier=modifier.testTag("check_time_${check.taskId}_${check.user}")
        .animateContentSize(tween(if(motion) 180 else 0)),shape=RoundedCornerShape(8.dp),color=androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.heightIn(min=48.dp).padding(horizontal=4.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Crossfade(detail,animationSpec=tween(if(motion) 150 else 0),label="时间详情",modifier=Modifier.weight(1f)) {expanded ->
                Text(if(expanded) CheckTimes.detail(check) else CheckTimes.short(check,today),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if(detail) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(detail) "收起详细时间" else "展开详细时间",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckHistorySheet(task:TodoTask,book:TodoBook,name:String,today:LocalDate,close:()->Unit) {
    val checks=book.checks.filter {it.taskId==task.id && it.done}.sortedByDescending(CheckTimes::sortTime)
    var notes by rememberSaveable(task.id) {mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*.82f).dp).padding(horizontal=20.dp).testTag("history_panel")) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("打卡记录",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(close) {Icon(Icons.Outlined.Close,"关闭打卡记录")}
            }
            Text(task.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(task.notes.isNotBlank()) TextButton(onClick={notes=!notes}) {Text(if(notes) "收起备注" else "查看备注")}
            if(task.kind==TodoKind.SHARED) Text("分别记录每个人的完成情况",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f,fill=false).testTag("check_history_list"),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(notes) item {Text(task.notes,style=MaterialTheme.typography.bodySmall)}
                if(checks.isEmpty()) item {Text("还没有打卡记录，完成一次小事就会留在这里",Modifier.padding(vertical=24.dp),style=MaterialTheme.typography.bodyMedium)}
                checks.groupBy {CheckTimes.date(it)}.forEach {(date,group) ->
                    item(key="date_$date") {Text(date?.let {if(it==today) "今天" else if(it==today.minusDays(1)) "昨天" else it.label()} ?: "日期未记录",Modifier.padding(top=14.dp,bottom=4.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
                    items(group,key={"${it.taskId}_${it.user}_${it.day}"}) {check ->
                        Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    if(LocalStatusFaces.current.enabled) SmallStatusFace(LocalStatusFaces.current.done)
                                    Text(if(check.user==book.uid) name else book.partnerName,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                                    Text("已打卡",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                                }
                                CheckTimeLabel(check,today)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompletedHeader(count:Int,expanded:Boolean,toggle:()->Unit) {
    Surface(onClick=toggle,shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant,
        modifier=Modifier.fillMaxWidth().testTag("completed_group_toggle")) {
        Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Inventory2,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
            Text("已完成 · $count 项",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded) "收起已完成事项" else "展开已完成事项")
        }
    }
}

@Composable
internal fun CompletedTodoRow(task:TodoTask,book:TodoBook,name:String,today:LocalDate,busy:Boolean,
    history:()->Unit,undo:()->Unit,edit:()->Unit,delete:()->Unit,modifier:Modifier=Modifier) {
    var menu by remember {mutableStateOf(false)}
    val check=CheckTimes.displayCheck(task,book,today) ?: return
    Surface(modifier.fillMaxWidth().testTag("collected_${task.id}"),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal=12.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                if(LocalStatusFaces.current.enabled) SmallStatusFace(LocalStatusFaces.current.done)
                Column(Modifier.weight(1f)) {
                    Text(task.title,style=MaterialTheme.typography.bodyMedium)
                    Text(if(book.pairId.isBlank()) "已打卡" else (if(task.owner==book.uid) name else book.partnerName)+" · 已打卡",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick={menu=true}) {Icon(Icons.Outlined.MoreHoriz,"已完成事项操作：${task.title}")}
                    DropdownMenu(menu,{menu=false},containerColor=MaterialTheme.colorScheme.surface) {
                        DropdownMenuItem(text={Text("查看打卡记录")},onClick={menu=false;history()})
                        if(task.owner==book.uid) {
                            DropdownMenuItem(text={Text("撤销完成")},onClick={menu=false;undo()},enabled=!busy)
                            DropdownMenuItem(text={Text("编辑事项")},onClick={menu=false;edit()},enabled=!busy)
                            DropdownMenuItem(text={Text("删除事项",color=MaterialTheme.colorScheme.error)},onClick={menu=false;delete()},enabled=!busy)
                        }
                    }
                }
            }
            CheckTimeLabel(check,today)
        }
    }
}
