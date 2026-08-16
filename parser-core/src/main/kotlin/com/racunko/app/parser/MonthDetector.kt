package com.racunko.app.parser

/**
 * Billing-month detection, ordered rules — first hit wins (§5.5):
 * 1. QR S field: MM/YYYY, or Serbian month name + 4-digit year
 * 2. eps: RO digits, last 4 = YYMM
 * 3. infostan: raw RO structured as {ident}-{YY}{MM}{D}-{K}; month read from the
 *    hyphen-anchored suffix (v1.4.1 Bug 1 — the old unanchored pattern could bite
 *    an earlier "...080-" inside the ident and misread the month)
 * 4. text: billing period dd.MM.yyyy - dd.MM.yyyy -> end date's month
 * 5. text: Serbian month name + 4-digit year — the one that comes first IN THE
 *    TEXT, never first in the calendar (v1.7 Bug: a back charge for an earlier
 *    month, printed among the line items, was renaming the whole bill)
 */
object MonthDetector {

    private val RX_MM_YYYY = Regex("(\\d{1,2})/(\\d{4})")
    private val RX_PERIOD = Regex("(\\d{2})\\.(\\d{2})\\.(\\d{4})\\.?\\s*[-–]\\s*(\\d{2})\\.(\\d{2})\\.(\\d{4})")
    // Anchored to the hyphen-delimited suffix: -{YY}{MM}{D}-{K}  (group1=YY, group2=MM).
    private val RX_INFOSTAN_RO = Regex("-(\\d{2})(\\d{2})\\d-\\d")
    private val MONTH_NAME_RX: List<Regex> =
        Months.NAMES.map { Regex("\\b$it[a-z]*\\.?,?\\s+(\\d{4})") }

    fun detect(provider: String, ips: Map<String, String>?, text: String?): MonthYear? {
        val t = Normalizer.norm(text)
        val roDigits = IpsQr.roDigits(ips)

        val s = ips?.get("S")
        if (!s.isNullOrEmpty()) {
            val sn = Normalizer.norm(s)
            RX_MM_YYYY.find(sn)?.let { m ->
                val mm = m.groupValues[1].toInt()
                if (mm in 1..12) return MonthYear(mm, m.groupValues[2].toInt() % 100)
            }
            earliestMonthName(sn)?.let { return it }
        }

        if (provider == "eps" && roDigits.length >= 4) {
            val yy = roDigits.substring(roDigits.length - 4, roDigits.length - 2).toInt()
            val mm = roDigits.substring(roDigits.length - 2).toInt()
            if (mm in 1..12 && yy in 20..45) return MonthYear(mm, yy)
        }

        if (provider == "infostan") {
            RX_INFOSTAN_RO.find(ips?.get("RO") ?: "")?.let { m ->
                val mm = m.groupValues[2].toInt()
                if (mm in 1..12) return MonthYear(mm, m.groupValues[1].toInt())
            }
        }

        RX_PERIOD.find(t)?.let { m ->
            val mm = m.groupValues[5].toInt()
            if (mm in 1..12) return MonthYear(mm, m.groupValues[6].toInt() % 100)
        }

        return earliestMonthName(t)
    }

    /**
     * The month name that comes first IN THE DOCUMENT, not first in the calendar.
     *
     * This used to loop over the month list and return on the first regex that
     * matched anywhere, which made January beat December no matter where either
     * stood on the page. Found on device 15.08.2026: an InfoStan bill for July
     * carries the line „заједничка електрична енергија – мај 2026." — a back
     * charge for May — and the bill was named `..._maj26_...`. Every issuer
     * prints the billing month in the HEADER and older months further down (back
     * charges, consumption charts, comparisons), so earliest-in-text is both the
     * right answer here and the safer rule in general.
     *
     * Only reached when the QR is unreadable — with it, rules 1–3 answer first,
     * which is why `gms` named the same bill correctly and `foss` did not.
     */
    private fun earliestMonthName(t: String): MonthYear? =
        MONTH_NAME_RX.withIndex()
            .mapNotNull { (i, rx) ->
                rx.find(t)?.let { m -> m.range.first to MonthYear(i + 1, m.groupValues[1].toInt() % 100) }
            }
            .minByOrNull { it.first }
            ?.second
}
