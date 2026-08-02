package com.racunko.app

import com.racunko.app.parser.AddressEntry

/**
 * A FICTIONAL Belgrade-style address book (§10 / #7 / #8). The street names are
 * invented; only their SHAPE mirrors real bills (genitive person-name streets,
 * Cyrillic/Latin variants, letter-suffixed numbers), so the matcher is exercised
 * exactly like in production. Kept in the TEST source set only — never shipped
 * as an app default; the user fills in their own addresses.
 */
object SampleAddresses {
    val MAP: List<AddressEntry> = listOf(
        AddressEntry("KD7", listOf("koste dragojevića 7")),
        AddressEntry("SG26", listOf("svetozara glišića 26")),
        AddressEntry("BDS95", listOf("bulevar dušana simića 95", "dušana simića 95")),
        AddressEntry("AJ46b", listOf("arse jankovića 46b", "arse jankovića 46")),
        AddressEntry("MR1", listOf("miladina račića 1", "brodska 1")),
        AddressEntry("JA2", listOf("janka arsenijevića 2")),
        AddressEntry("DS99", listOf("dobrivoja stankovića 99")),
        AddressEntry("DL27", listOf("dragiše lovčevića 27")),
        AddressEntry("DL31", listOf("dragiše lovčevića 31"))
    )
}
