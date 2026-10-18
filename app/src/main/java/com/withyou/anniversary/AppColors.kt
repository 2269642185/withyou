package com.withyou.anniversary

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

fun withYouColors(dark: Boolean) = if(dark) darkColorScheme(
    primary=Color(0xFFE8BA9B),onPrimary=Color(0xFF49392D),primaryContainer=Color(0xFF574034),secondaryContainer=Color(0xFF574034),onSecondaryContainer=Color(0xFFF4D9C5),
    background=Color(0xFF211D1A),surface=Color(0xFF2D2722),surfaceVariant=Color(0xFF40362F),onSurface=Color(0xFFF5EAE1),onSurfaceVariant=Color(0xFFC9B8A9)
) else lightColorScheme(
    primary=Color(0xFF956347),onPrimary=Color.White,primaryContainer=Color(0xFFF1DFCF),secondaryContainer=Color(0xFFF1DFCF),onSecondaryContainer=Color(0xFF795443),
    background=Color(0xFFFFFAF4),surface=Color(0xFFFFFDFC),surfaceVariant=Color(0xFFF2EAE0),onSurface=Color(0xFF42362D),onSurfaceVariant=Color(0xFF817267),outline=Color(0xFFAA9687)
)
