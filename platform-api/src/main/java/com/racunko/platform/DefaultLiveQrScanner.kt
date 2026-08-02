package com.racunko.platform

import android.graphics.Bitmap

/**
 * Engine-agnostic live-scan debounce (Change 3 logic; the camera itself arrives
 * in 8d). Wraps any [QrDecoder]: successful decode of an NBS IPS payload
 * (`K:PR…`) on [requiredStableFrames] consecutive frames is the quality gate —
 * there is deliberately no blur/quality scoring. Once locked, [onFrame] keeps
 * returning the same payload until [reset].
 *
 * Pure logic over the injected decoder, so both flavors reuse it and no engine
 * leaks into parser-core.
 */
class DefaultLiveQrScanner(
    private val decoder: QrDecoder,
    private val requiredStableFrames: Int = 3
) : LiveQrScanner {

    private var lastCandidate: String? = null
    private var streak = 0
    private var locked: String? = null

    override fun onFrame(bitmap: Bitmap): String? {
        locked?.let { return it }

        val ips = decoder.decode(bitmap).firstOrNull { it.startsWith(IPS_PREFIX) }
        if (ips == null) {
            lastCandidate = null
            streak = 0
            return null
        }
        if (ips == lastCandidate) {
            streak++
        } else {
            lastCandidate = ips
            streak = 1
        }
        if (streak >= requiredStableFrames) {
            locked = ips
            return ips
        }
        return null
    }

    override fun reset() {
        lastCandidate = null
        streak = 0
        locked = null
    }

    private companion object {
        // NBS IPS identifier; kept local so platform-api stays independent of parser-core.
        const val IPS_PREFIX = "K:PR"
    }
}
