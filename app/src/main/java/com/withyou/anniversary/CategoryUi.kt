package com.withyou.anniversary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.util.UUID

object CategoryIcons {
    val keys = listOf("heart","family","cake","travel","flower","star")
    fun icon(key: String): ImageVector = when(key) {
        "family" -> Icons.Outlined.Home; "cake" -> Icons.Outlined.Cake
        "travel" -> Icons.Outlined.Flight; "flower" -> Icons.Outlined.Spa
        "star" -> Icons.Outlined.StarOutline; else -> Icons.Outlined.FavoriteBorder
    }
    val labels = listOf("爱心","家人","生日","旅行","花朵","星星")
}

@Composable
fun CategoryDialog(existing: Category?, categories: List<Category>, onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var icon by rememberSaveable(existing?.id) { mutableStateOf(existing?.icon ?: "heart") }
    var color by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.color ?: 0) }
    val duplicate=categories.any { it.id != existing?.id && it.name.equals(name.trim(),true) } || name.trim() in listOf("全部","未分类")
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing == null) "创建分类" else "编辑分类")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(name,{if(it.length<=20)name=it},label={Text("分类名称")},singleLine=true,isError=duplicate,
                supportingText={Text(if(duplicate) "这个分类名称已存在" else "1 至 20 字")})
            Text("选择图标")
            CategoryIcons.keys.chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {row.forEach { key ->
                FilterChip(selected=icon==key,onClick={icon=key},label={Icon(CategoryIcons.icon(key),CategoryIcons.labels[CategoryIcons.keys.indexOf(key)],Modifier.size(22.dp))})
            }} }
            Text("分类颜色")
            SwatchGrid(selected=ColorRules.swatches[color].hex,swatches=ColorRules.swatches.take(5)) { s -> color=ColorRules.swatches.indexOf(s) }
        }
    },confirmButton={TextButton(onClick={onSave(Category(existing?.id ?: UUID.randomUUID().toString(),name.trim(),icon,color))},enabled=name.isNotBlank()&&!duplicate){Text("保存分类")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable
fun CategoryManager(data: AppData, vm: MainViewModel, back: () -> Unit) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var dialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    Column(Modifier.fillMaxSize()) {
        PageHeading("管理分类",back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("把每一种心动，放进自己的小抽屉。",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={editing=null;dialog=true}) { Icon(Icons.Outlined.Add,null);Text("创建分类") }
            if(data.categories.isEmpty()) InfoCard("还没有分类", "可以创建恋爱、家人、旅行等分类。已有纪念日会保留在“未分类”中。",Icons.Outlined.FolderOpen)
            data.categories.forEach { c ->
                Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Surface(color=Color(ColorRules.argb(ColorRules.swatches[c.color].hex)),shape=androidx.compose.foundation.shape.CircleShape) {
                                Icon(CategoryIcons.icon(c.icon),null,Modifier.padding(10.dp),tint=Color(ColorRules.argb(ColorRules.autoInk(ColorRules.swatches[c.color].hex))))
                            }
                            Text("${c.name} · ${data.records.count {it.categoryId==c.id}}",Modifier.weight(1f))
                        }
                        Row(Modifier.align(Alignment.End)) {
                            TextButton(onClick={editing=c;dialog=true}) {Text("编辑")}
                            TextButton(onClick={deleting=c}) {Text("删除",color=MaterialTheme.colorScheme.error)}
                        }
                    }
                }
            }
        }
    }
    if(dialog) CategoryDialog(editing,data.categories,{dialog=false}) { c -> vm.saveCategory(c) { dialog=false } }
    deleting?.let { c -> AlertDialog(onDismissRequest={deleting=null},title={Text("删除分类“${c.name}”？")},text={Text("分类中的纪念日会保留，并转入“未分类”。")},confirmButton={TextButton(onClick={vm.deleteCategory(c.id);deleting=null}){Text("删除分类")}},dismissButton={TextButton(onClick={deleting=null}){Text("取消")}}) }
}
