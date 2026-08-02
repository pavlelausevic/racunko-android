package com.racunko.app

import com.racunko.app.parser.AddressEntry
import com.racunko.app.parser.AddressMatcher
import com.racunko.app.parser.PayeeMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5.1 Change 1 — the address is NEVER guessed. With a one-entry address book
 * and a document whose address matches nothing, the label resolves to empty —
 * not to the lone entry. No fallback to "first/any", no guessing from count.
 * Payee-memory prefill is allowed ONLY on an exact recipient-account match.
 */
class NeverGuessAddressTest {

    private val oneEntryBook = listOf(
        AddressEntry("BDS95", listOf("bulevar dušana simića 95"))
    )

    @Test
    fun oneEntryBook_nonMatchingDocument_staysEmpty() {
        val text = "Adresa: VOJVODE STEPE 10, iznos za uplatu 1.234,00"
        for (provider in listOf("", "infostan", "eps", "sz")) {
            val hit = AddressMatcher.detect(oneEntryBook, null, text, provider)
            assertEquals("provider=$provider", "", hit.label)
            assertFalse(hit.ambiguous)
            assertTrue(hit.all.isEmpty())
        }
    }

    @Test
    fun oneEntryBook_matchingDocument_resolvesNormally() {
        val text = "Адреса мерног места: БУЛЕВАР ДУШАНА СИМИЋА 95"
        val hit = AddressMatcher.detect(oneEntryBook, null, text, "eps")
        assertEquals("BDS95", hit.label)
        assertFalse(hit.ambiguous)
    }

    @Test
    fun blankPattern_neverMatchesAnything() {
        // A blank/whitespace pattern must not turn into a match-everything regex.
        val broken = listOf(AddressEntry("X", listOf(" ", "")))
        val hit = AddressMatcher.detect(broken, null, "bilo koji tekst racuna 123", "")
        assertEquals("", hit.label)
    }

    @Test
    fun payeePrefill_onlyOnExactAccountMatch() {
        // No remembered profile for the account -> nothing is filled.
        val none = PayeeMemory.prefill("200555000123456764", "", "") { null }
        assertEquals("", none.addressLabel)
        assertFalse(none.addressSuggested)
        // Invalid/absent account -> lookup is not even attempted.
        val invalid = PayeeMemory.prefill("123", "", "") { error("must not be called") }
        assertEquals("", invalid.addressLabel)
    }
}
