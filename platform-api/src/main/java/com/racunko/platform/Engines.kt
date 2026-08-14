package com.racunko.platform

import android.graphics.Bitmap

/**
 * Device capabilities the app needs from a QR/OCR engine (Change 2).
 *
 * These interfaces are the DI boundary: `:parser-core` never sees them (it works
 * on decoded text/QR strings), and no concrete engine may be referenced here.
 * The `gms` flavor implements them with ML Kit + ZXing; the `foss` flavor with
 * ZXing + Tesseract. Both are injected through [PlatformEngines] at the app
 * layer, so swapping engines is a compile-time flavor choice, not a code change.
 */

/** Decodes every barcode/QR payload found in a still bitmap. */
interface QrDecoder {
    /** @return raw payload strings; the caller keeps only those starting `K:PR`. */
    fun decode(bitmap: Bitmap): List<String>

    /**
     * The same decode, but for a ONE-SHOT image where spending more time is
     * cheap and a miss is expensive — a rendered PDF page or a photographed
     * bill, as opposed to a camera frame of which there are thirty a second.
     *
     * Exists because the two engines are not equally strong: on a full A4 page
     * render the IPS code occupies a small corner, and ZXing's default pass
     * misses layouts that ML Kit reads (measured on device: an InfoStan bill
     * read by ML Kit in 245 ms, missed entirely by plain ZXing). A missed
     * decode is not fatal — the app rebuilds the code from the stored fields —
     * but the rebuilt payload is not the issuer's, so it is worth some effort
     * to avoid.
     *
     * Defaults to [decode]; a flavor overrides it only if it has something
     * slower and better to offer.
     */
    fun decodeThorough(bitmap: Bitmap): List<String> = decode(bitmap)
}

/** Renders an NBS IPS payload (built by parser-core's `IpsQrPayload`) to a QR bitmap. */
interface QrEncoder {
    /** @param size target side in px (≥ 660 for a scannable IPS QR); ECC level M, quiet zone. */
    fun encode(payload: String, size: Int): Bitmap
}

/** On-device text recognition for image/screenshot confirmations and image bills. */
interface TextRecognizer {
    suspend fun recognize(bitmap: Bitmap): String
}

/**
 * Live camera QR scanning (Change 3). The app feeds decoded camera frames as
 * bitmaps; the scanner locks onto an IPS payload only after it has seen the
 * SAME `K:PR` payload on several consecutive frames — successful decode is the
 * quality gate, so there is no blur/quality scoring. The stateful debounce
 * implementation ships with 8d; this is only its contract.
 */
interface LiveQrScanner {
    /** @return the locked IPS payload once stable across enough frames, else null. */
    fun onFrame(bitmap: Bitmap): String?

    /** Drops accumulated frame state (e.g. when the camera screen is reopened). */
    fun reset()
}

/**
 * The aggregate a flavor provides (8b) and the app consumes. Kept as an
 * interface so neither the app's UI/domain nor the core depends on any concrete
 * engine — only on this seam.
 */
interface PlatformEngines {
    val qrDecoder: QrDecoder
    val qrEncoder: QrEncoder
    val textRecognizer: TextRecognizer

    /** A fresh, stateful scanner per camera session. */
    fun newLiveQrScanner(): LiveQrScanner
}

/**
 * The single injection point. A flavor's `Application` sets [instance] once at
 * startup; UI/domain read it. Left unset here in 8a (no implementations yet), so
 * nothing calls into an engine and the app still builds.
 */
object Engines {
    @Volatile
    var instance: PlatformEngines? = null

    /** Non-null accessor for call sites that require a wired engine. */
    fun require(): PlatformEngines =
        instance ?: error("PlatformEngines not initialized — the flavor's Application must set Engines.instance")
}
