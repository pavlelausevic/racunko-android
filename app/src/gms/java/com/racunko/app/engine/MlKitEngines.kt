package com.racunko.app.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.racunko.platform.LiveQrScanner
import com.racunko.platform.PlatformEngines
import com.racunko.platform.QrDecoder
import com.racunko.platform.QrEncoder
import com.racunko.platform.TextRecognizer
import com.racunko.platform.DefaultLiveQrScanner
import kotlinx.coroutines.tasks.await

/**
 * `gms` flavor engines (Play Store): ML Kit barcode + text recognition (bundled,
 * on-device, no network) for decode/OCR; ZXing for QR encode. No proprietary
 * call appears in main / parser-core — only here.
 */
object EngineFactory {
    fun create(context: Context): PlatformEngines = MlKitEngines()
}

private class MlKitEngines : PlatformEngines {
    override val qrDecoder: QrDecoder = MlKitQrDecoder()
    override val qrEncoder: QrEncoder = ZxingQrEncoder()
    override val textRecognizer: TextRecognizer = MlKitTextRecognizer()
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

private class MlKitTextRecognizer : TextRecognizer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): String =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
}
