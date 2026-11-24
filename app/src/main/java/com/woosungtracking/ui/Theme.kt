package com.woosungtracking.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Pine = Color(0xFF1F6B4A)
val Ember = Color(0xFFE8590C)

private val Light = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE8D8),
    onPrimaryContainer = Color(0xFF0B2E1F),
    secondary = Ember,
    onSecondary = Color.White,
    background = Color(0xFFF6F4EE),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEAE1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD1AE),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF145236),
    secondary = Color(0xFFFFB68A),
    background = Color(0xFF121411),
    surface = Color(0xFF1A1C19),
)

@Composable
fun WoosungTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
