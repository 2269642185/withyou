package com.withyou.anniversary

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalMotionEnabled=compositionLocalOf {true}

class CelebrationLedger(context: android.content.Context) {
    private val prefs=context.getSharedPreferences("anniversary_celebrations",android.content.Context.MODE_PRIVATE)
    fun claim(id: String, date: LocalDate): Boolean = synchronized(lock) {
        if(prefs.getString(id,null)==date.toString()) false else prefs.edit().putString(id,date.toString()).commit()
    }
    companion object {private val lock=Any()}
}

@Composable
fun rememberMotionEnabled(preference: Boolean): Boolean {
    val context=LocalContext.current
    fun systemEnabled()=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
    var system by remember {mutableStateOf(systemEnabled())}
    DisposableEffect(context) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {override fun onChange(selfChange: Boolean) {system=systemEnabled()}}
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose {context.contentResolver.unregisterContentObserver(observer)}
    }
    return preference && system
}

@Composable
fun PageMotion(page: String, content: @Composable () -> Unit) {
    val enabled=LocalMotionEnabled.current
    val progress=remember {Animatable(1f)}
    LaunchedEffect(page,enabled) {if(enabled) {progress.snapTo(.65f);progress.animateTo(1f,tween(240))} else progress.snapTo(1f)}
    Box(Modifier.fillMaxSize().graphicsLayer {alpha=progress.value;translationY=(1-progress.value)*18.dp.toPx()}) {content()}
}

@Composable
fun AnniversaryPetals(record: MemoryDay, today: LocalDate) {
    val enabled=LocalMotionEnabled.current
    val context=LocalContext.current
    var played by rememberSaveable(record.id,today.toString()) {mutableStateOf(false)}
    val progress=remember {Animatable(1f)}
    LaunchedEffect(record.id,today,enabled) {
        if(enabled && !played && ColorRules.isAnnualDay(record,today) && withContext(Dispatchers.IO) {CelebrationLedger(context).claim(record.id,today)}) {
            played=true;progress.snapTo(0f);progress.animateTo(1f,tween(1800))
        } else progress.snapTo(1f)
    }
    if(enabled && progress.value<1f) Canvas(Modifier.fillMaxSize()) {
        val p=progress.value
        repeat(14) {i ->
            val x=size.width*((i*37%100)/100f)+sin(p*6+i)*12.dp.toPx()
            val y=size.height*(p*.65f+(i%3)*.08f)
            drawOval(if(i%2==0) Color(0xFFD8A894).copy(alpha=(1-p)*.75f) else Color(0xFFE7C6CC).copy(alpha=(1-p)*.7f),
                topLeft=Offset(x,y),size=androidx.compose.ui.geometry.Size(7.dp.toPx(),12.dp.toPx()))
        }
    }
}
