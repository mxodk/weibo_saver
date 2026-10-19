package com.mxodk.weibosaver.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ReaderTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when(appearance) { Appearance.SYSTEM -> isSystemInDarkTheme(); Appearance.LIGHT -> false; Appearance.DARK -> true }
    val scheme = if(dark) darkColorScheme(primary=Color(0xFF9AB8FF),background=Color(0xFF10151E),surface=Color(0xFF1C2532),
        onSurface=Color(0xFFE7EDF7),onBackground=Color(0xFFE7EDF7),surfaceContainer=Color(0xFF253041))
    else lightColorScheme(primary=Color(0xFF3865DF),background=Color(0xFFF3F5F9),surface=Color.White,
        onSurface=Color(0xFF202939),onBackground=Color(0xFF202939),surfaceContainer=Color(0xFFEAF0FB))
    MaterialTheme(colorScheme=scheme,content=content)
}
