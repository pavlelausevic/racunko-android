package com.racunko.app.parser

/** One user address: a short filename label + the patterns as printed on bills. */
data class AddressEntry(val label: String, val patterns: List<String>)

data class AddressMatch(val label: String, val ambiguous: Boolean, val all: List<String>) {
    companion object { val NONE = AddressMatch("", false, emptyList()) }
}

/**
 * Zone-ordered address detection (§5.6): QR P field, provider anchor zone
 * (eps: after "mernog mesta", infostan: after "adresa:"), then full text.
 * Strict boundaries: no alphanumeric before the pattern, spaces match [\s,]+,
 * and the trailing house number must not be followed by another digit.
 */
object AddressMatcher {

    /**
     * Shipped first-run seed (v1.4.2 Change 4 — de-branded): neutral demo entries,
     * NOT anyone's real addresses. The user replaces these in Settings on first
     * run. (The fictional Belgrade-style addresses used to exercise the parser
     * live only in the test source set, `SampleAddresses`, never in the app.)
     */
    val DEFAULTS: List<AddressEntry> = listOf(
        AddressEntry("U1", listOf("ulica 1")),
        AddressEntry("P2", listOf("primer 2"))
    )

    private val RX_SPECIAL = Regex("[.*+?^\${}()|\\[\\]\\\\]")

    fun patternRegex(pattern: String): Regex {
        val p = RX_SPECIAL.replace(Normalizer.norm(pattern)) { "\\" + it.value }
            .replace(" ", "[\\s,]+")
        return Regex("(?<![a-z0-9])$p(?![0-9])")
    }

    fun detect(
        addresses: List<AddressEntry>,
        ips: Map<String, String>?,
        text: String?,
        provider: String
    ): AddressMatch {
        val zones = mutableListOf<String>()
        ips?.get("P")?.let { if (it.isNotEmpty()) zones.add(Normalizer.norm(it)) }
        val t = Normalizer.norm(text)
        if (provider == "eps") {
            val i = t.indexOf("mernog mesta")
            if (i >= 0) zones.add(t.substring(i, minOf(t.length, i + 140)))
        }
        if (provider == "infostan") {
            val i = t.indexOf("adresa:")
            if (i >= 0) zones.add(t.substring(i, minOf(t.length, i + 120)))
        }
        zones.add(t)

        for (zone in zones) {
            val found = mutableListOf<String>()
            for (a in addresses) {
                for (pat in a.patterns) {
                    // v1.5.1 Change 1: a blank pattern compiles to a regex that
                    // matches EVERY document, silently turning a one-entry book
                    // into "always that label". The address is never guessed —
                    // an entry matches only through its real, non-blank pattern.
                    if (Normalizer.norm(pat).isBlank()) continue
                    if (patternRegex(pat).containsMatchIn(zone)) {
                        if (a.label !in found) found.add(a.label)
                        break
                    }
                }
            }
            if (found.isNotEmpty()) return AddressMatch(found[0], found.size > 1, found)
        }
        return AddressMatch.NONE
    }
}
