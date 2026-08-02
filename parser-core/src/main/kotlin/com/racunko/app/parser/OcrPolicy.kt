package com.racunko.app.parser

/**
 * v1.1 Change 1: a PDF whose extracted text is shorter than 40 characters
 * (after trimming) is treated as image-based and routed to the OCR branch.
 */
object OcrPolicy {
    const val MIN_TEXT_LENGTH = 40

    fun needsOcr(text: String?): Boolean = (text?.trim()?.length ?: 0) < MIN_TEXT_LENGTH
}
