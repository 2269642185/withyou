package com.withyou.anniversary

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun TodoAccountScreen(vm:TodoViewModel,back:()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->uri?.let(vm::export)}
    TodoAccountContent(state,vm,back,export::launch)
}

@Composable
internal fun TodoAccountContent(state:TodoState,vm:TodoViewModel,back:()->Unit,export:(String)->Unit) {
    if(state.session==null || state.reauthRequired) {
        Column(Modifier.fillMaxSize()) {
            IconButton(onClick=back) {Icon(Icons.Outlined.ArrowBack,"返回设置")}
            TextButton(onClick={export("与你-打卡-${TodoRules.today()}.json")},modifier=Modifier.padding(horizontal=16.dp)) {Text(if(state.reauthRequired) "导出当前账号打卡" else "导出本机个人打卡")}
            Text(if(state.reauthRequired) "登录状态需要更新。同一账号重新登录后会继续同步，当前记录和待同步操作保留。" else "登录前的打卡单独保留在这台设备；登录后显示账号记录，退出登录可返回本机记录。",Modifier.padding(horizontal=24.dp),style=MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f).testTag("account_login")) {EmailLogin(state,vm)}
        }
    } else if(state.session?.admitted==false) FriendAdmission(state,vm)
    else TodoAccount(state,vm,back)
}
