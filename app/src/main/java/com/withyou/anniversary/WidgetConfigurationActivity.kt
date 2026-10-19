package com.withyou.anniversary

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        val info=AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID || info?.provider?.packageName!=packageName) {finish();return}
        enableEdgeToEdge()
        setContent {
            val choice=remember {WidgetStore(this).get(id)}
            var data by remember {mutableStateOf<AppData?>(null)}
            var error by remember {mutableStateOf<String?>(null)}
            var busy by remember {mutableStateOf(false)}
            var selected by rememberSaveable {mutableStateOf(choice?.recordId)}
            var follow by rememberSaveable {mutableStateOf(choice?.followColors ?: true)}
            var background by rememberSaveable {mutableStateOf(choice?.background ?: "#F0DCC8")}
            var text by rememberSaveable {mutableStateOf(choice?.text)}
            var group by rememberSaveable {mutableStateOf("奶油")}
            var previewKind by rememberSaveable {mutableIntStateOf(when(info.provider.className) {SmallAnniversaryWidget::class.java.name ->0;LargeAnniversaryWidget::class.java.name ->2;else ->1})}
            val dark=when(data?.preferences?.theme ?: AppTheme.SYSTEM) {AppTheme.SYSTEM ->isSystemInDarkTheme();AppTheme.LIGHT ->false;AppTheme.DARK ->true}
            val view=LocalView.current
            SideEffect {WindowCompat.getInsetsController(window,view).apply {isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark}}
            val scope=rememberCoroutineScope()
            LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {data=withContext(Dispatchers.IO) {Repository(this@WidgetConfigurationActivity).read()};if(selected==null)selected=data?.ordered()?.firstOrNull()?.id}
                    catch(e:Exception) {error="读取失败，请先打开应用检查记录"}
                }
            }
            MaterialTheme(colorScheme=withYouColors(dark)) {
                Scaffold {padding ->Column(Modifier.fillMaxSize().padding(padding)) {
                    PageHeading("选择桌面上的纪念",{finish()})
                    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        item {Text("每个小组件都可以有自己的日子与外观。")}
                        if(data==null && error==null) item {CircularProgressIndicator()}
                        if(data?.records?.isEmpty()==true) item {Text("还没有纪念日，请先回到应用创建。")
                            TextButton(onClick={startActivity(Intent(this@WidgetConfigurationActivity,MainActivity::class.java));finish()}) {Text("打开应用")}}
                        items(data?.ordered() ?: emptyList(),key={it.id}) {r ->SelectionRow("${r.title} · ${r.date}",selected==r.id) {selected=r.id}}
                        item {Row(verticalAlignment=Alignment.CenterVertically) {Text("跟随卡片外观",Modifier.weight(1f));Switch(checked=follow,onCheckedChange={follow=it})}}
                        item {
                            Text(if(follow) "同步背景、照片、模板与文字颜色；卡片修改后自动更新。" else "使用独立纯色与简洁布局，不受卡片照片、模板和配色修改影响。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf("小","中","大").forEachIndexed {index,label ->FilterChip(selected=previewKind==index,onClick={previewKind=index},label={Text("${label}尺寸预览")})}
                            }
                            data?.records?.firstOrNull {it.id==selected}?.let {r ->WidgetPreview(id,r,WidgetChoice(r.id,follow,background,text),previewKind)}
                            Text("预览按尺寸简化排版。拍立得小尺寸使用照片背景，中尺寸左右分区，大尺寸上下分区。实际尺寸及圆角由桌面共同决定。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(!follow) {
                            item {Text("独立背景色")
                                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {ColorRules.groups.forEach {g ->FilterChip(group==g,{group=g},label={Text(g)})}}
                                SwatchGrid(background,ColorRules.swatches.filter {it.group==group}) {background=it.hex}
                            }
                            item {Text("文字颜色")
                                FilterChip(selected=text==null,onClick={text=null},label={Text("自动适配")})
                                SwatchGrid(text ?: "",ColorRules.textChoices.map {ColorSwatch("文字色",it,"")}) {text=it.hex}
                                if(text!=null && ColorRules.contrast(background,text!!)<4.5) {
                                    Text("当前对比度较低，建议使用自动文字颜色。")
                                    TextButton(onClick={text=null}) {Text("一键提高可读性")}
                                }
                            }
                        }
                        error?.let {message ->item {Text(message,color=MaterialTheme.colorScheme.error)}}
                    }
                    Button(enabled=!busy && data?.records?.any {it.id==selected}==true,onClick={scope.launch {
                        busy=true
                        try {
                            withContext(Dispatchers.IO) {
                                require(Repository(this@WidgetConfigurationActivity).read().records.any {it.id==selected}) {"该纪念日已移除，请重新选择"}
                                WidgetStore(this@WidgetConfigurationActivity).save(id,WidgetChoice(selected!!,follow,background,text))
                                AnniversaryWidget.refresh(this@WidgetConfigurationActivity)
                            }
                            setResult(Activity.RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish()
                        } catch(e:Exception) {error=e.message ?: "配置失败，请重试";busy=false}
                    }},modifier=Modifier.fillMaxWidth().padding(24.dp).heightIn(min=52.dp)) {Text(if(busy) "正在保存…" else "保存小组件")}
                }}
            }
        }
    }
}
