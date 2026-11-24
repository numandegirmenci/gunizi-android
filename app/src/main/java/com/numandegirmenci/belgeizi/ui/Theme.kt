package com.numandegirmenci.belgeizi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0A111A)
val Panel = Color(0xFF14212E)
val PanelBright = Color(0xFF243747)
val Accent = Color(0xFF70E5C5)
val TextMain = Color(0xFFF5F8FC)
val TextMuted = Color(0xFF9EB2C4)
val Danger = Color(0xFFFF8C9A)

private val palette = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    surface = Panel,
    onSurface = TextMain,
    background = Background,
    onBackground = TextMain,
    secondary = TextMuted,
    outline = PanelBright,
    surfaceVariant = PanelBright,
    onSurfaceVariant = TextMuted,
    error = Danger
)

@Composable
fun BelgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, content = content)
}
