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
 */
class PayeeMemoryTest {

    private val account = "200555000123456764" // checksum-valid (SZ)
    private val profile = PayeeProfile(account, "sz", "DS99", "SZ DOBRIVOJA STANKOVICA 99")

    // month-1: bill A processed → remembered. month-2: same account, missing fields.
    private val table = mutableMapOf(account to profile)
    private val lookup: (String) -> PayeeProfile? = { table[it] }

    @Test
    fun secondDocumentWithSameAccountGetsPrefilled() {
        val r = PayeeMemory.prefill(account, currentProvider = "", currentAddress = "", lookup)
        assertEquals("sz", r.provider)
        assertEquals("DS99", r.addressLabel)
        assertTrue(r.providerSuggested)
        assertTrue(r.addressSuggested)
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
