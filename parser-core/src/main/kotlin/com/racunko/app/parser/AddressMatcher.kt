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

    /** A following „rec:" label ends the printed address. */
    private val RX_NEXT_LABEL = Regex("[a-z]+\\.?[a-z]*:")

    /**
     * So does the first run of four or more digits. A house number here is at most
     * three, so four is already the post code or a meter id — EPS prints the
     * street, the post code and the town in one run, and only the street belongs
     * in the book.
     */
    private val RX_LONG_NUMBER = Regex("\\d{4,}")

    /** A hint is a nudge, not a paragraph. */
    private const val MAX_HINT = 48

    private val RX_SPECIAL = Regex("[.*+?^\${}()|\\[\\]\\\\]")

    fun patternRegex(pattern: String): Regex {
        val p = RX_SPECIAL.replace(Normalizer.norm(pattern)) { "\\" + it.value }
            .replace(" ", "[\\s,]+")
        return Regex("(?<![a-z0-9])$p(?![0-9])")
    }


    /** The provider's property-address zone, or null when the bill prints no anchor. */
    private fun anchorZone(t: String, provider: String): String? {
        if (provider == "eps") {
            val i = t.indexOf("mernog mesta")
            if (i >= 0) return t.substring(i, minOf(t.length, i + 140))
        }
        if (provider == "infostan") {
            val i = t.indexOf("adresa:")
            if (i >= 0) return t.substring(i, minOf(t.length, i + 120))
        }
        return null
    }

    /**
     * The address the bill PRINTS, for a document where [detect] matched nothing.
     *
     * The app never guesses an address (D4), and it should not start — but staying
     * silent about it turned out to be its own bug. On 19.08.2026 an EPS bill whose
     * street was simply missing from the address book looked like a broken parser:
     * the card said „adresa?" and gave no reason, so nothing pointed at the one
     * place that could fix it. This returns what the bill states so the app can SAY
     * that, and it stays a suggestion — it is never applied on its own.
     *
     * Read only from the provider's own labelled zone, never from free text, for
     * the same reason `detect` prefers that zone: it is the PROPERTY, while the
     * page also carries the payer's postal address.
     *
     * Comes back normalized (lowercase, Cyrillic transliterated) — which is also
     * the form the address book stores, so it can be pasted straight in.
     */
    fun suggestion(ips: Map<String, String>?, text: String?, provider: String): String {
        val t = Normalizer.norm(text)
        // The ADDRESS label, not merely the matcher's zone: EPS anchors that zone
        // on „mernog mesta", whose first occurrence is „sifra mernog mesta:" — a
        // code, not a street. A hint that showed the code would be worse than none.
        val label = when (provider) {
            "eps" -> "adresa mernog mesta:"
            "infostan" -> "adresa:"
            else -> return ""
        }
        val at = t.indexOf(label)
        if (at < 0) return ""
        val body = t.substring(at + label.length, minOf(t.length, at + label.length + 120)).trim()
        // stop at the next „label:" — the zone deliberately over-reaches so the
        // matcher has room, but a hint must not read as one long run-on line
        val cut = minOf(
            RX_NEXT_LABEL.find(body)?.range?.first ?: body.length,
            RX_LONG_NUMBER.find(body)?.range?.first ?: body.length,
            MAX_HINT
        )
        return body.take(cut).trim().trimEnd(',', '-', '.').trim()
    }

    fun detect(
        addresses: List<AddressEntry>,
        ips: Map<String, String>?,
        text: String?,
        provider: String
    ): AddressMatch {
        val zones = mutableListOf<String>()
        val t = Normalizer.norm(text)
        // v1.7: the PROPERTY anchor outranks the QR's P field. P is the PAYER's
        // postal address, which is a different thing from the place the bill is
        // for — an InfoStan bill prints the flat/garage under „adresa:" while P
        // carries where the post goes, and the two are routinely different
        // addresses that the user keeps as two separate labels. Whenever a bill
        // states the property itself, that is the answer.
        // The rest of the app already treats P as not-the-label: `buildBillCard`
        // withholds the QR entirely on the scan path for this exact reason. This
        // only finishes the thought. Providers with no anchor (mts, yettel, sz)
        // are unaffected — for them P IS the subscriber's address, and it stays
        // the first zone tried.
        anchorZone(t, provider)?.let { zones.add(it) }
        ips?.get("P")?.let { if (it.isNotEmpty()) zones.add(Normalizer.norm(it)) }
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
