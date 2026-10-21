package com.withyou.anniversary

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
internal fun SyncDiagnosticButton(detail:String) {
    var open by remember {mutableStateOf(false)}
    val clipboard=LocalClipboardManager.current
    TextButton(onClick={open=true}) {Text("同步详情")}
    if(open) AlertDialog(onDismissRequest={open=false},title={Text("同步诊断")},
        text={SelectionContainer {Text(detail,Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)}},
        confirmButton={TextButton(onClick={clipboard.setText(AnnotatedString(detail));open=false}) {Text("复制诊断信息")}},
        dismissButton={TextButton(onClick={open=false}) {Text("关闭")}})
}
