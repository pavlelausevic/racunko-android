package com.racunko.app.domain

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

/**
 * A scanned paper bill has no source PDF (8d). We generate a clean one-page PDF
 * embedding the freshly-generated IPS QR (+ minimal text), so the existing PDF
 * pipeline can re-read it exactly like any downloaded bill — the QR re-decodes,
 * fields parse, the file is renamed and stored normally. Its QR is marked
 * generated=true so the Change 1 disclaimer applies.
 */
object ScanArtifact {

    fun buildPdf(qr: Bitmap, lines: List<String>): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()) // ~A4 pt
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val title = Paint().apply {
            color = Color.BLACK; textSize = 22f; isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("NBS IPS QR", 297f, 90f, title)

        val size = 360
        val scaled = Bitmap.createScaledBitmap(qr, size, size, false)
        canvas.drawBitmap(scaled, (595 - size) / 2f, 130f, null)
        if (scaled != qr) scaled.recycle()

        val body = Paint().apply {
            color = Color.BLACK; textSize = 14f; isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        var y = 130f + size + 40f
        for (line in lines) {
            canvas.drawText(line.take(80), 297f, y, body)
            y += 22f
        }

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
