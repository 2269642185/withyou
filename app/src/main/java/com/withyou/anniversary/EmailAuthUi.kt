package com.withyou.anniversary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
private fun SecretField(value:String,onChange:(String)->Unit,label:String,enabled:Boolean) {
    var visible by remember {mutableStateOf(false)}
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth(),
        visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
        trailingIcon={IconButton(onClick={visible=!visible}) {Icon(if(visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(visible) "隐藏$label" else "显示$label")}})
}

@Composable
private fun VerificationFields(state:TodoState,address:String,onAddress:(String)->Unit,code:String,onCode:(String)->Unit,send:()->Unit) {
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(state.mailChallenge) {while(true) {now=System.currentTimeMillis();delay(1000)}}
    val remaining=((state.mailChallenge?.resendAt ?: 0)-now+999).coerceAtLeast(0)/1000
    OutlinedTextField(address,onAddress,label={Text("邮箱地址")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!state.busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
    OutlinedTextField(code,{onCode(it.filter(Char::isDigit).take(6))},label={Text("邮箱验证码")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!state.busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
    OutlinedButton(onClick=send,enabled=!state.busy && address.isNotBlank() && remaining==0L) {Text(if(remaining>0) "${remaining} 秒后可重发" else "发送验证码")}
    Text("验证码请在此输入，不要发给他人。未收到时请检查垃圾邮件。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun EmailLogin(state:TodoState,vm:TodoViewModel) {
    var mode by rememberSaveable {mutableStateOf("login")}
    var account by rememberSaveable {mutableStateOf("")}
    var email by rememberSaveable {mutableStateOf("")}
    var nickname by rememberSaveable {mutableStateOf("")}
    var link by rememberSaveable {mutableStateOf("")}
    var password by remember {mutableStateOf("")}
    var confirm by remember {mutableStateOf("")}
    var code by remember {mutableStateOf("")}
    val purpose=if(mode=="reset") EmailPurpose.RESET else EmailPurpose.REGISTER
    LaunchedEffect(state.incomingInvite) {if(state.incomingInvite.isNotBlank()) {link=state.incomingInvite;mode="register"}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.MarkEmailRead,null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.primary)
        Text(when(mode) {"register"->"让日常，彼此看见";"reset"->"找回熟悉的账号";else->"把小事，一起坚持"},style=MaterialTheme.typography.headlineSmall)
        Text("纪念日与个人打卡无需登录。登录后可同步打卡，配对后再共享日常。",style=MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(mode=="login",{mode="login";password="";confirm="";code=""},label={Text("登录")},enabled=!state.busy)
            FilterChip(mode=="register",{mode="register";password="";confirm="";code=""},label={Text("邮箱注册")},enabled=!state.busy)
        }
        if(mode=="login") {
            OutlinedTextField(account,{account=it},label={Text("邮箱或原用户名")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!state.busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
            SecretField(password,{password=it},"密码",!state.busy)
            Button(onClick={vm.login(account,password)},enabled=!state.busy && !state.failed,modifier=Modifier.fillMaxWidth().testTag("email_login_submit")) {Text(if(state.busy) "请稍候…" else "登录")}
            TextButton(onClick={mode="reset";email=account.takeIf {it.contains('@')}.orEmpty();password="";confirm="";code=""},enabled=!state.busy) {Text("忘记密码")}
            Text("原账号继续使用原用户名和密码。登录后可在账号安全中绑定邮箱。",style=MaterialTheme.typography.bodySmall)
        } else {
            if(mode=="register") {
                OutlinedTextField(nickname,{nickname=it},label={Text("昵称")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
                if(EmailRules.invite(link).isBlank()) OutlinedTextField(link,{link=it},label={Text("朋友的注册链接")},modifier=Modifier.fillMaxWidth(),enabled=!state.busy,maxLines=3)
                else Text("♡ 已识别朋友邀请 · 注册不会自动配对",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            }
            VerificationFields(state,email,{email=it;code=""},code,{code=it}) {vm.sendEmail(email,purpose)}
            SecretField(password,{password=it},if(mode=="reset") "新密码" else "设置密码",!state.busy)
            SecretField(confirm,{confirm=it},"确认密码",!state.busy)
            Text("密码 8～32 位，以字母或数字开头，包含大小写字母、数字、符号中的至少三类。",style=MaterialTheme.typography.bodySmall)
            if(confirm.isNotEmpty() && confirm!=password) Text("两次密码不一致",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Button(onClick={if(mode=="register") vm.registerEmail(email,code,password,nickname,link) else vm.resetEmail(email,code,password) {mode="login";account=email;password="";confirm="";code=""}},
                enabled=!state.busy && !state.failed && password.isNotBlank() && password==confirm && code.length==6,modifier=Modifier.fillMaxWidth()) {Text(if(state.busy) "请稍候…" else if(mode=="reset") "更新密码" else "创建账号")}
        }
        if(state.message.isNotBlank()) Text(state.message,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium)
        Text("邮箱用于验证身份、登录和找回账号。待办、配对和主动共享的纪念日由腾讯 CloudBase 保存；未共享的个人纪念日仍保存在本机。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun FriendAdmission(state:TodoState,vm:TodoViewModel) {
    var nickname by rememberSaveable {mutableStateOf(state.session?.name.orEmpty())}
    var link by rememberSaveable(state.incomingInvite) {mutableStateOf(state.incomingInvite)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("继续朋友的邀请",style=MaterialTheme.typography.headlineSmall)
        Text("账号已建立，请使用朋友的注册链接开通日常功能。原有账号请退出后使用原用户名登录，不要重新注册。")
        OutlinedTextField(nickname,{nickname=it},label={Text("昵称")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(link,{link=it},label={Text("朋友的注册链接")},modifier=Modifier.fillMaxWidth(),maxLines=3)
        Button(onClick={vm.completeFriend(nickname,link)},enabled=!state.busy) {Text("完成开通")}
        if(state.message.isNotBlank()) Text(state.message,color=MaterialTheme.colorScheme.primary)
        TextButton(onClick=vm::logout,enabled=!state.busy) {Text("退出账号")}
    }
}

@Composable
internal fun EmailSecurity(state:TodoState,vm:TodoViewModel,back:()->Unit) {
    var address by rememberSaveable {mutableStateOf("")}
    var password by remember {mutableStateOf("")}
    var code by remember {mutableStateOf("")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=back,enabled=!state.busy) {Text("返回账号与伙伴")}
        Text(if(state.contactEmail.isBlank()) "绑定邮箱" else "修改邮箱",style=MaterialTheme.typography.headlineSmall)
        Text(if(state.contactEmail.isBlank()) "绑定后可使用邮箱和原密码登录，并通过邮箱找回密码。" else "当前邮箱：${state.contactEmail}")
        Text("验证当前密码和新邮箱后完成操作，原账号、记录与配对保留。",style=MaterialTheme.typography.bodyMedium)
        SecretField(password,{password=it},"当前密码",!state.busy)
        VerificationFields(state,address,{address=it;code=""},code,{code=it}) {vm.sendEmail(address,EmailPurpose.BIND,password)}
        Button(onClick={vm.bindEmail(address,code) {password="";code="";back()}},enabled=!state.busy && code.length==6) {Text("确认绑定邮箱")}
        if(state.message.isNotBlank()) Text(state.message,color=MaterialTheme.colorScheme.primary)
    }
}
