package com.racunko.app.parser

object ProviderDetector {

    val PROVIDERS = listOf("infostan", "eps", "mts", "yettel", "sz")

    private val RX_INFOSTAN = Regex("infostan")
    private val RX_EPS = Regex("\\beps\\b|elektroprivreda")
    private val RX_YETTEL_SBB = Regex("yettel|sbb")
    private val RX_MTS = Regex("telekom srbija|\\bmts\\b")
    private val RX_SZ_N = Regex("^sz\\b")
    private val RX_SZ_TEXT = Regex("stambena zajednica")
    private val RX_YETTEL = Regex("yettel")
    private val RX_ELEKTRANE = Regex("beogradske elektrane")

    /** SZ names on confirmations appear without the words "stambena zajednica". */
    private val RX_SZ_NAME = Regex("(^|\\s)sz\\s+[a-z]{3,}")

    /** Detect on normalized QR N field (preferred), then normalized full text. */
    fun detect(ips: Map<String, String>?, text: String?): String {
        val n = Normalizer.norm(ips?.get("N"))
        val t = Normalizer.norm(text)
        val src = n.ifEmpty { t }
        if (RX_INFOSTAN.containsMatchIn(src)) return "infostan"
        if (RX_EPS.containsMatchIn(src)) return "eps"
        if (RX_YETTEL_SBB.containsMatchIn(n)) return "yettel"
        if (RX_MTS.containsMatchIn(src)) return "mts"
        if (RX_SZ_N.containsMatchIn(n) || RX_SZ_TEXT.containsMatchIn(t)) return "sz"
        if (RX_YETTEL.containsMatchIn(t)) return "yettel"
        if (RX_ELEKTRANE.containsMatchIn(t)) return "infostan"
        if (RX_SZ_NAME.containsMatchIn(t)) return "sz"
        return ""
    }
}
