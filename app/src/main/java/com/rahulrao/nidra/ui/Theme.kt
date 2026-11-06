package com.rahulrao.nidra.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Two complete looks: Voltage (dark) and Aurora (light). */
data class Palette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val track: Color,
    val flame: Color,
    val flameCore: Color,
    val bowl: Color,
    val danger: Color,
)

val Voltage = Palette(
    background = Color(0xFF08080A),
    surface = Color(0xFF141417),
    text = Color(0xFFF7F7F2),
    muted = Color(0xFFA8A8A0),
    primary = Color(0xFFFFD60A),
    onPrimary = Color(0xFF08080A),
    accent = Color(0xFF4ADE80),
    track = Color(0xFF2A2A2E),
    flame = Color(0xFFFFD60A),
    flameCore = Color(0xFFF7F7F2),
    bowl = Color(0xFF55555C),
    danger = Color(0xFFFF6B6B),
)

val Aurora = Palette(
    background = Color(0xFFFBFBF9),
    surface = Color(0xFFF0F1F3),
    text = Color(0xFF14161C),
    muted = Color(0xFF5A5E68),
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFFEA6A1F),
    track = Color(0xFFE4E6EA),
    flame = Color(0xFFEA6A1F),
    flameCore = Color(0xFFFAC775),
    bowl = Color(0xFF9AA0AB),
    danger = Color(0xFFC2261D),
)

val LocalPalette = staticCompositionLocalOf { Voltage }

@Composable
fun NidraTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) Voltage else Aurora
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.text,
            onSurface = palette.text,
            onSurfaceVariant = palette.muted,
            outline = palette.muted,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            background = palette.background,
            surface = palette.surface,
            onBackground = palette.text,
            onSurface = palette.text,
            onSurfaceVariant = palette.muted,
            outline = palette.muted,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
