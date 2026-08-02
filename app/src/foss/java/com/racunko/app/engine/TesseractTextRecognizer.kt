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
 * foss OCR via Tesseract (tesseract4android). Language models (srp, srp_latn,
 * eng) are BUNDLED in the APK assets (`tessdata/`) — zero network at runtime,
 * so foss keeps the no-INTERNET guarantee. On first use the models are copied
 * from read-only assets into app-private storage (Tesseract needs a filesystem
 * datapath), then the engine is initialized once and reused.
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
