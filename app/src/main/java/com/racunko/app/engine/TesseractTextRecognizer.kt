package com.racunko.app.engine

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.racunko.platform.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OCR via Tesseract (tesseract4android) — for BOTH flavors since v1.7, which is
 * why it lives in the shared source set rather than under one of them. The
 * flavor split exists to keep a PROPRIETARY engine out of shared code; Tesseract
 * is Apache-2.0, so that reason does not apply to it.
 *
 * It is here because of the script. Every bill a public utility prints in Serbia
 * is in CYRILLIC, and ML Kit's on-device recognizer is Latin-only with no
 * Cyrillic model available — on a screenshot of such a bill it returned nothing
 * usable, which is how an InfoStan bill ended up with no address and no
 * deadline. Tesseract carries `srp`.
 *
 * Language models (srp, srp_latn, eng) are BUNDLED in the APK assets
 * (`tessdata/`) — zero network at runtime, so the no-INTERNET guarantee holds in
 * both flavors. On first use the models are copied from read-only assets into
 * app-private storage (Tesseract needs a filesystem datapath), then the engine
 * is initialized once and reused.
 */
class TesseractTextRecognizer(context: Context) : TextRecognizer {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile
    private var api: TessBaseAPI? = null

    private fun ensureApi(): TessBaseAPI? {
        api?.let { return it }
        val dataParent = File(appContext.filesDir, "tesseract")
        val tessdata = File(dataParent, "tessdata").apply { mkdirs() }
        try {
            for (lang in LANGS) {
                val out = File(tessdata, "$lang.traineddata")
                if (out.exists() && out.length() > 0L) continue
                appContext.assets.open("tessdata/$lang.traineddata").use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
            val t = TessBaseAPI()
            if (!t.init(dataParent.absolutePath, LANGS.joinToString("+"))) {
                t.recycle()
                return null
            }
            api = t
            return t
        } catch (e: Exception) {
            // Missing assets (e.g. fetchTessdata didn't run) or init failure:
            // degrade to empty text rather than crashing the batch.
            return null
        }
    }

    override suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            val t = ensureApi() ?: return@withContext ""
            try {
                t.setImage(bitmap)
                t.getUTF8Text() ?: ""
            } finally {
                t.clear()
            }
        }
    }

    private companion object {
        val LANGS = listOf("srp", "srp_latn", "eng")
    }
}
