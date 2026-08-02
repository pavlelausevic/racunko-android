package com.racunko.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette carried over from the validated web prototype. */
object Palette {
    val Bg = Color(0xFF12171F)
    val Card = Color(0xFF1B222C)
    val Card2 = Color(0xFF212A36)
    val Line = Color(0xFF2C3746)
    val Text = Color(0xFFE9EEF5)
    val Muted = Color(0xFF8C99AB)
    val Dim = Color(0xFF5C6B7E)
    val Amber = Color(0xFFE8A93D)
    val Blue = Color(0xFF5FA8F5)
    val Violet = Color(0xFFB48CF2)
    val Green = Color(0xFF45D39E)
    val Red = Color(0xFFF27E7E)
}

private val DarkScheme = darkColorScheme(
    primary = Palette.Blue,
    onPrimary = Palette.Bg,
    secondary = Palette.Amber,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Card,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Card2,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
    error = Palette.Red
)

@Composable
fun RacunkoTheme(content: @Composable () -> Unit) {
    // The app is dark-first by design (matches the prototype)
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
