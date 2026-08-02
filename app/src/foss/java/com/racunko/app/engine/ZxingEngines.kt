package com.racunko.app.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.racunko.platform.DefaultLiveQrScanner
import com.racunko.platform.LiveQrScanner
import com.racunko.platform.PlatformEngines
import com.racunko.platform.QrDecoder
import com.racunko.platform.QrEncoder
import com.racunko.platform.TextRecognizer

/**
 * `foss` flavor engines (F-Droid): ZXing for QR decode and encode, Tesseract for
 * OCR (models bundled in the APK — zero runtime network). No proprietary engine
 * here or in main / parser-core.
 */
object EngineFactory {
    fun create(context: Context): PlatformEngines = ZxingEngines(context)
}

private class ZxingEngines(context: Context) : PlatformEngines {
    override val qrDecoder: QrDecoder = ZxingQrDecoder()
    override val qrEncoder: QrEncoder = ZxingQrEncoder()
    override val textRecognizer: TextRecognizer = TesseractTextRecognizer(context)
    override fun newLiveQrScanner(): LiveQrScanner = DefaultLiveQrScanner(qrDecoder)
}

private class ZxingQrDecoder : QrDecoder {
    override fun decode(bitmap: Bitmap): List<String> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE))
        return try {
            listOf(MultiFormatReader().decode(binary, hints).text)
        } catch (e: Exception) {
            emptyList() // NotFoundException etc. — no QR on this frame/page
        }
    }
}
