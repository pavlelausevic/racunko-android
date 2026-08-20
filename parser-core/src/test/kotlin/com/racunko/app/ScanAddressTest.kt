package com.racunko.app

import com.racunko.app.parser.AddressMatcher
import com.racunko.app.parser.AmountParser
import com.racunko.app.parser.IpsQr
import com.racunko.app.parser.PayeeMemory
import com.racunko.app.parser.PayeeProfile
import com.racunko.app.parser.ProviderDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.3 Change 6 — a scanned/QR-imported bill must NEVER guess the address from
 * the QR. The IPS `P` field is the PAYER's name/address, not the user's label,
 * so the scan path passes no QR to the address matcher; the label comes from
 * payee memory (if the recipient account is known) or stays a manual "adresa?".
 * Provider/amount/account/reference still come from the QR.
 */
class ScanAddressTest {

    // MTS-style payload whose P field literally contains a known address pattern.
    private val payload =
        "K:PR|V:01|C:1|R:200555000123456764|N:Telekom Srbija A.D. Beograd|I:RSD3282,21|SF:221" +
            "|P:PETAR PETROVIĆ\r\nKOSTE DRAGOJEVIĆA 7\r\n11000 BEOGRAD 35|S:MTS Račun 05/2026|RO:97742911111115870"
    private val ips = IpsQr.parse(payload)

    @Test
    fun qrYieldsProviderAmountAccountReference() {
        assertEquals("mts", ProviderDetector.detect(ips, null))
        assertEquals(3282L, AmountParser.parse(ips, null))
        assertEquals("200555000123456764", IpsQr.recipientAccountDigits(ips))
        assertEquals("97742911111115870", IpsQr.roDigits(ips))
    }

    @Test
    fun addressIsNotTakenFromTheQr() {
        // If the QR were passed, P's "KOSTE DRAGOJEVIĆA 7" would match KD7 …
        assertEquals("KD7", AddressMatcher.detect(SampleAddresses.MAP, ips, null, "mts").label)
        // … but the scan path passes no QR (and there is no OCR text), so: empty.
        assertEquals("", AddressMatcher.detect(SampleAddresses.MAP, null, null, "mts").label)
    }

    @Test
    fun knownPayeePrefillsTheProviderButNeverTheLabel() {
        val account = IpsQr.recipientAccountDigits(ips) // checksum-valid (SZ vector)
        val profile = PayeeProfile(account, "mts", "KD7", "Telekom Srbija")

        // v1.7.1: the remembered PROVIDER still arrives — it is a property of the
        // account and cannot be wrong. The remembered ADDRESS no longer does: this
        // key is the recipient's, i.e. the operator's, so „KD7" here means only
        // „the last property processed for this issuer". Pinning it was pinning a
        // guess; on device 20.08.2026 the same path filed a bill under a label
        // belonging to a different address entirely.
        val known = PayeeMemory.prefill(account, "", "") { profile }
        assertEquals("mts", known.provider)
        assertTrue(known.providerSuggested)
        assertEquals("", known.addressLabel)
        assertFalse(known.addressSuggested)

        val unknown = PayeeMemory.prefill(account, "", "") { null }
        assertEquals("", unknown.addressLabel)
        assertFalse(unknown.addressSuggested)
    }
}
