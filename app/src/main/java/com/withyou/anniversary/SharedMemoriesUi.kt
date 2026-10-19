package com.withyou.anniversary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
internal fun MemoryShareAction(record:MemoryDay,vm:TodoViewModel,manage:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val book=state.book
    val current=book?.memories?.find {it.id==record.id && it.owner==book.uid && it.active}
    val job=book?.shareJobs?.find {it.id==record.id}
    val shared=job?.active ?: (current!=null)
    var confirm by remember {mutableStateOf(false)}
    var targetPair by remember {mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={if(book==null || book.pairId.isBlank()) manage() else {targetPair=book.pairId;confirm=true}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
            Icon(if(shared) Icons.Outlined.PeopleOutline else Icons.Outlined.FavoriteBorder,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
            Text(if(job!=null) "共享操作待同步" else if(shared) "已与伙伴共享 · 管理" else "分享这份纪念")
        }
        if(job!=null || state.message.isNotBlank()) Text(state.message.ifBlank {"联网后会同步最新版本，包括照片和配色。"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(if(shared) "停止共享这份纪念？" else "把这份纪念分享给伙伴？")},text={Text(if(shared) "联网同步后，对方将无法继续访问这份纪念和照片。对方离线时已缓存的内容会在重新联网后清理，截图和导出无法撤回。" else "将向 ${book?.partnerName ?: "当前伙伴"} 分享标题、昵称、日期、计数规则，以及完整模板、背景照片、遮罩和配色。之后在本机编辑时也会同步。其他纪念日保持私密。双方按上海日期计算共享日子。")},confirmButton={TextButton(onClick={confirm=false;vm.shareMemory(record,!shared,expectedPair=targetPair)},enabled=!state.busy) {Text(if(shared) "停止共享" else "确认分享")}},dismissButton={TextButton(onClick={confirm=false}) {Text("取消")}})
}

@Composable
internal fun SharedMemoriesScreen(vm:TodoViewModel,local:List<MemoryDay>,back:()->Unit,account:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val book=state.book
    var choose by rememberSaveable {mutableStateOf(false)}
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var today by remember {mutableStateOf(TodoRules.today())}
    LaunchedEffect(Unit) {while(true) {today=TodoRules.today();kotlinx.coroutines.delay(1000)}}
    val visible=book?.memories?.filter {it.active && (it.owner==book.uid || it.pairId==book.pairId && book.pairId.isNotBlank())} ?: emptyList()
    val item=visible.find {it.owner+":"+it.id==selected}
    if(item!=null && book!=null) {
        val photo=item.photoPath.takeIf {it.isNotBlank() && vm.sharedPhotos.file(book.uid,it).exists()}
        val record=item.record.copy(photo=photo?.let(vm.sharedPhotos::name))
        LazyColumn(Modifier.fillMaxSize().testTag("shared_memories_list"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            item {TextButton(onClick={selected=null}) {Icon(Icons.Outlined.ArrowBack,null);Text("返回共享纪念日")}}
            item {Text(if(item.owner==book.uid) "我分享的纪念" else "${book.partnerName} 分享的纪念",fontSize=26.sp,fontWeight=FontWeight.SemiBold)}
            item {MemoryCard(record,today,vm.sharedPhotos.directory(book.uid),true) {}}
            item {InfoCard("${DateRules.untilAnniversary(record.date,today)} 天后 · 下一个年度纪念日",DateRules.nextAnniversary(record.date,today).label(),Icons.Outlined.Event)}
            item {Text("起始日期：${record.date.label()}\n${record.mode.label}\n共享日子统一按上海日期计算，2 月 29 日在非闰年以 2 月 28 日作为周年日。",style=MaterialTheme.typography.bodyMedium)}
            if(photo==null && item.photoPath.isNotBlank()) item {Text("照片暂不可用，联网后会重新加载。",style=MaterialTheme.typography.bodySmall)}
            if(item.owner==book.uid) item {MemoryShareAction(local.find {it.id==item.id} ?: record.copy(photo=null),vm,account)}
            else item {Text("这是伙伴的纪念日，可查看，编辑由创建者完成。",style=MaterialTheme.typography.bodySmall)}
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("shared_memories_list"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {TextButton(onClick=back) {Icon(Icons.Outlined.ArrowBack,null);Text("返回纪念日")}}
        item {Text("我们的共享",fontSize=28.sp,fontWeight=FontWeight.SemiBold);Text("把珍贵的日子，也放进彼此心里。",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(book==null || book.pairId.isBlank()) item {
            InfoCard("先与伙伴相连","在打卡页登录并邀请伙伴。现有纪念日和照片不会自动上传。",Icons.Outlined.PeopleOutline)
            Button(onClick=account) {Text("前往账号与伙伴")}
        }
        if(book!=null) {
            item {Row {Text(state.syncLabel,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.sync(true)},enabled=!state.busy) {Text("刷新")}}}
            if(state.message.isNotBlank()) item {Text(state.message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
            if(book.shareJobs.isNotEmpty()) item {Text("${book.shareJobs.size} 份共享修改待同步 · 伙伴看到的可能仍是旧版本",style=MaterialTheme.typography.bodySmall)}
            if(book.pairId.isNotBlank()) item {Button(onClick={choose=true},enabled=!state.busy) {Text("选择一份纪念分享")}}
        }
        if(visible.isEmpty()) item {InfoCard("让回忆，走近一点","选择一份已有纪念日，连同你设置的照片、模板和颜色一起分享。",Icons.Outlined.FavoriteBorder)}
        items(visible,key={it.owner+":"+it.id}) {shared->
            val photo=shared.photoPath.takeIf {it.isNotBlank() && vm.sharedPhotos.file(book!!.uid,it).exists()}
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(shared.owner==book!!.uid) if(shared.pairId==book.pairId) "我分享的" else "我分享的 · 原配对已结束" else "伙伴 · ${book.partnerName}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                MemoryCard(shared.record.copy(photo=photo?.let(vm.sharedPhotos::name)),today,vm.sharedPhotos.directory(book.uid),false) {selected=shared.owner+":"+shared.id}
                if(photo==null && shared.photoPath.isNotBlank()) Text("照片等待加载，已显示安全底色。",style=MaterialTheme.typography.bodySmall)
            }
        }
        item {Text("共享按上海日历日期计算。离线可查看已有副本；停止共享或解除配对需要联网后才能更新到另一台设备。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    if(choose) AlertDialog(onDismissRequest={choose=false},title={Text("选择要分享的纪念日")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
        if(local.isEmpty()) Text("先创建一份纪念日，再来分享。")
        local.forEach {r->TextButton(onClick={selected=null;choose=false;vm.shareMemory(r)},enabled=!state.busy) {Text(r.title)}}
        Text("选择后会上传这份纪念的照片、模板及配色，仅供当前伙伴查看。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={choose=false}) {Text("取消")}})
}
