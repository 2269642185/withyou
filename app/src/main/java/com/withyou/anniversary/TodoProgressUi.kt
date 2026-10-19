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
import androidx.compose.ui.platform.LocalConfiguration
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
    Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("今天，也在慢慢变好",fontWeight=FontWeight.SemiBold,fontSize=19.sp)
            val large=LocalConfiguration.current.fontScale>1.3f || LocalConfiguration.current.screenWidthDp<350
            if(large) {
                PersonProgress("我 · $name",TodoRules.progress(book,book.uid,date),Modifier.fillMaxWidth())
                if(book.partnerId.isNotBlank()) PersonProgress("伙伴 · ${book.partnerName}",TodoRules.progress(book,book.partnerId,date),Modifier.fillMaxWidth())
            } else Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                PersonProgress("我 · $name",TodoRules.progress(book,book.uid,date),Modifier.weight(1f))
                if(book.partnerId.isNotBlank()) PersonProgress("伙伴 · ${book.partnerName}",TodoRules.progress(book,book.partnerId,date),Modifier.weight(1f))
            }
            val stamp=if(book.syncedAt==0L) "尚未同步" else "上次同步 "+DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(TodoRules.zone).format(Instant.ofEpochMilli(book.syncedAt))
            Text(if(pending) "本机进度 · 待同步，伙伴进度可能尚未更新" else "$stamp · 伙伴进度仅统计共享事项",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonProgress(name:String,progress:TodoRules.Progress,modifier:Modifier) {
    val value by animateFloatAsState(progress.fraction,tween(if(LocalMotionEnabled.current) 450 else 0),label="今日完成进度")
    Column(modifier,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.primary) {
                Box(Modifier.size(32.dp),contentAlignment=Alignment.Center) {Text(name.substringAfter(" · ").take(1),fontWeight=FontWeight.Bold)}
            }
            Text(name,style=MaterialTheme.typography.labelLarge)
        }
        Text(if(progress.total==0 && progress.unknown>0) "完成日期待同步" else if(progress.total==0) "今天还没有计划" else "${progress.done} / ${progress.total} 已完成",fontWeight=FontWeight.SemiBold)
        if(progress.unknown>0) Text("${progress.unknown} 项旧版完成日期需联网补齐，暂不计入",style=MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(progress={value},modifier=Modifier.fillMaxWidth().height(6.dp),trackColor=MaterialTheme.colorScheme.surface)
    }
}

/** Initialize from the first confirmed snapshot. Reopening a page never replays old celebrations. */
@Composable
internal fun TodoTogetherCelebration(book:TodoBook,date:LocalDate,confirmed:Boolean) {
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
