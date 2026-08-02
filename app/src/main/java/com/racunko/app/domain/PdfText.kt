package com.racunko.app.domain

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** PDF text extraction (pages 1–3) via pdfbox-android. */
object PdfText {

    fun extract(context: Context, uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                if (doc.numberOfPages == 0) return ""
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = minOf(3, doc.numberOfPages)
                return stripper.getText(doc) ?: ""
            }
        }
        return ""
    }
}
