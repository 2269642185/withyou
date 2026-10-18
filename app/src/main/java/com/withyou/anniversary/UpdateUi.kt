package com.withyou.anniversary

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UpdatesScreen(vm: UpdateViewModel, back: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var repo by rememberSaveable(state.repo) { mutableStateOf(state.repo) }
    var permission by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf("") }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        installMessage="如果取消了安装，可再次点击安装新版。原有数据仍保留。"
    }
    fun install() { vm.install { intent -> installer.launch(intent) } }
    val permissionSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(context.packageManager.canRequestPackageInstalls()) install()
        else installMessage="尚未允许安装，可再次点击安装新版后授权。"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        IconButton(onClick=back) { Icon(Icons.Outlined.ArrowBack,"返回") }
        Text("版本与更新",fontSize=28.sp)
        Text("当前版本 ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleMedium)
        Text("回忆保留在本机。新版会覆盖安装，无需卸载原 App。",style=MaterialTheme.typography.bodyMedium)
        if(state.repo.isBlank()) {
            Text("尚未连接更新仓库",style=MaterialTheme.typography.titleMedium)
            Text("发布完成后，在下方填入 GitHub 下载仓库地址。配置前无法在线检查更新。")
        }
        if(state.message.isNotBlank()) Text(state.message)
        Button(onClick={vm.check(true)},enabled=state.repo.isNotBlank() && state.phase !in listOf("checking","downloading","ready"),modifier=Modifier.fillMaxWidth()) { Text("检查更新") }
        state.update?.let { update ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("新版 ${update.version}",style=MaterialTheme.typography.titleLarge)
                Text("安装包 %.1f MB".format(update.size / 1048576.0))
                Text(update.notes)
                when(state.phase) {
                    "downloading" -> {
                        LinearProgressIndicator(progress={state.progress/100f},modifier=Modifier.fillMaxWidth())
                        Text("已下载 ${state.progress}%")
                        OutlinedButton(onClick=vm::cancel) { Text("取消下载") }
                    }
                    "ready" -> Button(onClick={
                        if(context.packageManager.canRequestPackageInstalls()) install() else permission=true
                    }) { Text("安装新版") }
                    else -> {
                        Button(onClick=vm::download) { Text("下载新版") }
                        TextButton(onClick=vm::ignore) { Text("忽略此版本") }
                    }
                }
            } }
        }
        if(installMessage.isNotBlank()) Text(installMessage)
        HorizontalDivider()
        Text("更新地址",style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(value=repo,onValueChange={repo=it},label={Text("GitHub 仓库地址")},supportingText={Text("填写完整仓库链接，或 用户名/仓库名")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedButton(onClick={vm.configure(repo)},enabled=state.phase !in listOf("checking","downloading")) { Text("保存更新地址") }
        Text("每个自然日自动检查一次，有新版时轻声提示。网络不可用不影响日常记录。安装需你在系统界面确认。",style=MaterialTheme.typography.bodySmall)
    }
    if(permission) AlertDialog(onDismissRequest={permission=false},title={Text("允许安装新版")},text={Text("系统需要允许“与你 · 纪念日”安装未知来源应用。开启后返回即可继续；本 App 不会静默安装。")},confirmButton={TextButton(onClick={
        permission=false
        try { permissionSettings.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))) }
        catch (_: Exception) { installMessage="此手机未提供授权入口，请到系统设置中手动允许本 App 安装应用。" }
    }) {Text("前往设置")}},dismissButton={TextButton(onClick={permission=false}) {Text("暂不安装")}})
}
