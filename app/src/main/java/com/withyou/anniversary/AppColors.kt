package com.withyou.anniversary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

private data class ThemeTones(val primary: Long, val tint: Long, val background: Long, val surface: Long,
    val variant: Long, val ink: Long, val muted: Long, val outline: Long)

fun withYouColors(dark: Boolean, palette: AppPalette = AppPalette.CREAM): ColorScheme {
    val t = if(dark) when(palette) {
        AppPalette.CREAM -> ThemeTones(0xFFE8BA9B,0xFF574034,0xFF211D1A,0xFF2D2722,0xFF40362F,0xFFF5EAE1,0xFFD0BFB0,0xFFAB9484)
        AppPalette.MINT -> ThemeTones(0xFF9CD6BF,0xFF264E40,0xFF171F1B,0xFF202C26,0xFF2F4037,0xFFE4F1E8,0xFFB5CCBF,0xFF8BA99A)
        AppPalette.ROSE -> ThemeTones(0xFFEDB4C3,0xFF593442,0xFF251C20,0xFF31252B,0xFF45323B,0xFFF9E7EC,0xFFD8BBC5,0xFFB693A1)
        AppPalette.BLUE -> ThemeTones(0xFFA9CDDE,0xFF2C4858,0xFF192025,0xFF242D34,0xFF33424D,0xFFE5EFF6,0xFFBACCD8,0xFF91A9B9)
        AppPalette.LAVENDER -> ThemeTones(0xFFCDBDEB,0xFF493B60,0xFF211C29,0xFF2C2636,0xFF3E344C,0xFFF0E9FA,0xFFC9BDD9,0xFFA99ABC)
        AppPalette.OAT -> ThemeTones(0xFFD7C1A5,0xFF504333,0xFF211E19,0xFF2D2922,0xFF40392E,0xFFF1EBE0,0xFFCFC3B1,0xFFAA9B85)
    } else when(palette) {
        AppPalette.CREAM -> ThemeTones(0xFF956347,0xFFF1DFCF,0xFFFFFAF4,0xFFFFFDFC,0xFFF2EAE0,0xFF42362D,0xFF716258,0xFF9A8372)
        AppPalette.MINT -> ThemeTones(0xFF326B55,0xFFD5EDE0,0xFFF5FBF6,0xFFFCFEFC,0xFFE5F0E8,0xFF263C31,0xFF536C5E,0xFF7C9988)
        AppPalette.ROSE -> ThemeTones(0xFF96536B,0xFFF4DDE5,0xFFFFF7F8,0xFFFFFDFD,0xFFF4E8EC,0xFF49323B,0xFF795D69,0xFFA78393)
        AppPalette.BLUE -> ThemeTones(0xFF3C687F,0xFFD9EAF3,0xFFF5FAFD,0xFFFCFEFF,0xFFE5EEF4,0xFF2D3D48,0xFF526B7B,0xFF839DAD)
        AppPalette.LAVENDER -> ThemeTones(0xFF75578F,0xFFEBDFF4,0xFFFBF7FE,0xFFFFFDFF,0xFFF0E9F5,0xFF40334D,0xFF715F80,0xFF9C88AD)
        AppPalette.OAT -> ThemeTones(0xFF756044,0xFFEBE1D1,0xFFFBF9F3,0xFFFFFEFA,0xFFF0EBE0,0xFF40392C,0xFF706551,0xFF9E9078)
    }
    val primary=Color(t.primary);val tint=Color(t.tint);val ink=Color(t.ink)
    val base=if(dark) darkColorScheme() else lightColorScheme()
    return base.copy(primary=primary,onPrimary=if(dark) Color(t.background) else Color.White,
        primaryContainer=tint,onPrimaryContainer=ink,secondary=primary,onSecondary=if(dark) Color(t.background) else Color.White,
        secondaryContainer=tint,onSecondaryContainer=ink,tertiary=primary,onTertiary=if(dark) Color(t.background) else Color.White,
        tertiaryContainer=tint,onTertiaryContainer=ink,background=Color(t.background),onBackground=ink,
        surface=Color(t.surface),onSurface=ink,surfaceVariant=Color(t.variant),onSurfaceVariant=Color(t.muted),
        outline=Color(t.outline),outlineVariant=Color(t.outline).copy(alpha=.45f),surfaceTint=primary,
        inverseSurface=ink,inverseOnSurface=Color(t.background),inversePrimary=Color(t.background),
        surfaceBright=Color(t.surface),surfaceDim=Color(t.background),surfaceContainerLowest=Color(t.background),
        surfaceContainerLow=Color(t.surface),surfaceContainer=Color(t.variant),surfaceContainerHigh=tint,surfaceContainerHighest=Color(t.variant))
}

/** Animate semantic roles together without touching card-specific colors. */
@Composable
internal fun animatedAppColors(target:ColorScheme,enabled:Boolean):ColorScheme {
    @Composable fun fade(color:Color):Color = animateColorAsState(color,tween(if(enabled) 240 else 0),label="主题配色").value
    return target.copy(primary=fade(target.primary),onPrimary=fade(target.onPrimary),primaryContainer=fade(target.primaryContainer),
        onPrimaryContainer=fade(target.onPrimaryContainer),secondary=fade(target.secondary),onSecondary=fade(target.onSecondary),
        secondaryContainer=fade(target.secondaryContainer),onSecondaryContainer=fade(target.onSecondaryContainer),tertiary=fade(target.tertiary),
        onTertiary=fade(target.onTertiary),tertiaryContainer=fade(target.tertiaryContainer),onTertiaryContainer=fade(target.onTertiaryContainer),
        background=fade(target.background),onBackground=fade(target.onBackground),surface=fade(target.surface),onSurface=fade(target.onSurface),
        surfaceVariant=fade(target.surfaceVariant),onSurfaceVariant=fade(target.onSurfaceVariant),outline=fade(target.outline),
        outlineVariant=fade(target.outlineVariant),surfaceTint=fade(target.surfaceTint),inverseSurface=fade(target.inverseSurface),
        inverseOnSurface=fade(target.inverseOnSurface),inversePrimary=fade(target.inversePrimary),surfaceBright=fade(target.surfaceBright),
        surfaceDim=fade(target.surfaceDim),surfaceContainerLowest=fade(target.surfaceContainerLowest),surfaceContainerLow=fade(target.surfaceContainerLow),
        surfaceContainer=fade(target.surfaceContainer),surfaceContainerHigh=fade(target.surfaceContainerHigh),surfaceContainerHighest=fade(target.surfaceContainerHighest))
}
