package com.withyou.anniversary

import android.app.DatePickerDialog
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var requestedId = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedId.value = intent.getStringExtra("recordId")
        setContent { AnniversaryApp(requestedId.value, consumed = { requestedId.value = null }) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); requestedId.value = intent.getStringExtra("recordId")
    }
}

private val palette = ColorRules.swatches.map { Color(ColorRules.argb(it.hex)) }
private val chineseDate = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")
internal fun LocalDate.label() = format(chineseDate)

@Composable
fun AnniversaryApp(requestedId: String? = null, consumed: () -> Unit = {}, vm: MainViewModel = viewModel()) {
    val updater: UpdateViewModel = viewModel()
    val todos: TodoViewModel = viewModel()
    val updateState by updater.state.collectAsStateWithLifecycle()
    val todoState by todos.state.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val failed by vm.loadFailed.collectAsStateWithLifecycle()
    val dark = when (data.preferences.theme) { AppTheme.SYSTEM -> isSystemInDarkTheme(); AppTheme.LIGHT -> false; AppTheme.DARK -> true }
    val activity = LocalContext.current as? Activity
    val view = LocalView.current
    SideEffect {
        activity?.let { WindowCompat.getInsetsController(it.window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        } }
    }
    val colors = withYouColors(dark)
    MaterialTheme(colorScheme = colors, typography = Typography()) {
        var route by rememberSaveable { mutableStateOf("home") }
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        var homeFilter by rememberSaveable { mutableStateOf("all") }
        val today = rememberToday()
        val snackbar = remember { SnackbarHostState() }
        var notice by remember { mutableStateOf<String?>(null) }
        var announcedUpdate by rememberSaveable { mutableStateOf(0L) }
        val updateOwner=LocalLifecycleOwner.current
        LaunchedEffect(updateOwner,todoState.session?.uid,route) {
            if(todoState.session!=null && route!="editor") updateOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                todos.sync(false)
                while(true) {delay(5000);todos.sync(false)}
            }
        }
        val safeForUpdate by rememberUpdatedState(ready && route in listOf("home","settings"))
        DisposableEffect(updateOwner) {
            val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME && safeForUpdate) updater.check(false) }
            updateOwner.lifecycle.addObserver(observer)
            onDispose {updateOwner.lifecycle.removeObserver(observer)}
        }
        LaunchedEffect(route, ready) { if(ready && route in listOf("home","settings")) updater.check(false) }
        LaunchedEffect(route, updateState.phase, updateState.update?.code) {
            val update = updateState.update
            if(route in listOf("home","settings") && updateState.automatic && updateState.phase=="available" && update!=null && announcedUpdate!=update.code) {
                announcedUpdate=update.code
                if(snackbar.showSnackbar("有新版 ${update.version}，可前往设置更新",actionLabel="查看")==SnackbarResult.ActionPerformed) route="updates"
            }
        }
        LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.dismissError() } }
        LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); notice = null } }
        LaunchedEffect(requestedId, ready) {
            if (ready && requestedId != null) {
                if (data.records.any { it.id == requestedId }) { selected = requestedId; route = "detail" }
                consumed()
            }
        }
        val record = data.records.firstOrNull { it.id == selected }
        fun add() { selected = null; route = "editor" }
        fun open(id: String) { selected = id; route = "detail" }
        BackHandler(enabled = route != "home" && route != "editor" && !busy) { route = "home" }
        CompositionLocalProvider(LocalMotionEnabled provides rememberMotionEnabled(data.preferences.animations)) {
        Scaffold(
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (route in listOf("home","todos","settings")) NavigationBar(containerColor = colors.surface) {
                    NavigationBarItem(selected = route == "home", onClick = { route = "home" }, icon = { Icon(Icons.Outlined.FavoriteBorder, null) }, label = { Text("纪念日") })
                    NavigationBarItem(selected = route == "todos", onClick = { route = "todos" }, icon = { Icon(Icons.Outlined.CheckCircleOutline, null) }, label = { Text("打卡") })
                    NavigationBarItem(selected = route == "settings", onClick = { route = "settings" }, icon = { Icon(Icons.Outlined.Tune, null) }, label = { Text("设置") })
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).clipToBounds()) {
                if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else PageMotion(route) { when (route) {
                    "home" -> Home(data, today, failed, ::add, ::open, { route = "categories" }, {route="shared"}, homeFilter) { homeFilter=it }
                    "detail" -> if (record != null) Detail(record, today, vm.repository.photos, busy,
                        { route = "home" }, { route = "editor" }, { vm.pin(record.id) }, { todos.sharedDelete(record) {vm.delete(record.id) { route = "home" }} }, {MemoryShareAction(record,todos) {route="todos"}})
                        else LaunchedEffect(Unit) { route = "home" }
                    "editor" -> key(selected) { Editor(record, data.preferences.defaultMode, today, vm, busy,
                        { route = if (record == null) "home" else "detail" }, { saved -> vm.save(saved) { selected = saved.id; route = "detail"; todos.sharedEdit(saved) } }) }
                    "settings" -> Settings(data, vm, busy, {route="updates"}) { notice = it }
                    "updates" -> UpdatesScreen(updater) {route="settings"}
                    "todos" -> TodoScreen(todos)
                    "shared" -> SharedMemoriesScreen(todos,data.records,{route="home"},{route="todos"})
                    "categories" -> CategoryManager(data,vm) {route="home"}
                } }
                if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 4.dp) { Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text("正在妥善保存…") } }
                }
            }
        }
        }
    }
}

