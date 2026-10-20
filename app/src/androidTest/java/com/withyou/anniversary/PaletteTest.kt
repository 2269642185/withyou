package com.withyou.anniversary

import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

class PaletteTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun twelveSchemesHaveReadableTextAndNoPurpleFallbackRoles() {
        AppPalette.entries.forEach {palette -> listOf(false,true).forEach {dark ->
            val c=withYouColors(dark,palette)
            listOf(c.onSurface to c.surface,c.onBackground to c.background,c.onSurfaceVariant to c.surfaceVariant,
                c.onPrimary to c.primary,c.onPrimaryContainer to c.primaryContainer,c.onSecondaryContainer to c.secondaryContainer,
                c.onSurface to c.surfaceContainerHigh,c.inverseOnSurface to c.inverseSurface).forEach {(ink,bg) ->
                assertTrue("${palette.name} dark=$dark contrast",ColorUtils.calculateContrast(ink.toArgb(),bg.toArgb())>=4.5)
            }
            assertEquals(c.primary,c.secondary);assertEquals(c.primary,c.tertiary)
        }}
    }
    @Test fun savedPaletteRoundTripsAndOldBackupsKeepCardAppearance()=runBlocking {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(base.cacheDir,"palette-test-${System.nanoTime()}").apply {mkdirs()}
        val repo=Repository(object:ContextWrapper(base) {override fun getFilesDir()=dir})
        val record=MemoryDay(UUID.randomUUID().toString(),"我的日子",LocalDate.now(),appearance=CardAppearance(background="#E1F2E7",days="#35597F"))
        AppPalette.entries.forEach {palette ->
            val data=AppData(listOf(record),Preferences(AppTheme.DARK,palette=palette))
            repo.save(data);assertEquals(data,Repository(object:ContextWrapper(base) {override fun getFilesDir()=dir}).read())
        }
        val old=JSONObject(Repository.encode(AppData(listOf(record)))).apply {remove("palette")}
        val decoded=Repository.decode(old.toString())
        assertEquals(AppPalette.CREAM,decoded.preferences.palette);assertEquals(record,decoded.records.single())
        old.put("palette","FUTURE_UNSUPPORTED_PALETTE")
        assertEquals(AppPalette.CREAM,Repository.decode(old.toString()).preferences.palette)
    }
    @Test fun largeFontPickerWorksAcrossAllLightAndDarkPalettes() {
        var selected by mutableStateOf(AppPalette.CREAM);var dark by mutableStateOf(false)
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,1.6f)) {
                MaterialTheme(colorScheme=withYouColors(dark,selected)) {
                    Surface(Modifier.width(320.dp).fillMaxHeight()) {Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {PalettePicker(selected) {selected=it}}}
                }
            }
        }
        val inst=InstrumentationRegistry.getInstrumentation()
        listOf(false,true).forEach {mode ->
            compose.runOnIdle {dark=mode}
            AppPalette.entries.forEach {palette ->
                compose.onNodeWithTag("palette_${palette.name}").performScrollTo().performClick().assertIsSelected()
                val bounds=compose.onNodeWithTag("palette_${palette.name}").fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left>=0 && bounds.width>0)
                compose.waitForIdle()
                android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/withyou-v18-palette-${palette.name}-$mode.png")).use {it.readBytes()}
            }
        }
    }
}
