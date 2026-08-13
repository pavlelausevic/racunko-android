package com.racunko.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The app's icon set, drawn in code.
 *
 * Material Symbols shapes on the standard 24×24 grid, but only the ~20 glyphs
 * Računko actually uses — pulling in `material-icons-extended` for that would
 * ship thousands of unused vectors. Every icon is a single stroked path at a
 * uniform 1.7 px weight, so the whole UI has one line quality, and `Icon(…)`
 * tints them from the palette like any other vector.
 */
object RIcons {

    private const val W = 1.7f

    private fun stroked(name: String, body: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = W,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = body
            )
        }.build()

    /** Two half-arcs — PathBuilder has no circle primitive. */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    val Close: ImageVector = stroked("close") {
        moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
    }

    val Check: ImageVector = stroked("check") {
        moveTo(5f, 12.6f); lineTo(9.8f, 17.4f); lineTo(19f, 6.6f)
    }

    val Add: ImageVector = stroked("add") {
        moveTo(12f, 5.5f); lineTo(12f, 18.5f)
        moveTo(5.5f, 12f); lineTo(18.5f, 12f)
    }

    val ExpandMore: ImageVector = stroked("expand_more") {
        moveTo(6.5f, 9.5f); lineTo(12f, 15f); lineTo(17.5f, 9.5f)
    }

    val ChevronRight: ImageVector = stroked("chevron_right") {
        moveTo(9.5f, 6.5f); lineTo(15f, 12f); lineTo(9.5f, 17.5f)
    }

    /** Circular arrow, open at the top-right, with the arrowhead on that gap. */
    val Refresh: ImageVector = stroked("refresh") {
        moveTo(19f, 12f)
        arcToRelative(7f, 7f, 0f, true, true, -2.05f, -4.95f)
        moveTo(19f, 4.2f); lineTo(19f, 8f); lineTo(15.2f, 8f)
    }

    val ArrowBack: ImageVector = stroked("arrow_back") {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }

    val Delete: ImageVector = stroked("delete") {
        moveTo(4.5f, 6.8f); lineTo(19.5f, 6.8f)
        moveTo(9.5f, 6.8f); lineTo(9.5f, 4.6f); lineTo(14.5f, 4.6f); lineTo(14.5f, 6.8f)
        moveTo(6.4f, 6.8f); lineTo(7.3f, 19.6f); lineTo(16.7f, 19.6f); lineTo(17.6f, 6.8f)
        moveTo(10.2f, 10.3f); lineTo(10.2f, 16.3f)
        moveTo(13.8f, 10.3f); lineTo(13.8f, 16.3f)
    }

    val Share: ImageVector = stroked("share") {
        circle(18f, 5.4f, 2.1f)
        circle(6f, 12f, 2.1f)
        circle(18f, 18.6f, 2.1f)
        moveTo(7.9f, 10.9f); lineTo(16.1f, 6.5f)
        moveTo(7.9f, 13.1f); lineTo(16.1f, 17.5f)
    }

    val Copy: ImageVector = stroked("copy") {
        moveTo(9.2f, 8.4f); lineTo(19.4f, 8.4f); lineTo(19.4f, 20f); lineTo(9.2f, 20f); close()
        moveTo(15.6f, 8.4f); lineTo(15.6f, 4f); lineTo(4.6f, 4f); lineTo(4.6f, 15.6f); lineTo(9.2f, 15.6f)
    }

    /** „Tune" sliders — reads as settings without a 20-segment gear outline. */
    val Settings: ImageVector = stroked("settings") {
        moveTo(4f, 7f); lineTo(6.9f, 7f)
        moveTo(11.1f, 7f); lineTo(20f, 7f)
        circle(9f, 7f, 2.1f)
        moveTo(4f, 12.5f); lineTo(12.9f, 12.5f)
        moveTo(17.1f, 12.5f); lineTo(20f, 12.5f)
        circle(15f, 12.5f, 2.1f)
        moveTo(4f, 18f); lineTo(6.9f, 18f)
        moveTo(11.1f, 18f); lineTo(20f, 18f)
        circle(9f, 18f, 2.1f)
    }

    val Camera: ImageVector = stroked("camera") {
        moveTo(3.2f, 8.2f); lineTo(7.4f, 8.2f); lineTo(9.2f, 5.6f); lineTo(14.8f, 5.6f)
        lineTo(16.6f, 8.2f); lineTo(20.8f, 8.2f); lineTo(20.8f, 19.2f); lineTo(3.2f, 19.2f); close()
        circle(12f, 13.4f, 3.6f)
    }

    val Image: ImageVector = stroked("image") {
        moveTo(3.6f, 5.2f); lineTo(20.4f, 5.2f); lineTo(20.4f, 18.8f); lineTo(3.6f, 18.8f); close()
        circle(8.6f, 9.6f, 1.5f)
        moveTo(4.6f, 17.2f); lineTo(9.6f, 12.2f); lineTo(13f, 15.6f); lineTo(15.8f, 12.8f); lineTo(19.4f, 16.4f)
    }

    val Document: ImageVector = stroked("document") {
        moveTo(6.2f, 3.2f); lineTo(14f, 3.2f); lineTo(18.8f, 8f); lineTo(18.8f, 20.8f)
        lineTo(6.2f, 20.8f); close()
        moveTo(14f, 3.2f); lineTo(14f, 8f); lineTo(18.8f, 8f)
        moveTo(9.2f, 13f); lineTo(15.8f, 13f)
        moveTo(9.2f, 16.4f); lineTo(15.8f, 16.4f)
    }

    val QrCode: ImageVector = stroked("qr_code") {
        moveTo(4f, 4f); lineTo(9.8f, 4f); lineTo(9.8f, 9.8f); lineTo(4f, 9.8f); close()
        moveTo(14.2f, 4f); lineTo(20f, 4f); lineTo(20f, 9.8f); lineTo(14.2f, 9.8f); close()
        moveTo(4f, 14.2f); lineTo(9.8f, 14.2f); lineTo(9.8f, 20f); lineTo(4f, 20f); close()
        moveTo(14.2f, 14.2f); lineTo(14.2f, 17f)
        moveTo(17.2f, 14.2f); lineTo(20f, 14.2f)
        moveTo(17.2f, 17.2f); lineTo(17.2f, 20f)
        moveTo(20f, 20f); lineTo(20f, 17.2f)
    }

    val Clock: ImageVector = stroked("clock") {
        circle(12f, 12f, 8.2f)
        moveTo(12f, 7.2f); lineTo(12f, 12.4f); lineTo(15.6f, 14.4f)
    }

    val Bell: ImageVector = stroked("bell") {
        moveTo(12f, 3.2f); lineTo(12f, 5.4f)
        moveTo(17.2f, 15.4f); lineTo(18.8f, 17.6f); lineTo(5.2f, 17.6f); lineTo(6.8f, 15.4f)
        lineTo(6.8f, 11.2f)
        curveTo(6.8f, 7.9f, 9.1f, 5.6f, 12f, 5.6f)
        curveTo(14.9f, 5.6f, 17.2f, 7.9f, 17.2f, 11.2f)
        close()
        moveTo(10f, 19.8f)
        curveTo(10.6f, 21.1f, 13.4f, 21.1f, 14f, 19.8f)
    }

    val Torch: ImageVector = stroked("torch") {
        moveTo(8.6f, 3.4f); lineTo(15.4f, 3.4f); lineTo(15.4f, 7f); lineTo(13.8f, 9.2f)
        lineTo(13.8f, 20.4f); lineTo(10.2f, 20.4f); lineTo(10.2f, 9.2f); lineTo(8.6f, 7f); close()
        moveTo(8.6f, 6.2f); lineTo(15.4f, 6.2f)
        moveTo(12f, 12.6f); lineTo(12f, 14.8f)
    }

    val Info: ImageVector = stroked("info") {
        circle(12f, 12f, 8.4f)
        moveTo(12f, 11f); lineTo(12f, 16.4f)
        moveTo(12f, 7.7f); lineTo(12f, 8.1f)
    }

    val Report: ImageVector = stroked("report") {
        moveTo(5f, 6.8f); lineTo(19f, 6.8f)
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(5f, 17.2f); lineTo(13.4f, 17.2f)
    }

    val Calendar: ImageVector = stroked("calendar") {
        moveTo(4.2f, 6.4f); lineTo(19.8f, 6.4f); lineTo(19.8f, 20f); lineTo(4.2f, 20f); close()
        moveTo(4.2f, 10.4f); lineTo(19.8f, 10.4f)
        moveTo(8.6f, 4f); lineTo(8.6f, 7.6f)
        moveTo(15.4f, 4f); lineTo(15.4f, 7.6f)
    }
}
