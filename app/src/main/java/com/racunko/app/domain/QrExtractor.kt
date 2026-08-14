package com.racunko.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.racunko.app.parser.IpsQr
import com.racunko.platform.Engines
import kotlin.math.ceil

data class QrResult(val payload: String, val qrPng: Bitmap)

/**
 * Renders page 1 (then 2) with PdfRenderer at ~1750 px width and asks the
 * flavor's [com.racunko.platform.QrDecoder] for every QR payload, keeping the
 * NBS IPS one (rawValue starting `K:PR` — other barcodes on the bill are
 * ignored).
 *
 * The gallery PNG is then **regenerated** from the decoded payload via the
 * flavor's `QrEncoder` (round-trip proven by `IpsQrRoundTripTest`) rather than
 * cropped out of the page. That keeps the engine boundary clean — the decoder
 * interface only needs to return payloads, not bounding boxes — and yields a
 * crisp, uniformly-sized IPS QR regardless of the source scan quality.
 */
object QrExtractor {

    private const val QR_PX = 660

    suspend fun extract(context: Context, uri: Uri): QrResult? {
        val engines = Engines.instance ?: return null
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            try {
                val pages = minOf(2, renderer.pageCount)
                for (p in 0 until pages) {
                    val page = renderer.openPage(p)
                    val bmp: Bitmap
                    try {
                        val scale = minOf(3.2f, 1750f / page.width)
                        val w = ceil(page.width * scale).toInt()
                        val h = ceil(page.height * scale).toInt()
                        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        val m = Matrix().apply { setScale(scale, scale) }
                        page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } finally {
                        page.close()
                    }
                    // One page, decoded once — worth the slower sweep.
                    val payload = engines.qrDecoder.decodeThorough(bmp)
                        .firstOrNull { it.startsWith(IpsQr.PREFIX) }
                    bmp.recycle()
                    if (payload != null) {
                        val png = engines.qrEncoder.encode(payload, QR_PX)
                        return QrResult(payload, png)
                    }
                }
            } finally {
                renderer.close()
            }
        }
        return null
    }
}
