package com.numandegirmenci.belgeizi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF091321)
val Panel = Color(0xFF132235)
val PanelBright = Color(0xFF1A2D43)
val Accent = Color(0xFF68E1C1)
val TextMain = Color(0xFFF6F8FB)
val TextMuted = Color(0xFFA3B6C9)
val Danger = Color(0xFFFF8490)

private val palette = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    surface = Panel,
    onSurface = TextMain,
    background = Background,
    onBackground = TextMain,
    secondary = TextMuted,
    outline = PanelBright,
    error = Danger
)

@Composable
fun BelgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, content = content)
}
