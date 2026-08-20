package com.racunko.app

import com.racunko.app.parser.PayeeMemory
import com.racunko.app.parser.PayeeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Change 6 / Change 9.4 — payee memory prefills month-2 documents and is
 * cleanly disabled once the remembered payees are cleared.
 *
 * v1.7.1 INVERTED one of these assertions on purpose: memory fills the PROVIDER
 * and no longer the ADDRESS. Its key is the recipient account, which names the
 * issuer rather than the property — institutional and shared for InfoStan and
 * EPS — so a remembered address is only ever „the last one seen". It filed a
 * bill under a stranger's label on device 20.08.2026. See `PayeeMemory.prefill`.
 */
class PayeeMemoryTest {

    private val account = "200555000123456764" // checksum-valid (SZ)
    private val profile = PayeeProfile(account, "sz", "DS99", "SZ DOBRIVOJA STANKOVICA 99")

    // month-1: bill A processed → remembered. month-2: same account, missing fields.
    private val table = mutableMapOf(account to profile)
    private val lookup: (String) -> PayeeProfile? = { table[it] }

    @Test
    fun secondDocumentWithSameAccountGetsItsProviderPrefilled() {
        val r = PayeeMemory.prefill(account, currentProvider = "", currentAddress = "", lookup)
        assertEquals("sz", r.provider)
        assertTrue(r.providerSuggested)
    }

    @Test
    fun theRememberedAddressIsNeverHandedBack() {
        // The blank stays blank. The account is the issuer's, so the address it
        // remembers belongs to whichever property was processed last — which is a
        // guess, and this app does not guess an address.
        val r = PayeeMemory.prefill(account, currentProvider = "", currentAddress = "", lookup)
        assertEquals("", r.addressLabel)
        assertFalse(r.addressSuggested)
    }

    @Test
    fun documentValuesAreNeverOverridden() {
        val r = PayeeMemory.prefill(account, currentProvider = "eps", currentAddress = "KD7", lookup)
        assertEquals("eps", r.provider)
        assertEquals("KD7", r.addressLabel)
        assertFalse(r.providerSuggested)
        assertFalse(r.addressSuggested)
    }

    @Test
    fun onlyChecksumValidAccountsLookUp() {
        val bad = "200555000123456765" // one digit off -> invalid
        val r = PayeeMemory.prefill(bad, "", "", lookup)
        assertEquals("", r.provider)
        assertFalse(r.providerSuggested)
        assertFalse(PayeeMemory.rememberable(bad))
        assertTrue(PayeeMemory.rememberable(account))

        // An unproven account is not merely a lookup that misses — the lookup is
        // not attempted at all. Prefill is the one place a remembered address can
        // reach a file name without the document naming it, so the key it keys on
        // has to be proven first.
        PayeeMemory.prefill("123", "", "") { error("lookup must not be attempted") }
    }

    @Test
    fun clearingRememberedPayeesDisablesPrefill() {
        table.clear() // „Obriši zapamćene primaoce"
        val r = PayeeMemory.prefill(account, "", "", lookup)
        assertEquals("", r.provider)
        assertEquals("", r.addressLabel)
        assertFalse(r.providerSuggested)
        assertFalse(r.addressSuggested)
    }
}
