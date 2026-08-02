package com.racunko.app

import com.racunko.app.parser.IpsQrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Change 5b / Change 9.2 — a generated IPS QR payload must decode back to the
 * same fields. This guards the "generate a QR for QR-less bills" feature end to
 * end: build(fields) → decode → identical provider, account, amount, RO, name.
 */
class IpsQrRoundTripTest {

    private data class Sample(
        val provider: String,
        val account: String,
        val name: String,
        val amount: Long,
        val ro: String
    )

    // the 5 original sample bills, using checksum-valid recipient accounts
    private val samples = listOf(
        Sample("sz", "200555000123456764", "SZ DOBRIVOJA STANKOVIĆA 99 Beograd", 1070, "97040255500012"),
        Sample("infostan", "200220618010100048", "JKP INFOSTAN TEHNOLOGIJE BEOGRAD", 11152, "118005123450112605"),
        Sample("eps", "190000000009987010", "EPS AD Beograd", 3963, "9725020055555552605"),
        Sample("yettel", "170003000505000876", "Yettel d.o.o. Beograd", 2699, "97202012345161"),
        Sample("mts", "200555000123456764", "Telekom Srbija A.D. Beograd", 3282, "97742911111115870")
    )

    @Test
    fun buildDecodeIdentity() {
        for (s in samples) {
            val payload = IpsQrPayload.build(
                recipientAccount = s.account,
                recipientName = s.name,
                amount = s.amount,
                paymentReference = s.ro
            )
            assertTrue("payload must start with K:PR", payload.startsWith("K:PR"))
            val d = IpsQrPayload.decodeFields(payload)
            assertEquals(s.provider, d.provider)
            assertEquals(s.account, d.recipientAccount)
            assertEquals(s.amount, d.amount)
            assertEquals(s.ro, d.paymentReference)
            assertEquals(s.name, d.recipientName)
        }
    }

    @Test
    fun generationIsGatedOnVerifiedAccountAndAmount() {
        assertTrue(IpsQrPayload.canGenerate("190000000009987010", 3963))
        // checksum-failing account -> no generation
        assertTrue(!IpsQrPayload.canGenerate("190000000009987011", 3963))
        // missing amount -> no generation
        assertTrue(!IpsQrPayload.canGenerate("190000000009987010", null))
    }
}
