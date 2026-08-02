package com.racunko.app.parser

object AmountParser {

    /** Round half-up: 2-digit decimals >= 50 add one dinar. */
    fun roundHalfUp(whole: Long, decimals: Int): Long = whole + if (decimals >= 50) 1 else 0

    private val RX_I = Regex("([A-Z]{3})?\\s*([\\d.]+)(?:,(\\d{1,2}))?")
    private val RX_LABELED = Regex("(?:za uplatu|ukupno za placanje|iznos)[^\\d]{0,30}([\\d.]+),(\\d{2})")
    private val RX_RSD = Regex("([\\d.]+),(\\d{2})\\s*(?:rsd|din)\\b")

    /** From the IPS I field (RSD11151,71), fallback to labeled amounts in normalized text. */
    fun parse(ips: Map<String, String>?, text: String?): Long? {
        val i = ips?.get("I")
        if (!i.isNullOrEmpty()) {
            val m = RX_I.find(i)
            if (m != null) {
                val whole = m.groupValues[2].replace(".", "").toLongOrNull()
                if (whole != null) {
                    val decStr = m.groupValues[3]
                    val dec = if (decStr.isEmpty()) 0 else decStr.padEnd(2, '0').toInt()
                    return roundHalfUp(whole, dec)
                }
            }
        }
        val t = Normalizer.norm(text)
        RX_LABELED.find(t)?.let { m ->
            val w = m.groupValues[1].replace(".", "").toLongOrNull()
            if (w != null) return roundHalfUp(w, m.groupValues[2].toInt())
        }
        RX_RSD.find(t)?.let { m ->
            val w = m.groupValues[1].replace(".", "").toLongOrNull()
            if (w != null) return roundHalfUp(w, m.groupValues[2].toInt())
        }
        return null
    }

    /** All rounded amounts found in raw text — used for layer-2 confirmation matching. */
    fun extractAll(text: String?): Set<Long> {
        val out = LinkedHashSet<Long>()
        if (text == null) return out
        for (m in Regex("([\\d.]{1,12}),(\\d{2})").findAll(text)) {
            val w = m.groupValues[1].replace(".", "").toLongOrNull() ?: continue
            out.add(roundHalfUp(w, m.groupValues[2].toInt()))
        }
        return out
    }
}