@Composable
private fun rememberToday(): LocalDate {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { date = LocalDate.now(); scope.launch(Dispatchers.IO) {AnniversaryWidget.refresh(context)} } }
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { date = LocalDate.now(); scope.launch(Dispatchers.IO) {AnniversaryWidget.refresh(context)} } }
        owner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply { addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { owner.lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(Unit) { while (true) { date = LocalDate.now(); delay(1000) } }
    return date
}

@Composable
private fun Home(data: AppData, today: LocalDate, failed: Boolean, add: () -> Unit, open: (String) -> Unit, categories: () -> Unit, shared: () -> Unit, filter: String, onFilter: (String) -> Unit) {
    val context = LocalContext.current
    val motion=LocalMotionEnabled.current
    val listState=rememberLazyListState()
    val threshold=with(LocalDensity.current) {72.dp.toPx()}
    val pull=remember(listState,threshold) {CategoryPullState(threshold) {listState.firstVisibleItemIndex==0 && listState.firstVisibleItemScrollOffset==0}}
    val transition=remember {MutableTransitionState(false)}
    transition.targetState=pull.expanded
    val panelVisible=transition.currentState || transition.targetState || !transition.isIdle
    val scope=rememberCoroutineScope()
    val categoryName=if(filter=="none") "未分类" else data.categories.firstOrNull {it.id==filter}?.name
    LaunchedEffect(data.categories,filter) {if(filter!="all" && filter!="none" && data.categories.none {it.id==filter}) onFilter("all")}
    val ordered = data.ordered().filter {filter=="all" || if(filter=="none") it.categoryId==null else it.categoryId==filter}
    LazyColumn(Modifier.fillMaxSize().testTag("home_list").nestedScroll(pull.connection).semantics {
        customActions=listOf(CustomAccessibilityAction(if(pull.expanded) "收起分类" else "展开分类") {
            scope.launch {listState.scrollToItem(0);pull.expanded=!pull.expanded};true
        })
    },state=listState, contentPadding = PaddingValues(24.dp, 22.dp, 24.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item(key="home_heading") {
                Column(Modifier.fillMaxWidth()) {
                    Text("与你", fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("把平凡日子，过成值得纪念。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
        }
        item(key="home_date") {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("${today.label()}  ·  星期${listOf("一", "二", "三", "四", "五", "六", "日")[today.dayOfWeek.value - 1]}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                if(filter!="all" && !panelVisible && categoryName!=null) Text("当前：$categoryName",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
            }
        }
        item(key="shared_entry") {TextButton(onClick=shared) {Icon(Icons.Outlined.PeopleOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("我们的共享")}}
        if(panelVisible) item(key="category_panel") {
            AnimatedVisibility(visibleState=transition,enter=expandVertically(tween(if(motion)200 else 0))+fadeIn(tween(if(motion)160 else 0)),
                exit=shrinkVertically(tween(if(motion)200 else 0))+fadeOut(tween(if(motion)120 else 0))) {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick=categories) {Icon(Icons.Outlined.FolderOpen,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("管理分类")}
                    TextButton(onClick={scope.launch {listState.scrollToItem(0);pull.expanded=false}}) {Text("收起");Icon(Icons.Outlined.ExpandLess,null,Modifier.size(18.dp))}
                }
                Row(Modifier.testTag("category_chips").horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=filter=="all",onClick={onFilter("all")},label={Text("全部 ${data.records.size}")})
                    FilterChip(selected=filter=="none",onClick={onFilter("none")},label={Text("未分类 ${data.records.count {it.categoryId==null}}")})
                    data.categories.forEach { c ->FilterChip(selected=filter==c.id,onClick={onFilter(c.id)},label={Text("${c.name} ${data.records.count {it.categoryId==c.id}}")},leadingIcon={Surface(color=palette[c.color],shape=CircleShape) {Icon(CategoryIcons.icon(c.icon),null,Modifier.size(22.dp).padding(2.dp),tint=Color(ColorRules.argb(ColorRules.autoInk(ColorRules.swatches[c.color].hex))))}})}
                }
            }
            }
        }
        if (failed) item { InfoCard("本地数据暂时无法读取", "为保护原有记录，已暂停编辑。请前往设置，从备份恢复。", Icons.Outlined.Info) }
        else if (ordered.isEmpty() && data.records.isNotEmpty()) {
            item {InfoCard("这个分类还没有纪念日", "可以新增纪念日并选择此分类，也可以切换到“全部”查看已有记录。",Icons.Outlined.FolderOpen)}
            item {Button(onClick=add) {Icon(Icons.Outlined.Add,null);Text("新增纪念日")}}
        }
        else if (ordered.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        KeepsakeArt(Modifier.size(180.dp))
                        Text("总有一天，值得记住", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Text("第一次相遇、认真说爱、一起出发…\n从一个日期开始，收藏你们的故事。", textAlign = TextAlign.Center, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp))
                        Button(onClick = add, contentPadding = PaddingValues(24.dp, 14.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("添加第一个纪念日") }
                    }
                }
            }
            item { InfoCard("日子会走，心动留下", "记录只保存在本机。你可以随时在设置里导出含照片的完整备份。", Icons.Outlined.Spa) }
        } else {
            item(key=ordered.first().id) { MemoryCard(ordered.first(), today, File(context.filesDir, "photos"), true,if(motion) Modifier.animateItem() else Modifier) { open(ordered.first().id) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("珍藏的日子  ·  ${ordered.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    FilledTonalButton(onClick = add) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text("新增") }
                }
            }
            items(ordered.drop(1), key = { it.id }) { r -> MemoryCard(r, today, File(context.filesDir, "photos"), false,if(motion) Modifier.animateItem() else Modifier) { open(r.id) } }
            item { Text("每一个小日子，都有大大的意义。", Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun KeepsakeArt(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawCircle(Color(0xFFF9F0E5), w * .42f, Offset(w*.5f,h*.49f))
        drawCircle(Color(0xFFE6C4A4), w*.29f, Offset(w*.57f,h*.42f))
        drawRoundRect(Color(0xFFFFFAF4), Offset(w*.22f,h*.34f), androidx.compose.ui.geometry.Size(w*.52f,h*.47f), androidx.compose.ui.geometry.CornerRadius(w*.05f))
        drawRoundRect(Color(0xFFBD8D71), Offset(w*.22f,h*.34f), androidx.compose.ui.geometry.Size(w*.52f,h*.12f), androidx.compose.ui.geometry.CornerRadius(w*.04f))
        drawCircle(Color(0xFFD6AA8E), w*.065f, Offset(w*.45f,h*.58f)); drawCircle(Color(0xFFD6AA8E),w*.065f,Offset(w*.55f,h*.58f))
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(w*.388f,h*.6f); lineTo(w*.5f,h*.73f); lineTo(w*.612f,h*.6f); close() }
        drawPath(path,Color(0xFFD6AA8E))
        drawCircle(Color(0xFF9EAB8C),w*.028f,Offset(w*.18f,h*.25f)); drawCircle(Color(0xFF9EAB8C),w*.018f,Offset(w*.81f,h*.72f))
    }
}

@Composable
internal fun Photo(name: String?, dir: File, modifier: Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { name?.let { runCatching { BitmapFactory.decodeFile(File(dir, it).path)?.asImageBitmap() }.getOrNull() } }
    }
    bitmap?.let { Image(it, null, modifier, contentScale = ContentScale.Crop) }
}

@Composable
internal fun ClassicMemoryCard(record: MemoryDay, today: LocalDate, dir: File, hero: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    val count = DateRules.count(record.date, today, record.mode)
    val hasPhoto = record.photo != null
    val motion=LocalMotionEnabled.current
    val duration=if(motion) 240 else 0
    val ink by animateColorAsState(Color(ColorRules.argb(ColorRules.ink(record))),tween(duration),label="文字颜色")
    val background by animateColorAsState(Color(ColorRules.argb(ColorRules.background(record))),tween(duration),label="背景颜色")
    val titleInk by animateColorAsState(record.appearance.title?.let {Color(ColorRules.argb(it))} ?: ink,tween(duration),label="标题颜色")
    val dayInk by animateColorAsState(record.appearance.days?.let {Color(ColorRules.argb(it))} ?: ink,tween(duration),label="天数颜色")
    val auxInk by animateColorAsState(record.appearance.auxiliary?.let {Color(ColorRules.argb(it))} ?: ink,tween(duration),label="辅助文字颜色")
    val interactions=remember {MutableInteractionSource()}
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if(motion && pressed) .985f else 1f,tween(if(motion)100 else 0),label="按压反馈")
    Surface(onClick = click, interactionSource=interactions, modifier=modifier.graphicsLayer {scaleX=scale;scaleY=scale}, shape = RoundedCornerShape(if (hero) 30.dp else 24.dp), color = background) {
        Box(Modifier.fillMaxWidth().then(if(motion) Modifier.animateContentSize() else Modifier)) {
            Photo(record.photo, dir, Modifier.matchParentSize())
            if (hasPhoto) {
                val veil=if(ColorRules.contrast(ColorRules.ink(record),"#000000")>=ColorRules.contrast(ColorRules.ink(record),"#FFFFFF")) Color.Black else Color.White
                val strength=if(record.appearance.tone==TextTone.AUTO) maxOf(.67f,record.appearance.overlay) else record.appearance.overlay
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(veil.copy(alpha=(strength-.11f).coerceIn(0f,1f)),veil.copy(alpha=(strength+.12f).coerceIn(0f,1f))))))
            }
            else Canvas(Modifier.matchParentSize()) {
                val decoration=if(ColorRules.contrast(ColorRules.ink(record),"#FFFFFF")>=ColorRules.contrast(ColorRules.ink(record),"#000000")) Color.White else Color.Black
                drawCircle(decoration.copy(alpha=.16f), size.width*.45f, Offset(size.width,size.height*.25f));drawCircle(decoration.copy(alpha=.12f),size.width*.25f,Offset(size.width*.1f,size.height*1.2f))
            }
            Column(Modifier.padding(if (hero) 28.dp else 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (record.pinned) Icons.Outlined.PushPin else Icons.Outlined.FavoriteBorder, null, Modifier.size(18.dp), tint = auxInk)
                    Text(if (record.pinned) "置顶 · 最特别的日子" else "我们的纪念", color = auxInk, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(start=6.dp))
                    Icon(Icons.Outlined.ArrowOutward, "查看详情", tint = auxInk, modifier = Modifier.size(20.dp))
                }
                Text(record.title, color = titleInk, fontSize = if (hero) 24.sp else 20.sp, fontWeight = FontWeight.SemiBold)
                if (record.nickname.isNotBlank()) Text("和 ${record.nickname}", color = auxInk, style = MaterialTheme.typography.bodyMedium)
                Text(count.caption, color = auxInk, style = MaterialTheme.typography.bodyMedium)
                BoxWithConstraints {
                    val scale = LocalDensity.current.fontScale
                    var size by remember(count.days, hero, maxWidth, scale) { mutableFloatStateOf(if(hero) 72f else 44f) }
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(targetState=count.days,transitionSpec={fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))},label="日数切换",modifier=Modifier.weight(1f,fill=false)) { days ->
                        Text(days.toString(), color = dayInk, fontSize = size.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp,
                            maxLines = 1, softWrap = false, onTextLayout = { if (it.hasVisualOverflow && size > 18f) size = (size * .9f).coerceAtLeast(18f) })
                        }
                        Text("天", color = dayInk, modifier = Modifier.padding(start = 10.dp, bottom = if(hero) 16.dp else 8.dp))
                    }
                }
                HorizontalDivider(color=ink.copy(alpha=.15f))
                Text("${record.date.label()}  ·  ${if (count.today) "今天值得庆祝" else "故事的开始"}", color = auxInk, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun PageHeading(title: String, back: () -> Unit, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, "返回") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun Detail(record: MemoryDay, today: LocalDate, dir: File, busy: Boolean, back: () -> Unit, edit: () -> Unit, pin: () -> Unit, delete: () -> Unit, share: @Composable () -> Unit = {}) {
    var confirm by remember { mutableStateOf(false) }
    val next = DateRules.nextAnniversary(record.date, today)
    val days = DateRules.untilAnniversary(record.date, today)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        PageHeading("日子的故事", back) { TextButton(onClick = edit, enabled = !busy) { Text("编辑") } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom=28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            MemoryCard(record, today, dir, true) {}
            InfoCard(if (days == 0L) "今天，就是纪念日" else if (today < record.date) "距离故事开始，还有 $days 天" else "下一个年度纪念日 · 还有 $days 天", next.label(), Icons.Outlined.Event)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("关于这份纪念", fontWeight = FontWeight.SemiBold)
                    Text("起始日期：${record.date.label()}")
                    Text("计数方式：${record.mode.label}", style = MaterialTheme.typography.bodyMedium)
                    Text("累计与周年倒计时分别计算。未来日期按实际相差天数倒计时；周年当天为 0 天。2 月 29 日在非闰年以 2 月 28 日作为周年日。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
                }
            }
            share()
            OutlinedButton(onClick = pin, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PushPin, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if(record.pinned) "取消置顶" else "设为首页置顶") }
            TextButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error); Text("删除这个纪念日", color = MaterialTheme.colorScheme.error) }
        }
    }
    AnniversaryPetals(record,today)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("删除这份纪念？") }, text = { Text("“${record.title}”将从本机移除。此操作无法撤销，已有备份不受影响。") }, confirmButton = { TextButton(onClick = { confirm = false; delete() }) { Text("确认删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("再想想") } })
}

@Composable
fun InfoCard(title: String, text: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f)) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp) }
        }
    }
}

