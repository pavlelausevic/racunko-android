package com.racunko.app.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.racunko.platform.LiveQrScanner
import com.racunko.platform.PlatformEngines
import com.racunko.platform.QrDecoder
import com.racunko.platform.QrEncoder
import com.racunko.platform.TextRecognizer
import com.racunko.platform.DefaultLiveQrScanner

/**
 * `gms` flavor engines (Play Store): ML Kit for BARCODE decoding, ZXing for QR
 * encoding, Tesseract for OCR. Bundled, on-device, no network.
 *
 * v1.7: OCR moved off ML Kit. Its recognizer is Latin-script and has no Cyrillic
 * model to add — and every bill a public utility prints here is Cyrillic, so on
 * a screenshot of one it read nothing usable. Tesseract carries `srp`, and the
 * swap costs no size: ML Kit's OCR native libraries were 17 MB against
 * Tesseract's whole 16 MB. ML Kit stays where it is genuinely better and has no
 * substitute in this flavor — reading the QR.
 */
object EngineFactory {
    fun create(context: Context): PlatformEngines = MlKitEngines(context)
}

private class MlKitEngines(context: Context) : PlatformEngines {
    override val qrDecoder: QrDecoder = MlKitQrDecoder()
    override val qrEncoder: QrEncoder = ZxingQrEncoder()
    override val textRecognizer: TextRecognizer = TesseractTextRecognizer(context)
    override fun newLiveQrScanner(): LiveQrScanner = DefaultLiveQrScanner(qrDecoder)
}

private class MlKitQrDecoder : QrDecoder {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )

    override fun decode(bitmap: Bitmap): List<String> {
        // Synchronous by design (called from a suspend fun on Dispatchers.IO).
        val result = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)))
        return result.mapNotNull { it.rawValue }
    }
}
