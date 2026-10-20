package com.withyou.anniversary

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
internal fun TodoProgressPanel(book:TodoBook,name:String,date:LocalDate,pending:Boolean) {
    Surface(modifier=Modifier.testTag("todo_progress_panel"),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text("今日进度",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleSmall)
            PersonProgress(name.ifBlank {"自己"},TodoRules.progress(book,book.uid,date),"self")
            if(book.pairId.isNotBlank() && book.partnerId.isNotBlank()) PersonProgress(book.partnerName.ifBlank {"伙伴"},TodoRules.progress(book,book.partnerId,date),"partner")
            val stamp=if(book.syncedAt==0L) "尚未同步" else "同步于 "+DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(TodoRules.zone).format(Instant.ofEpochMilli(book.syncedAt))
            Text(if(book.pairId.isNotBlank()) {if(pending) "本机变化待同步 · 伙伴进度可能延迟" else "$stamp · 伙伴仅含共享事项"} else if(book.uid==LOCAL_TODO_UID) "仅保存在这台设备" else if(pending) "本机变化待同步" else stamp,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonProgress(name:String,progress:TodoRules.Progress,key:String) {
    val value by animateFloatAsState(progress.fraction,tween(if(LocalMotionEnabled.current) 450 else 0),label="今日完成进度")
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("progress_row_$key")) {
        val stacked=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.7f || maxWidth<280.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=30.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.primary) {
                    Box(Modifier.size(24.dp),contentAlignment=Alignment.Center) {Text(String(Character.toChars(name.codePointAt(0))),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)}
                }
                Text(name,Modifier.weight(1f).testTag("progress_name_$key"),style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(!stacked) LinearProgressIndicator(progress={value},modifier=Modifier.weight(.7f).height(4.dp).testTag("progress_bar_$key"),trackColor=MaterialTheme.colorScheme.surface,drawStopIndicator={})
                Text(if(progress.total==0 && progress.unknown>0) "待同步" else if(progress.total==0) "暂无计划" else "${progress.done} / ${progress.total}",
                    Modifier.widthIn(min=42.dp,max=108.dp).testTag("progress_count_$key"),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.End)
            }
            if(stacked) LinearProgressIndicator(progress={value},modifier=Modifier.fillMaxWidth().height(4.dp).testTag("progress_bar_$key"),trackColor=MaterialTheme.colorScheme.surface,drawStopIndicator={})
            if(progress.unknown>0) Text("${progress.unknown} 项旧版完成日期待同步，暂不计入",style=MaterialTheme.typography.bodySmall)
        }
    }
}

/** Initialize from the first confirmed snapshot. Reopening a page never replays old celebrations. */
@Composable
internal fun TodoTogetherCelebration(book:TodoBook,date:LocalDate,confirmed:Boolean) {
    if(book.pairId.isBlank()) return
    val context=LocalContext.current
    val ledger=remember {context.getSharedPreferences("todo_celebrations",Context.MODE_PRIVATE)}
    val motion=LocalMotionEnabled.current
    var previous by remember(book.uid,book.pairId,date) {mutableStateOf<Set<String>?>(null)}
    var show by remember {mutableStateOf(false)}
    val completed=book.tasks.filter {TodoRules.bothComplete(book,it,date)}.map {it.id}.toSet()
    LaunchedEffect(completed,confirmed,book.pending.isEmpty()) {
        if(!confirmed || book.pending.isNotEmpty()) return@LaunchedEffect
        val newly=completed-(previous ?: completed)
        var celebrate=false
        completed.forEach {id->
            val key="${book.uid}:${book.pairId}:$id:$date"
            if(!ledger.contains(key)) {ledger.edit().putBoolean(key,true).apply();if(id in newly) celebrate=true}
        }
        previous=completed
        if(celebrate && motion) {show=true;delay(2400);show=false}
    }
    Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.TopCenter) {
        AnimatedVisibility(show,enter=fadeIn(tween(if(motion) 250 else 0)),exit=fadeOut(tween(if(motion) 200 else 0))) {
            Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer,shadowElevation=6.dp) {
                Text("♡ 今天的小约定，一起完成了",Modifier.padding(20.dp),fontWeight=FontWeight.SemiBold)
            }
        }
    }
}
