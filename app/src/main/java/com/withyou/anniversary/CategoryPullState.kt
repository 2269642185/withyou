package com.withyou.anniversary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** A release-to-toggle gesture. Eligibility is fixed at the start of each touch gesture,
 * so scrolling up to the top cannot also reveal the panel in that same gesture. */
class CategoryPullState(private val threshold: Float, private val atTop: () -> Boolean) {
    var expanded by mutableStateOf(false)
    private var startedAtTop: Boolean? = null
    private var distance = 0f

    private fun reset() { startedAtTop=null;distance=0f }

    val connection=object:NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if(source!=NestedScrollSource.UserInput || abs(available.y)<=abs(available.x)) return Offset.Zero
            if(startedAtTop==null) startedAtTop=atTop()
            if(startedAtTop!=true) return Offset.Zero
            if(expanded && (available.y<0 || distance>0)) {
                distance=(distance-available.y).coerceIn(0f,threshold*2)
                return Offset(0f,available.y)
            }
            if(!expanded && distance>0 && available.y<0) {
                distance=(distance+available.y).coerceAtLeast(0f)
                return Offset(0f,available.y)
            }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if(source==NestedScrollSource.UserInput && startedAtTop==true && available.y>0 && atTop()) {
                if(!expanded) distance=(distance+available.y).coerceAtMost(threshold*2)
                return Offset(0f,available.y)
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            val captured=distance>0
            if(startedAtTop==true && distance>=threshold) expanded=!expanded
            reset()
            return if(captured) Velocity(0f,available.y) else Velocity.Zero
        }
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            reset();return Velocity.Zero
        }
    }
}
