package com.workouthacker.revamp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF04210F),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF1F1300),
    error = Color(0xFFFB7185),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111C31),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF152746),
    onSurfaceVariant = Color(0xFF9FB3D1),
    outline = Color(0xFF35507F),
)

@Composable
fun WorkoutHackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}