@Composable
private fun Editor(record: MemoryDay?, defaultMode: CountMode, today: LocalDate, vm: MainViewModel, busy: Boolean, back: () -> Unit, save: (MemoryDay) -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(record?.title ?: "") }
    var dateText by rememberSaveable { mutableStateOf((record?.date ?: today).toString()) }
    var nickname by rememberSaveable { mutableStateOf(record?.nickname ?: "") }
    var modeName by rememberSaveable { mutableStateOf((record?.mode ?: defaultMode).name) }
    var templateKey by rememberSaveable { mutableStateOf((record?.template ?: CardTemplate.CLASSIC).key) }
    val template = CardTemplate.fromKey(templateKey)
    var color by rememberSaveable { mutableIntStateOf(record?.color ?: 0) }
    var photo by rememberSaveable { mutableStateOf(record?.photo) }
    var pinned by rememberSaveable { mutableStateOf(record?.pinned ?: false) }
    val data by vm.data.collectAsStateWithLifecycle()
    var categoryId by rememberSaveable {mutableStateOf(record?.categoryId)}
    var createCategory by remember {mutableStateOf(false)}
    var bgHex by rememberSaveable {mutableStateOf(record?.appearance?.background)}
    var tone by rememberSaveable {mutableStateOf((record?.appearance?.tone ?: TextTone.AUTO).name)}
    var textHex by rememberSaveable {mutableStateOf(record?.appearance?.text)}
    var titleHex by rememberSaveable {mutableStateOf(record?.appearance?.title)}
    var daysHex by rememberSaveable {mutableStateOf(record?.appearance?.days)}
    var auxHex by rememberSaveable {mutableStateOf(record?.appearance?.auxiliary)}
    var overlay by rememberSaveable {mutableFloatStateOf(record?.appearance?.overlay ?: .56f)}
    var appearanceValid by remember {mutableStateOf(true)}
    val appearance=CardAppearance(bgHex,TextTone.valueOf(tone),textHex,titleHex,daysHex,auxHex,overlay)
    fun changeAppearance(a: CardAppearance) {bgHex=a.background;tone=a.tone.name;textHex=a.text;titleHex=a.title;daysHex=a.days;auxHex=a.auxiliary;overlay=a.overlay}
    val id = rememberSaveable { record?.id ?: UUID.randomUUID().toString() }
    var discard by remember { mutableStateOf(false) }
    val mode = CountMode.valueOf(modeName)
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()?.takeIf { it.year in 1..9998 }
    val dirty = title != (record?.title ?: "") || dateText != (record?.date ?: today).toString() || nickname != (record?.nickname ?: "") || mode != (record?.mode ?: defaultMode) || color != (record?.color ?: 0) || photo != record?.photo || pinned != (record?.pinned ?: false) || appearance != (record?.appearance ?: CardAppearance()) || categoryId != record?.categoryId || template != (record?.template ?: CardTemplate.CLASSIC)
    fun leave() { if (dirty) discard = true else back() }
    BackHandler { if (!busy) leave() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.importPhoto(it) { photo = it } } }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeading(if(record == null) "新建纪念日" else "编辑纪念日", ::leave)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("让某一天，从此变得特别。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value=title, onValueChange={ if(it.length <= 60) title = it }, label={Text("纪念日标题")}, placeholder={Text("例如：我们在一起")}, singleLine=true, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(16.dp), supportingText={Text("必填 · 最多 60 字")})
            OutlinedTextField(value=dateText, onValueChange={dateText=it.take(10)}, label={Text("起始日期")}, supportingText={Text(if(date == null) "请输入有效日期，例如 2024-02-29" else "年-月-日，也可点右侧日历选择")}, isError=date==null, singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii), modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(16.dp), trailingIcon={IconButton(onClick={
                val d = date ?: today
                DatePickerDialog(context, { _, y, m, day -> dateText = LocalDate.of(y,m+1,day).toString() },d.year,d.monthValue-1,d.dayOfMonth).apply {
                    datePicker.minDate = LocalDate.of(1,1,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    datePicker.maxDate = LocalDate.of(9998,12,31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.show()
            }) {Icon(Icons.Outlined.CalendarMonth,"选择日期")}})
            OutlinedTextField(value=nickname,onValueChange={if(it.length<=40) nickname=it},label={Text("对方昵称（选填）")},placeholder={Text("你想一起记住的人")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
            Text("所属分类",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=categoryId==null,onClick={categoryId=null},label={Text("未分类")})
                data.categories.forEach { c ->FilterChip(selected=categoryId==c.id,onClick={categoryId=c.id},label={Text(c.name)},leadingIcon={Icon(CategoryIcons.icon(c.icon),null,Modifier.size(18.dp))})}
            }
            TextButton(onClick={createCategory=true}) {Icon(Icons.Outlined.Add,null);Text("创建分类")}
            Text("怎样计算日子", fontWeight=FontWeight.SemiBold)
            CountMode.entries.forEach { m -> SelectionRow(m.label, mode==m) { modeName=m.name } }
            Text("卡片预览",style=MaterialTheme.typography.titleMedium)
            val preview=MemoryDay(id,title.ifBlank {"你的纪念日"},date ?: today,nickname,mode,color,photo,pinned,appearance,categoryId,template)
            MemoryCard(preview,today,vm.repository.photos,false) {}
            TemplateChooser(preview,today,vm.repository.photos) {templateKey=it.key}
            if(template.photoFocused && photo==null) {
                Text("这款模板也可以使用纯色背景，添加照片后更有纪念感。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick={picker.launch("image/*")},enabled=!busy) {Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));Text("添加照片")}
            }
            val textOnPhoto=preview.templateColors().photo!=null
            AppearanceEditor(color,appearance,textOnPhoto,{color=it},::changeAppearance,{appearanceValid=it})
            val bg=ColorRules.background(preview.templateColors())
            val actualInk=ColorRules.ink(preview.templateColors())
            val minimum=listOf(appearance.title ?: actualInk,appearance.days ?: actualInk,appearance.auxiliary ?: actualInk).minOf {ColorRules.contrast(bg,it)}
            if(!textOnPhoto && minimum<4.5) {
                InfoCard("这组文字与背景的对比度较低", "可以一键改成自动文字颜色，或在高级配色中调整。",Icons.Outlined.Contrast)
                TextButton(onClick={changeAppearance(appearance.copy(tone=TextTone.AUTO,title=null,days=null,auxiliary=null))}) {Text("一键提高可读性")}
            } else if(textOnPhoto && (appearance.tone!=TextTone.AUTO || appearance.title!=null || appearance.days!=null || appearance.auxiliary!=null)) {
                Text("照片明暗各不相同，请检查预览中的文字。自动文字与默认遮罩可提升可读性。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={changeAppearance(CardAppearance(background=appearance.background))}) {Text("一键提高可读性")}
            }
            OutlinedButton(onClick={picker.launch("image/*")},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AddPhotoAlternate,null); Spacer(Modifier.width(8.dp)); Text(if(photo==null) "导入背景照片" else "更换背景照片") }
            if(photo!=null) {
                Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(18.dp)).background(palette[color])) { Photo(photo,vm.repository.photos,Modifier.fillMaxSize()) }
                TextButton(onClick={photo=null}) {Text("移除照片，使用背景色")}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("置顶到首页",Modifier.weight(1f)); Switch(checked=pinned,onCheckedChange={pinned=it}) }
            if(date!=null) InfoCard("此刻的日子", "${DateRules.count(date,today,mode).let { "${it.caption} ${it.days} 天" }}\n${if(today < date) "到达日期" else "下个年度纪念日"}：${DateRules.nextAnniversary(date,today).label()}", Icons.Outlined.AutoAwesome)
            Spacer(Modifier.height(4.dp))
        }
        Button(onClick={date?.let { save(MemoryDay(id,title.trim(),it,nickname.trim(),mode,color,photo,pinned,appearance,categoryId,template)) }},enabled=!busy&&title.isNotBlank()&&date!=null&&appearanceValid,modifier=Modifier.fillMaxWidth().padding(24.dp).heightIn(min=52.dp),shape=RoundedCornerShape(18.dp)) {Text("保存纪念日")}
    }
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("放弃这次修改？")},text={Text("尚未保存的内容将不会保留。")},confirmButton={TextButton(onClick={discard=false;back()}){Text("放弃修改")}},dismissButton={TextButton(onClick={discard=false}){Text("继续编辑")}})
    if(createCategory) CategoryDialog(null,data.categories,{createCategory=false}) {c ->vm.saveCategory(c) {categoryId=c.id;createCategory=false}}
}

@Composable
fun SelectionRow(text: String, selected: Boolean, click: () -> Unit) {
    Surface(onClick=click,shape=RoundedCornerShape(16.dp),color=if(selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha=.5f) else MaterialTheme.colorScheme.surface, modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {RadioButton(selected=selected,onClick=null);Text(text,modifier=Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)}
    }
}

@Composable
private fun Settings(data: AppData, vm: MainViewModel, busy: Boolean, updates: () -> Unit, notice: (String) -> Unit) {
    var confirmRestore by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { vm.backup(it) { notice("备份已导出，包含记录、设置与照片") } } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.restore(it) { notice("恢复完成，已重新计算全部日期") } } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text("留给日常的偏爱",fontSize=28.sp,fontWeight=FontWeight.SemiBold)
        Text("简单一点，也安心一点。",color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=updates,modifier=Modifier.fillMaxWidth()) { Text("版本与更新") }
        Text("外观主题",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=10.dp))
        AppTheme.entries.forEach { theme -> SelectionRow(theme.label,data.preferences.theme==theme) { vm.update(transform={it.copy(preferences=it.preferences.copy(theme=theme))}) } }
        Text("新纪念日的默认计数",fontWeight=FontWeight.SemiBold)
        CountMode.entries.forEach { mode -> SelectionRow(mode.label,data.preferences.defaultMode==mode) { vm.update(transform={it.copy(preferences=it.preferences.copy(defaultMode=mode))}) } }
        Text("仅影响之后新建的记录。已有纪念日可在编辑页单独修改。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("温柔动画",Modifier.weight(1f));Switch(checked=data.preferences.animations,onCheckedChange={value ->vm.update(transform={it.copy(preferences=it.preferences.copy(animations=value))})})}
        Text("轻微转场与按压反馈；周年当天有一次花瓣庆祝。系统关闭动画时自动停用。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("把回忆妥善收藏",fontWeight=FontWeight.SemiBold)
        InfoCard("本机已收藏 ${data.records.size} 个日子", "记录与照片仅保存在本机，不上传服务器。卸载会清除本地数据，请先导出备份并妥善保存。备份包含个人照片，请保存在可信位置。", Icons.Outlined.Inventory2)
        OutlinedButton(onClick={export.launch("与你-纪念日-${LocalDate.now()}.zip")},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.FileUpload,null);Spacer(Modifier.width(8.dp));Text("导出完整备份")}
        OutlinedButton(onClick={confirmRestore=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.FileDownload,null);Spacer(Modifier.width(8.dp));Text("从备份恢复")}
        InfoCard("在桌面，也能想起你", "长按桌面 → 小组件 → 与你 · 纪念日。可选择小、中、大布局，每个组件独立选择记录与配色；组件右下角可重新配置。实际格子由桌面决定，省电模式可能延迟跨日刷新。", Icons.Outlined.Widgets)
        Text("日期小约定",fontWeight=FontWeight.SemiBold)
        Text("使用手机当前时区的本地日历日期，跨日和返回前台自动重算。\n2 月 29 日在非闰年的周年日为 2 月 28 日。未来日期显示实际倒计时，起始日会明确提示“就是今天”。",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,lineHeight=24.sp)
        Text("与你 · 纪念日  ${BuildConfig.VERSION_NAME}\n好好记住，一起走过的日子。",modifier=Modifier.fillMaxWidth().padding(vertical=18.dp),textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)
    }
    if(confirmRestore) AlertDialog(onDismissRequest={confirmRestore=false},title={Text("用备份替换本机数据？")},text={Text("恢复成功后，当前所有记录与设置将被备份内容替换。建议先导出当前备份。无效备份不会覆盖现有记录。")},confirmButton={TextButton(onClick={confirmRestore=false;restore.launch(arrayOf("application/zip","application/octet-stream"))}) {Text("选择备份文件")}},dismissButton={TextButton(onClick={confirmRestore=false}){Text("取消")}})
}
