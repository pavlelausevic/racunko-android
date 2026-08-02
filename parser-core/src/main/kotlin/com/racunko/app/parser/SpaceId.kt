package com.racunko.app.parser

/**
 * v1.5.2 Change B — per-space identifier (`spaceId`, generic name; InfoStan
 * calls it "Šifra korisnika (IDENT)"). Unique per space while the recipient
 * account is shared across all of a user's spaces (layout verified on real
 * bills; ids here are fictional samples: flat 0598102, garage 0614276, flat
 * 0512345 — one Rn for all), which makes it the natural key for sub-labels. Other providers map their own
 * subscriber id here later; when none exists the value is simply null.
 */
object SpaceId {

    /**
     * InfoStan RO layout observed on real bills: `118` + IDENT (8 digits,
     * zero-padded) + 3-digit partija, then `-YYMM0-D`. The IDENT repeats
     * every month; the tail changes.
     */
    private val RX_INFOSTAN_RO = Regex("^118(\\d{8})\\d{3}(?:-|$)")

    /** Label-anchored text fallback: "Šifra korisnika (IDENT): 0614276". */
    private val RX_LABEL = Regex("(?:sifra korisnika|\\bident\\b)[^0-9]{0,20}(\\d{5,10})")

    /**
     * Canonical form: leading zeros stripped, so the QR-derived (zero-padded)
     * and text-derived (printed) values of the same space always agree.
     */
    fun canonical(digits: String?): String? =
        digits?.filter { it.isDigit() }?.trimStart('0')?.takeIf { it.isNotEmpty() }

    fun detect(provider: String, ips: Map<String, String>?, text: String?): String? {
        if (provider == "infostan") {
            val roRaw = (ips?.get("RO") ?: "").trim()
            RX_INFOSTAN_RO.find(roRaw)?.let { return canonical(it.groupValues[1]) }
        }
        val t = Normalizer.norm(text)
        RX_LABEL.find(t)?.let { return canonical(it.groupValues[1]) }
        return null
    }
}
