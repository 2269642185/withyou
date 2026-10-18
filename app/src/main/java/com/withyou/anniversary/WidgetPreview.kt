package com.withyou.anniversary

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WidgetPreview(id:Int,record:MemoryDay,choice:WidgetChoice,kind:Int) {
    val context=LocalContext.current
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        val geometry=WidgetGeometry.defaults(kind)
        val width=minOf(maxWidth.value,geometry.width)
        val height=width*geometry.height/geometry.width
        val views by produceState<RemoteViews?>(null,record,choice,kind,width,density) {
            value=withContext(Dispatchers.IO) {AnniversaryWidget.render(context,id,record,kind,choice,width=width,height=height)}
        }
        Box(Modifier.width(width.dp).height(height.dp).testTag("widget_preview")) {
            views?.let {remote ->AndroidView(factory={FrameLayout(it)},update={frame ->
                frame.removeAllViews();frame.addView(remote.apply(context,frame),FrameLayout.LayoutParams(-1,-1))
            },modifier=Modifier.fillMaxSize())} ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
            // Preview taps never open the live widget's record/configuration PendingIntents.
            Box(Modifier.matchParentSize().clickable {})
        }
    }
}
