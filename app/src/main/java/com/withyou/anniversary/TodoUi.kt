package com.withyou.anniversary

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun TodoScreen(vm:TodoViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable {mutableStateOf("list")}
    var editId by rememberSaveable {mutableStateOf<String?>(null)}
    var date by remember {mutableStateOf(TodoRules.today())}
    val owner=LocalLifecycleOwner.current
    LaunchedEffect(owner,state.session?.uid,page) {
        if(state.session!=null && page!="editor") owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.sync(false)
            while(true) {delay(5000);date=TodoRules.today();vm.sync(false)}
        }
    }
    LaunchedEffect(state.busy,state.book?.pending?.size,page) {if(!state.busy && page!="editor") vm.sync(false)}
    BackHandler(page!="list") {page="list"}
    if(!state.ready) {Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {CircularProgressIndicator()};return}
    if(state.session==null) {TodoLogin(state,vm);return}
    val book=state.book ?: return
    if(page=="editor") {
        TodoEditor(book.tasks.find {it.id==editId},book,state.busy,state.message,{page="list"}) {vm.saveTask(it) {page="list"}}
        return
    }
    if(page=="account") {TodoAccount(state,vm) {page="list"};return}
    var historyId by rememberSaveable {mutableStateOf<String?>(null)}
    var actionTask by remember {mutableStateOf<TodoTask?>(null)}
    var confirmDelete by remember {mutableStateOf<TodoTask?>(null)}
    LazyColumn(Modifier.fillMaxSize().testTag("todo_list"),contentPadding=PaddingValues(horizontal=16.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {Text("把小事，一起坚持",fontSize=25.sp,fontWeight=FontWeight.SemiBold);Text("${date.monthValue} 月 ${date.dayOfMonth} 日 · 上海时间",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(onClick={page="account"}) {Icon(Icons.Outlined.PeopleOutline,"账号与伙伴")}
            }
        }
        item {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(state.syncLabel,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            TextButton(onClick={vm.sync(true)},enabled=!state.busy) {Text("刷新")}
            FilledTonalButton(onClick={editId=null;page="editor"},enabled=!state.busy && !state.failed) {Text("新增事项")}
        }}
        if(state.busy) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(state.message.isNotBlank()) item {Text(state.message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
        if(book.conflicts.isNotEmpty()) item {Card {Column(Modifier.padding(16.dp)) {
            Text("有些操作需要你确认",fontWeight=FontWeight.SemiBold)
            book.conflicts.forEach {Text(it,Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall)}
            TextButton(onClick=vm::acknowledge,enabled=!state.busy) {Text("我已查看")}
        }}}
        item {TodoProgressPanel(book,state.session!!.name,date,state.busy || book.pending.isNotEmpty() || book.shareJobs.isNotEmpty())}
        val tasks=book.tasks.filter {!it.deleted && !it.archived}
            .sortedWith(compareBy<TodoTask> {!TodoRules.scheduled(it,date)}.thenBy {TodoRules.complete(book,it,it.owner,date)}.thenBy {it.due ?: LocalDate.MAX}.thenBy {it.title})
        if(tasks.isEmpty()) item {Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Spa,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
            Text("从一件小事开始",fontWeight=FontWeight.SemiBold,fontSize=21.sp)
            Text(if(book.pairId.isBlank()) "写下一个小目标。邀请伙伴后，让彼此看见每天的进步。" else "写下一件想做的小事，与伙伴分享每天的一点进步。")
            TextButton(onClick={editId=null;page="editor"},enabled=!state.busy) {Text("写下第一件小事")}
        }}}
        items(tasks,key={it.id}) {task ->
            CompactTodoCard(task,book,state.session!!.name,date,state.busy || state.failed,
                actionTask?.id==task.id,{actionTask=task},{actionTask=null},
                {vm.toggle(task,date)},{historyId=task.id;actionTask=null},
                {editId=task.id;page="editor";actionTask=null},
                {vm.saveTask(task.copy(archived=true)) {};actionTask=null},
                {confirmDelete=task;actionTask=null},
                if(LocalMotionEnabled.current) Modifier.animateItem() else Modifier)
        }
        item {Text("断网时先保存到本机，联网后再同步。历史打卡按上海日期记录。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    TodoTogetherCelebration(book,date,state.syncLabel.startsWith("已同步") && !state.busy)
    confirmDelete?.let {task->AlertDialog(onDismissRequest={confirmDelete=null},title={Text("删除这个事项？")},text={Text("删除会同步给伙伴，历史记录不再显示。此操作不可在 App 内恢复。")},confirmButton={TextButton(onClick={vm.saveTask(task.copy(deleted=true)) {};confirmDelete=null}) {Text("确认删除")}},dismissButton={TextButton(onClick={confirmDelete=null}) {Text("取消")}})}
    historyId?.let {id->
        val checks=book.checks.filter {it.taskId==id && it.done}.sortedByDescending {it.day}
        AlertDialog(onDismissRequest={historyId=null},title={Text("打卡记录")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
            book.tasks.find {it.id==id}?.let {task->
                Text(task.title,fontWeight=FontWeight.SemiBold)
                if(task.notes.isNotBlank()) Text(task.notes,Modifier.padding(vertical=8.dp))
            }
            if(checks.isEmpty()) Text("还没有完成记录")
            checks.forEach {Text((if(it.day==LocalDate.of(1970,1,1)) "已完成待办" else it.day.label())+" · "+if(it.user==book.uid) state.session!!.name else book.partnerName,Modifier.padding(vertical=6.dp))}
        }},confirmButton={TextButton(onClick={historyId=null}) {Text("关闭")}})
    }
}

@Composable
private fun TodoLogin(state:TodoState,vm:TodoViewModel) {
    var register by rememberSaveable {mutableStateOf(false)}
    var username by rememberSaveable {mutableStateOf("")}
    var password by remember {mutableStateOf("")}
    var nickname by rememberSaveable {mutableStateOf("")}
    var code by remember {mutableStateOf("")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(16.dp));Icon(Icons.Outlined.CheckCircleOutline,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary)
        Text("把小事，一起坚持",fontSize=28.sp,fontWeight=FontWeight.SemiBold)
        Text("登录后收藏自己的待办，也能和伙伴分享每天的一点进步。")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(!register,{register=false},label={Text("登录")});FilterChip(register,{register=true},label={Text("注册")})}
        OutlinedTextField(username,{username=it},label={Text("用户名")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("密码")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,modifier=Modifier.fillMaxWidth())
        if(register) {
            OutlinedTextField(nickname,{nickname=it},label={Text("昵称")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(code,{code=it},label={Text("注册激活码")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("第一版为受邀双人使用，由应用管理员提供注册激活码，无需短信。密码 8～32 位，包含大小写字母、数字、符号中的至少三类。",style=MaterialTheme.typography.bodySmall)
        }
        if(state.message.isNotBlank()) Text(state.message,color=MaterialTheme.colorScheme.primary)
        Button(onClick={if(register) vm.register(username,password,nickname,code) else vm.login(username,password)},enabled=!state.busy && !state.failed,modifier=Modifier.fillMaxWidth()) {Text(if(state.busy) "请稍候…" else if(register) "创建账号" else "登录打卡")}
        Text("待办、打卡和配对资料同步到腾讯 CloudBase；纪念日和照片仅在你主动分享后上传。忘记密码可联系应用管理员，不在聊天中发送密码。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodoEditor(existing:TodoTask?,book:TodoBook,busy:Boolean,message:String,back:()->Unit,save:(TodoTask)->Unit) {
    val context=LocalContext.current
    var title by rememberSaveable(existing?.id) {mutableStateOf(existing?.title ?: "")}
    var notes by rememberSaveable(existing?.id) {mutableStateOf(existing?.notes ?: "")}
    var kind by rememberSaveable(existing?.id) {mutableStateOf(existing?.kind ?: TodoKind.TODO)}
    val initialPair=rememberSaveable(existing?.id) {book.pairId}
    var shared by rememberSaveable(existing?.id) {mutableStateOf(existing?.shared ?: book.pairId.isNotBlank())}
    var days by rememberSaveable(existing?.id) {mutableStateOf(existing?.weekdays?.sorted() ?: (1..7).toList())}
    var due by rememberSaveable(existing?.id) {mutableStateOf(existing?.due?.toString() ?: "")}
    var archived by rememberSaveable(existing?.id) {mutableStateOf(existing?.archived ?: false)}
    var advanced by rememberSaveable {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    val id=rememberSaveable(existing?.id) {existing?.id ?: java.util.UUID.randomUUID().toString()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        IconButton(onClick=back,enabled=!busy) {Icon(Icons.Outlined.ArrowBack,"返回打卡列表")}
        Text(if(existing==null) "写下一件小事" else "编辑事项",fontSize=27.sp,fontWeight=FontWeight.SemiBold)
        OutlinedTextField(title,{if(it.length<=80) title=it},label={Text("事项标题")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(notes,{if(it.length<=1000) notes=it},label={Text("备注（可选）")},modifier=Modifier.fillMaxWidth(),minLines=2)
        Text("事项类型",fontWeight=FontWeight.SemiBold)
        TodoKind.entries.forEach {value->Row(Modifier.fillMaxWidth().selectable(selected=kind==value,enabled=existing==null && (value!=TodoKind.SHARED || book.pairId.isNotBlank()),onClick={kind=value;if(value==TodoKind.SHARED) shared=true}),verticalAlignment=Alignment.CenterVertically) {
            RadioButton(selected=kind==value,onClick=null,enabled=existing==null && (value!=TodoKind.SHARED || book.pairId.isNotBlank()))
            Text(value.label)
        }}
        if(existing!=null) Text("类型创建后固定，避免改变已有打卡记录。",style=MaterialTheme.typography.bodySmall)
        if(kind==TodoKind.TODO) {
            OutlinedButton(onClick={val date=due.takeIf {it.isNotBlank()}?.let(LocalDate::parse) ?: TodoRules.today();DatePickerDialog(context,{_,y,m,d->due=LocalDate.of(y,m+1,d).toString()},date.year,date.monthValue-1,date.dayOfMonth).show()}) {Text(if(due.isBlank()) "选择截止日期（可选）" else "截止 $due")}
            if(due.isNotBlank()) TextButton(onClick={due=""}) {Text("清除截止日期")}
        } else {
            Text("每周打卡日")
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {(1..7).forEach {n->FilterChip(n in days,{days=if(n in days) days-n else days+n},label={Text("一二三四五六日"[n-1].toString())})}}
        }
        Text(if(shared) "♡ 这件小事默认让伙伴看见" else "仅自己可见",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        TextButton(onClick={advanced=!advanced}) {Text(if(advanced) "收起隐私设置" else "隐私设置")}
        if(advanced) Row(verticalAlignment=Alignment.CenterVertically) {Text("向伙伴共享",Modifier.weight(1f));Switch(shared,onCheckedChange={shared=it},enabled=kind!=TodoKind.SHARED && (book.pairId.isNotBlank() || shared))}
        if(book.pairId.isBlank()) Text("未配对时可创建私人事项。共享事项需要先邀请伙伴。",style=MaterialTheme.typography.bodySmall)
        if(existing!=null) Row(verticalAlignment=Alignment.CenterVertically) {Text("归档",Modifier.weight(1f));Switch(archived,{archived=it})}
        if(error.isNotBlank() || message.isNotBlank()) Text(error.ifBlank {message},color=MaterialTheme.colorScheme.primary)
        Button(onClick={try {
            require(!shared || book.pairId==initialPair) {"伙伴关系已变化，请返回列表重新确认共享范围"}
            val task=TodoTask(id,book.uid,title.trim(),kind,notes.trim(),due.takeIf {it.isNotBlank()}?.let(LocalDate::parse),days.toSet(),shared || kind==TodoKind.SHARED,if(shared || kind==TodoKind.SHARED) existing?.pairId?.takeIf {it.isNotBlank()} ?: book.pairId else "",archived,existing?.deleted ?: false,existing?.revision ?: 0)
            TodoRules.validate(task);save(task)
        } catch(e:Exception) {error=e.message ?: "请检查输入"}},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("保存事项")}
    }
}

@Composable
private fun TodoAccount(state:TodoState,vm:TodoViewModel,back:()->Unit) {
    val book=state.book ?: return
    var invite by rememberSaveable {mutableStateOf("")}
    var unlink by remember {mutableStateOf(false)}
    var logout by remember {mutableStateOf(false)}
    var archived by remember {mutableStateOf(false)}
    val clipboard=LocalClipboardManager.current
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->uri?.let(vm::export)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        IconButton(onClick=back) {Icon(Icons.Outlined.ArrowBack,"返回打卡列表")}
        Text("账号与伙伴",fontSize=27.sp,fontWeight=FontWeight.SemiBold)
        Text("你好，${state.session?.name}",style=MaterialTheme.typography.titleLarge)
        Text(state.syncLabel,style=MaterialTheme.typography.bodySmall)
        if(state.message.isNotBlank()) Text(state.message,color=MaterialTheme.colorScheme.primary)
        if(book.pairId.isNotBlank()) {
            Text("你的伙伴：${book.partnerName}")
            OutlinedButton(onClick={unlink=true},enabled=!state.busy) {Text("解除配对")}
        } else {
            Text("各自完成，也彼此看见",style=MaterialTheme.typography.titleMedium)
            Button(onClick={vm.pair("create")},enabled=!state.busy) {Text("生成配对邀请码")}
            if(state.invitationCode.isNotBlank()) {
                Text(state.invitationCode,style=MaterialTheme.typography.bodyLarge)
                TextButton(onClick={clipboard.setText(AnnotatedString(state.invitationCode))}) {Text("复制邀请码")}
            }
            OutlinedTextField(invite,{invite=it},label={Text("伙伴的邀请码")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedButton(onClick={vm.pair("join",invite.trim())},enabled=!state.busy && invite.isNotBlank()) {Text("申请配对")}
            book.invites.forEach {pending ->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {
                Text(if(pending.candidate.isBlank()) "邀请码等待使用，10 分钟内有效" else if(pending.owner==book.uid) "${pending.name} 请求与你配对" else "等待 ${pending.name} 确认配对")
                if(pending.owner==book.uid && pending.candidate.isNotBlank()) Button(onClick={vm.pair("confirm",pending.id)},enabled=!state.busy) {Text("确认成为伙伴")}
                TextButton(onClick={vm.pair("cancel",pending.id)},enabled=!state.busy) {Text("取消申请")}
            }}}
        }
        HorizontalDivider()
        Text("妥善保存日常",fontWeight=FontWeight.SemiBold)
        OutlinedButton(onClick={export.launch("与你-打卡-${TodoRules.today()}.json")},enabled=!state.busy) {Text("导出打卡数据")}
        Text("导出包含当前可见的事项、打卡、共享纪念日文字与待同步操作，不含照片文件、密码或登录令牌。请妥善保管，已导出的内容无法远程撤回。个人纪念日完整备份仍在原设置页。",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={archived=!archived}) {Text(if(archived) "收起归档" else "查看归档事项")}
        if(archived) book.tasks.filter {it.owner==book.uid && it.archived && !it.deleted}.forEach {task->Row(verticalAlignment=Alignment.CenterVertically) {Text(task.title,Modifier.weight(1f));TextButton(onClick={vm.saveTask(task.copy(archived=false)) {}},enabled=!state.busy) {Text("取消归档")}}}
        Text("云端使用免费体验环境，有期限和额度限制。服务到期或网络不可用时，本机副本仍可查看和记录；共享变化需恢复服务后同步。",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={logout=true},enabled=!state.busy) {Text("退出账号")}
    }
    if(unlink) AlertDialog(onDismissRequest={unlink=false},title={Text("解除与伙伴的配对？")},text={Text("双方将停止互相访问共享事项，本机会清理对方的缓存。自己的事项保留，旧共同事项停止打卡。已被看过或截图的内容无法撤回。")},confirmButton={TextButton(onClick={unlink=false;vm.pair("unlink",book.pairId)}) {Text("确认解除")}},dismissButton={TextButton(onClick={unlink=false}) {Text("取消")}})
    if(logout) AlertDialog(onDismissRequest={logout=false},title={Text("退出打卡账号？")},text={Text("待同步的个人记录会按账号保留，再次登录同一账号后继续处理。其他账号不会看到这些数据。")},confirmButton={TextButton(onClick={logout=false;vm.logout()}) {Text("退出账号")}},dismissButton={TextButton(onClick={logout=false}) {Text("取消")}})
}
