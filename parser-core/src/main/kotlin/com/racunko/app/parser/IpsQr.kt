package com.racunko.app.parser

/**
 * NBS IPS QR payload: pipe-separated KEY:VALUE pairs, e.g.
 * K:PR|V:01|C:1|R:200220618010100048|N:JKP INFOSTAN...|I:RSD11151,71|SF:122|S:...|RO:...
 */
object IpsQr {

    const val PREFIX = "K:PR"

    fun parse(payload: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (part in payload.split('|')) {
            val i = part.indexOf(':')
            if (i > 0) out[part.substring(0, i).trim().uppercase()] = part.substring(i + 1).trim()
        }
        return out
    }

    /** Digits-only payment reference (RO field), used as the bill record key. */
    fun roDigits(ips: Map<String, String>?): String =
        (ips?.get("RO") ?: "").filter { it.isDigit() }

    /** Recipient account (R field) digits — 18 digits in the IPS format. */
    fun recipientAccountDigits(ips: Map<String, String>?): String =
        (ips?.get("R") ?: "").filter { it.isDigit() }
}
