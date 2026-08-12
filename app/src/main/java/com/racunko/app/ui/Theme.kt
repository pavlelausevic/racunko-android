package com.racunko.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Deep-green surfaces with a single teal accent (the „mani" visual family), so
 * Računko reads as a companion to the household-budget app it complements
 * rather than a stranger next to it.
 *
 * The property names are unchanged on purpose — every screen already reads
 * `Palette.Blue` / `Palette.Amber`, so re-pointing the values shifts the whole
 * app at once. What each one MEANS is what matters:
 *
 *   Amber  → gold   : provider segment (infostan/eps/mts), warnings
 *   Blue   → teal   : THE accent — address segment, primary actions, links
 *   Violet → lime   : month segment (time)
 *   Green  → emerald: amounts, „plaćeno", success pills
 *
 * The four filename-segment hues stay far enough apart (gold 42° · teal 172° ·
 * lime 72° · emerald 158°) that the colour still carries meaning at a glance,
 * which is the point of the coloured segments in the first place.
 */
object Palette {
    /** Page background — deep green-black. */
    val Bg = Color(0xFF0B1512)
    /** Card / raised surface. */
    val Card = Color(0xFF12211C)
    /** Inner surface: segments, chips, fields. */
    val Card2 = Color(0xFF182C24)
    /** Hairline borders and dividers. */
    val Line = Color(0xFF21382E)

    val Text = Color(0xFFE7F0EA)
    val Muted = Color(0xFF8DA79A)
    val Dim = Color(0xFF5F7A6D)

    /** gold — provider segment, warnings, „Obradi". */
    val Amber = Color(0xFFE8B54A)
    /** teal — the accent: address segment, primary actions, links. */
    val Blue = Color(0xFF2DD4BF)
    /** lime — month segment. */
    val Violet = Color(0xFFB9D95E)
    /** emerald — amounts, paid state, success. */
    val Green = Color(0xFF34D399)
    /** soft red — errors, destructive actions. */
    val Red = Color(0xFFF87171)

    /**
     * The orange dot in „računko." — deliberately kept from the pre-mani
     * identity. It is the one mark that is ours and not borrowed, so it stays
     * its original orange rather than drifting into the gold above.
     */
    val Dot = Color(0xFFE8A93D)
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
    // The app is dark-first by design; the light system setting is not honoured
    // because every screen is tuned for the deep-green surface.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
