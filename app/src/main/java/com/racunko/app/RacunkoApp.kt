package com.racunko.app

import android.app.Application
import com.racunko.app.engine.EngineFactory
import com.racunko.platform.Engines
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class RacunkoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // Wire the flavor's engines (gms: ML Kit + ZXing, foss: ZXing + Tesseract).
        // EngineFactory is provided by the active flavor's source set (Change 2).
        Engines.instance = EngineFactory.create(applicationContext)
    }
}
