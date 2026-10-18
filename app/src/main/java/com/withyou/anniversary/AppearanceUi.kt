package com.withyou.anniversary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwatchGrid(selected: String, swatches: List<ColorSwatch>, choose: (ColorSwatch) -> Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        swatches.forEach { s ->
            Surface(onClick={choose(s)},color=Color(ColorRules.argb(s.hex)),shape=CircleShape,
                border=BorderStroke(if(selected==s.hex) 3.dp else 1.dp,if(selected==s.hex) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha=.1f)),
                modifier=Modifier.size(48.dp).semantics {contentDescription="${s.name} ${s.hex}${if(selected==s.hex) " 已选" else ""}"}) {
                if(selected==s.hex) Box(contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Check,null,tint=Color(ColorRules.argb(ColorRules.autoInk(s.hex))))}
            }
        }
    }
}

@Composable
private fun HexField(label: String, value: String?, update: (String?) -> Unit, valid: (Boolean) -> Unit) {
    var raw by rememberSaveable(value) {mutableStateOf(value ?: "")}
    val okay=raw.isBlank() || ColorRules.normalize(raw)!=null
    LaunchedEffect(okay) {valid(okay)}
    DisposableEffect(Unit) {onDispose {valid(true)}}
    OutlinedTextField(value=raw,onValueChange={s -> raw=s.take(7);if(raw.isBlank())update(null) else ColorRules.normalize(raw)?.let(update)},label={Text(label)},
        supportingText={Text(if(okay) "#RRGGBB，留空则跟随默认" else "请输入 6 位 HEX，例如 #F0DCC8")},isError=!okay,singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceEditor(color: Int, appearance: CardAppearance, photo: Boolean, selectColor: (Int) -> Unit, change: (CardAppearance) -> Unit, valid: (Boolean) -> Unit) {
    var group by rememberSaveable {mutableStateOf(ColorRules.swatches[color].group)}
    var advanced by rememberSaveable {mutableStateOf(false)}
    var resetVersion by rememberSaveable {mutableIntStateOf(0)}
    val invalid=remember {mutableStateMapOf<String,Boolean>()}
    LaunchedEffect(invalid.values.toList()) {valid(invalid.values.none {it})}
    fun validity(key: String, okay: Boolean) {invalid[key]=!okay}
    Text("背景颜色",style=MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        ColorRules.groups.forEach { g -> FilterChip(selected=group==g,onClick={group=g},label={Text(g)}) }
    }
    SwatchGrid(appearance.background ?: ColorRules.swatches[color].hex,ColorRules.swatches.filter {it.group==group}) { s ->selectColor(ColorRules.swatches.indexOf(s));change(appearance.copy(background=null))}
    Text("文字颜色",style=MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TextTone.entries.filterNot {it==TextTone.CUSTOM}.forEach { t ->FilterChip(selected=appearance.tone==t,onClick={change(appearance.copy(tone=t,title=null,days=null,auxiliary=null))},label={Text(t.label)})}
    }
    SwatchGrid(if(appearance.tone==TextTone.CUSTOM) appearance.text ?: "" else "",ColorRules.textChoices.map {ColorSwatch("文字色",it,"")}) {s -> change(appearance.copy(tone=TextTone.CUSTOM,text=s.hex,title=null,days=null,auxiliary=null))}
    OutlinedButton(onClick={advanced=!advanced},modifier=Modifier.fillMaxWidth()) {Text(if(advanced) "收起高级配色" else "高级配色");Icon(Icons.Outlined.ExpandMore,null)}
    if(advanced) key(resetVersion) {
        HexField("自定义背景 HEX",appearance.background,{change(appearance.copy(background=it))},{validity("background",it)})
        HexField("统一文字 HEX",appearance.text,{change(appearance.copy(text=it,tone=if(it==null) TextTone.AUTO else TextTone.CUSTOM))},{validity("text",it)})
        HexField("标题颜色 HEX",appearance.title,{change(appearance.copy(title=it))},{validity("title",it)})
        HexField("天数颜色 HEX",appearance.days,{change(appearance.copy(days=it))},{validity("days",it)})
        HexField("辅助文字 HEX",appearance.auxiliary,{change(appearance.copy(auxiliary=it))},{validity("auxiliary",it)})
        if(photo) {
            Text("照片遮罩强度 · ${(appearance.overlay*100).toInt()}%")
            Slider(value=appearance.overlay,onValueChange={change(appearance.copy(overlay=it))},valueRange=0f..1f,
                modifier=Modifier.semantics {contentDescription="照片遮罩强度"})
            Text("自动文字会保留最低遮罩，帮助照片上的文字清晰可读。",style=MaterialTheme.typography.bodySmall)
        }
    }
    TextButton(onClick={selectColor(0);change(CardAppearance());invalid.clear();resetVersion++}) {Text("恢复默认配色")}
}
