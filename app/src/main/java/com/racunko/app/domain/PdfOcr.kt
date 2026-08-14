package com.racunko.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.racunko.platform.Engines
import kotlin.math.ceil

/**
 * v1.1 Change 1: OCR branch for image-based PDFs (e.g. Erste confirmations —
 * a JPEG wrapped in a PDF with zero text layer). Renders pages 1–2 at >= 2000 px
 * width and runs [com.racunko.platform.TextRecognizer], which since v1.7 is
 * Tesseract in BOTH flavors (D1) — on-device either way.
 *
 * That >= 2000 px is not decoration: it is what lets the small print survive.
 * `Pipeline.scaledForOcr` applies the same floor to plain images, which until
 * v1.7 were fed at native size and lost their smallest rows.
 */
object PdfOcr {

    suspend fun ocr(context: Context, uri: Uri): String {
        val recognizer = Engines.instance?.textRecognizer ?: return ""
        val sb = StringBuilder()
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val renderer = PdfRenderer(pfd)
            try {
                val pages = minOf(2, renderer.pageCount)
                for (p in 0 until pages) {
                    val page = renderer.openPage(p)
                    val bmp: Bitmap
                    try {
                        val scale = maxOf(1f, 2000f / page.width)
                        val w = ceil(page.width * scale).toInt()
                        val h = ceil(page.height * scale).toInt()
                        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        val m = Matrix().apply { setScale(scale, scale) }
                        page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    } finally {
                        page.close()
                    }
                    sb.append(recognizer.recognize(bmp)).append('\n')
                    bmp.recycle()
                }
            } finally {
                renderer.close()
            }
        }
        return sb.toString()
    }
}
