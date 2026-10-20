package com.withyou.anniversary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun PalettePicker(selected:AppPalette,enabled:Boolean=true,onSelect:(AppPalette)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        AppPalette.entries.chunked(2).forEach {pair ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                pair.forEach {palette ->
                    val preview=withYouColors(false,palette)
                    Surface(Modifier.weight(1f).testTag("palette_${palette.name}")
                        .selectable(selected==palette,enabled=enabled,role=Role.RadioButton,onClick={onSelect(palette)}),
                        shape=RoundedCornerShape(16.dp),color=if(selected==palette) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border=if(selected==palette) androidx.compose.foundation.BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                listOf(preview.primary,preview.primaryContainer,preview.surfaceVariant).forEach {color -> Box(Modifier.size(20.dp).background(color,CircleShape)) }
                            }
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(palette.label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
                                if(selected==palette) Icon(Icons.Outlined.CheckCircle,"已选中",Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
