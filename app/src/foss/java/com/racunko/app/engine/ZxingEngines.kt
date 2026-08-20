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

private const val MIN_TILE = 120

private class ZxingQrDecoder : QrDecoder {

    override fun decode(bitmap: Bitmap): List<String> = read(bitmap, tryHarder = false)

    /**
     * TRY_HARDER makes ZXing sweep more rows and rotations. On a live camera
     * frame that cost lands on every frame that has no code — which is most of
     * them — so it is deliberately NOT the default; here it is right, because a
     * rendered page is decoded once and a miss means the app has to rebuild the
     * payment code from stored fields instead of reading the issuer's own.
     */
    override fun decodeThorough(bitmap: Bitmap): List<String> {
        val fast = read(bitmap, tryHarder = false)
        if (fast.isNotEmpty()) return fast
        val harder = read(bitmap, tryHarder = true)
        if (harder.isNotEmpty()) return harder
        return readTiled(bitmap)
    }

    /**
     * Last resort, and the reason the F-Droid flavor finally reads the InfoStan
     * bill it used to miss: sweep OVERLAPPING TILES instead of the whole page.
     *
     * `HybridBinarizer` picks its black/white threshold from the whole image and
     * the locator hunts finder patterns across it. An A4 bill is rendered ~1750 px
     * wide while its IPS code is only ~150 px of that — under 1% of the area, on a
     * page otherwise full of table rules and small print. Both stages are working
     * against a page that is mostly not the code. Inside a tile the same code is a
     * quarter of the frame, which is the situation ZXing is good at.
     *
     * Tiles overlap by half so a code cannot be lost by falling on a seam: any
     * code smaller than half a tile lies wholly inside at least one of them.
     * Reached ONLY after both whole-image passes fail, so the common bill pays
     * nothing for it, and never on the live camera path — `decode` does not call
     * this, and a scanner that sees ~30 frames a second must stay cheap.
     */
    private fun readTiled(bitmap: Bitmap): List<String> {
        val tileW = bitmap.width / 3
        val tileH = bitmap.height / 4
        if (tileW < MIN_TILE || tileH < MIN_TILE) return emptyList()
        val stepX = tileW / 2
        val stepY = tileH / 2
        var y = 0
        while (y + tileH <= bitmap.height) {
            var x = 0
            while (x + tileW <= bitmap.width) {
                val tile = runCatching {
                    Bitmap.createBitmap(bitmap, x, y, tileW, tileH)
                }.getOrNull()
                if (tile != null) {
                    val hit = try {
                        read(tile, tryHarder = true)
                    } finally {
                        if (tile !== bitmap) tile.recycle()
                    }
                    if (hit.isNotEmpty()) return hit
                }
                x += stepX
            }
            y += stepY
        }
        return emptyList()
    }

    private fun read(bitmap: Bitmap, tryHarder: Boolean): List<String> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        val hints = buildMap<DecodeHintType, Any> {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(com.google.zxing.BarcodeFormat.QR_CODE))
            if (tryHarder) put(DecodeHintType.TRY_HARDER, true)
        }
        return try {
            listOf(MultiFormatReader().decode(binary, hints).text)
        } catch (e: Exception) {
            emptyList() // NotFoundException etc. — no QR on this frame/page
        }
    }
}
