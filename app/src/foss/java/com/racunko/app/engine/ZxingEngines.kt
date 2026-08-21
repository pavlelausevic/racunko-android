package com.racunko.app.engine

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.racunko.platform.DefaultLiveQrScanner
import com.racunko.platform.LiveQrScanner
import com.racunko.platform.PlatformEngines
import com.racunko.app.parser.IpsQr
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
    /**
     * EVERY code on the page, not the first one found — and reading them with
     * ZXing's QR-specific multi-reader, not the generic one. Both halves of that
     * are load-bearing, and each was measured on 67 real bills.
     *
     * *Every* code: an InfoStan bill carries TWO QR codes, the IPS payment code
     * and a larger, cleaner marketing code pointing at the issuer's app. A reader
     * that returns ONE result hands back the marketing link, `QrExtractor` finds
     * no `K:PR` among the results and reports NO QR AT ALL — and the bill, which
     * plainly has a payment code, falls through to „račun ili potvrda?".
     *
     * *QR-specific*: `GenericMultipleBarcodeReader` runs one single-code detector
     * and then re-crops around what it found, so it inherits that detector's one
     * shot at picking the three finder patterns. On some bills the payment code's
     * own data modules happen to contain a false 1:1:3:1:1 run, `FinderPatternFinder`
     * picks it over a real corner, and the sampled grid is garbage — detection
     * "succeeds" and decoding fails. Measured on one April InfoStan bill: finders
     * came back as (460,60), (380,416), (60,60) instead of the three true corners, and
     * the code stayed unreadable at every render scale up to 3500 px and from the
     * pristine 520×520 image embedded in the PDF. It is not resolution, not the
     * renderer and not the second code — it is which payload the issuer encoded,
     * which is why two otherwise identical bills differ.
     *
     * `QRCodeMultiReader` uses `MultiFinderPatternFinder`, which enumerates all
     * finder-pattern candidates and tries the plausible TRIPLES, so a false corner
     * costs it one rejected combination instead of the whole read. Over the 67-page
     * local corpus: payment code found on 31 pages before, 36 after, nothing lost,
     * and total decode time roughly halved.
     */
    override fun decodeThorough(bitmap: Bitmap): List<String> {
        val found = LinkedHashSet<String>()
        // Each stage is skipped once a PAYMENT code is in hand — not merely once
        // something decoded. Finding "something" is what used to stop the search
        // one code too early on a page that carries two.
        found += readAll(bitmap, tryHarder = false)
        if (found.none(::isPayment)) found += readAll(bitmap, tryHarder = true)
        if (found.none(::isPayment)) found += readTiled(bitmap)
        return found.toList()
    }

    /**
     * The one-shot path is looking for the bill's IPS code specifically, so that
     * is what "found it" has to mean here. A page may also carry an unrelated
     * code — an InfoStan bill prints a marketing one, larger and cleaner than the
     * payment code, which ZXing reaches first — and treating that as success made
     * the app report NO QR on a bill that plainly has one.
     */
    private fun isPayment(text: String) = text.startsWith(IpsQr.PREFIX)

    /** Whole image, every code on it. */
    private fun readAll(bitmap: Bitmap, tryHarder: Boolean): List<String> = try {
        QRCodeMultiReader()
            .decodeMultiple(binarize(bitmap), hints(tryHarder))
            .mapNotNull { it.text }
            .distinct()
    } catch (_: Exception) {
        // decodeMultiple throws NotFound like the single reader does
        read(bitmap, tryHarder)
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
        val found = LinkedHashSet<String>()
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
                        readAll(tile, tryHarder = true)
                    } finally {
                        if (tile !== bitmap) tile.recycle()
                    }
                    found.addAll(hit)
                }
                x += stepX
            }
            y += stepY
        }
        return found.toList()
    }

    private fun read(bitmap: Bitmap, tryHarder: Boolean): List<String> = try {
        listOf(MultiFormatReader().decode(binarize(bitmap), hints(tryHarder)).text)
    } catch (e: Exception) {
        emptyList() // NotFoundException etc. — no QR on this frame/page
    }

    private fun binarize(bitmap: Bitmap): BinaryBitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
    }

    private fun hints(tryHarder: Boolean): Map<DecodeHintType, Any> =
        buildMap {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(com.google.zxing.BarcodeFormat.QR_CODE))
            if (tryHarder) put(DecodeHintType.TRY_HARDER, true)
        }
}
