package com.racunko.app.parser

/**
 * Billing-month detection, ordered rules — first hit wins (§5.5):
 * 1. QR S field: MM/YYYY, or Serbian month name + 4-digit year
 * 2. eps: RO digits, last 4 = YYMM
 * 3. infostan: raw RO structured as {ident}-{YY}{MM}{D}-{K}; month read from the
 *    hyphen-anchored suffix (v1.4.1 Bug 1 — the old unanchored pattern could bite
 *    an earlier "...080-" inside the ident and misread the month)
 * 4. text: billing period dd.MM.yyyy - dd.MM.yyyy -> end date's month
 * 5. text: Serbian month name + 4-digit year
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
            for (i in MONTH_NAME_RX.indices) {
                MONTH_NAME_RX[i].find(sn)?.let { m ->
                    return MonthYear(i + 1, m.groupValues[1].toInt() % 100)
                }
            }
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

        for (i in MONTH_NAME_RX.indices) {
            MONTH_NAME_RX[i].find(t)?.let { m ->
                return MonthYear(i + 1, m.groupValues[1].toInt() % 100)
            }
        }
        return null
    }
}
