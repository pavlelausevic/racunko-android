package com.racunko.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Neutral near-black surfaces, one teal accent, everything else carried by type.
 *
 * The palette that came before this one gave each filename segment its own hue —
 * gold for the provider, teal for the address, lime for the month, emerald for
 * the amount. It was legible to someone who had been told what the colours mean
 * and busy to everyone else, and it tinted the surfaces green on top of that.
 *
 * The rule now: **colour marks state, never category.** A card is grey until
 * something about it needs a person. Hierarchy comes from size and weight, which
 * every reader already understands without being taught.
 *
 *   Blue  → teal  : THE accent. Actions, links, active state, selection.
 *   Amber → amber : attention. A field we could not prove, a value we guessed.
 *   Green → green : settled state. Paid, verified, checksum passed.
 *   Red   → red   : errors and destructive actions.
 *
 * The property names are unchanged on purpose — every screen already reads
 * `Palette.Blue` / `Palette.Amber`, so re-pointing the values shifts the whole
 * app at once.
 */
object Palette {
    /** Page background — near-black, deliberately untinted. */
    val Bg = Color(0xFF0C0C0D)
    /** Card / raised surface. */
    val Card = Color(0xFF17181A)
    /** Inner surface: fields, pressed states, the selected segment of a switch. */
    val Card2 = Color(0xFF212327)
    /** Hairline borders and dividers. */
    val Line = Color(0xFF2B2D32)

    val Text = Color(0xFFF3F4F5)
    val Muted = Color(0xFF9BA0A6)
    val Dim = Color(0xFF6C7076)

    /** teal — the one accent: primary actions, links, active state, selection. */
    val Blue = Color(0xFF14C8B4)
    /** amber — attention: an unproven field, a guessed value, a caveat. */
    val Amber = Color(0xFFE9B949)
    /** green — settled: paid, paired, checksum verified. */
    val Green = Color(0xFF46B98A)
    /** soft red — errors, destructive actions. */
    val Red = Color(0xFFEF6E6E)

    /**
     * The orange dot in „računko." — carried over from the identity this palette
     * replaced. It is the one mark that survives every repaint, so it stays its
     * original orange instead of drifting into the amber above.
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
    // because every screen is tuned for the near-black surface.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
