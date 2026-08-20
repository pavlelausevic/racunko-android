package com.racunko.app

import com.racunko.app.parser.ConfirmationParser
import com.racunko.app.parser.MonthYear
import com.racunko.app.parser.PairResult
import com.racunko.app.parser.PairingEngine
import com.racunko.app.parser.StoredBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NLB „Potvrda o izvršenom nalogu za prenos".
 *
 * The document this was written from has HALF its text layer unreadable: labels
 * drawn in a subsetted Identity-H font with no `/ToUnicode` come out shifted by
 * a constant, while the Helvetica/WinAnsi parts read cleanly. The text below
 * reproduces that mix on purpose — the point of the template is that it holds on
 * the readable anchors alone, so the garbage has to be present for the test to
 * mean anything. All numbers and names are invented; the recipient account is
 * the institutional InfoStan one that is public and already used across this
 * corpus as a valid MOD 97-10 vector.
 */
class NlbConfirmationTest {

    private val nlbText = """
        Platilac (naziv, adresa, mesto)
        3(7$53(75295,û
        Novi Beograd
        BULEVAR PRIMERA 1
        6YUKDSODüDQMD
        OBJEDINJENA NAPLATA
        Primalac (naziv, adresa, mesto)
        JKP INFOSTAN TEHNOLOGIJE BEOGR
        5DþXQSODWLRFD
        205-9000123-57
        âLIUDSODüDQMD
        222
        Iznos
        970,52 RSD
        5DþXQSULPDRFD
        200-2206180101000-48
        Model i poziv na broj odobrenja
        11
        800614276087-26050-1
        Status
        Realizovan
        Provizija
        15,00 RSD
        Datum izvršenja
        Potvrda o izvršenom nalogu za prenos
    """.trimIndent()

    private val payerAccount = "205000000900012357"
    private val recipientAccount = "200220618010100048"

    private fun bill(ro: String, account: String, amount: Long, name: String) = StoredBill(
        roKey = ro,
        altKey = StoredBill.altKeyOf(ro),
        provider = "infostan",
        address = "SG26",
        month = MonthYear(5, 26),
        amount = amount,
        recipientAccount = account,
        name = name,
        paired = false
    )

    @Test
    fun `the readable title is enough to claim the document`() {
        assertEquals("nlb", ConfirmationParser.parse(nlbText, null).templateName)
    }

    @Test
    fun `the fee never becomes the amount`() {
        val fields = ConfirmationParser.parse(nlbText, null)
        // „Provizija 15,00 RSD" is printed beside „Iznos 970,52 RSD"; anchoring on
        // the label is the only thing that keeps the fee out of the payment.
        assertEquals(971L, fields.templatedAmount)
        assertEquals(971L, fields.amount)
        assertTrue("the fee is still SEEN, just not chosen", fields.amounts.contains(15L))
    }

    @Test
    fun `the payer account never becomes a pairing key`() {
        val fields = ConfirmationParser.parse(nlbText, null)
        assertEquals(setOf(recipientAccount), fields.accounts)
        assertFalse(fields.accounts.contains(payerAccount))
        assertTrue(fields.referenceCandidates.none { it.contains(payerAccount) })
    }

    @Test
    fun `the credit reference is read from its readable label`() {
        val fields = ConfirmationParser.parse(nlbText, null)
        assertTrue(
            "expected the odobrenje reference among ${fields.referenceCandidates}",
            fields.referenceCandidates.any { it.contains("800614276087") }
        )
    }

    @Test
    fun `it pairs to the bill it actually paid`() {
        val fields = ConfirmationParser.parse(nlbText, null)
        val paid = bill("97800614276087260501", recipientAccount, 971, "infostan_SG26_maj26_971")
        val other = bill("97800614276099260401", recipientAccount, 900, "infostan_SG26_apr26_900")
        val result = PairingEngine.pair(fields, listOf(other, paid))
        assertTrue("expected a match, got $result", result is PairResult.Matched)
        assertEquals("infostan_SG26_maj26_971", (result as PairResult.Matched).bill.name)
    }

    @Test
    fun `a thousands separator is not part of the money`() {
        // A device sample printed „30.000,00 RSD"; the dots are grouping,
        // not decimals, and „Provizija 15,00 RSD" sits right under it as always.
        val big = nlbText
            .replace("970,52 RSD", "30.000,00 RSD")
            .replace("200-2206180101000-48", "160-5100999-90")
        val fields = ConfirmationParser.parse(big, null)
        assertEquals(30000L, fields.templatedAmount)
        assertEquals(setOf("160000000510099990"), fields.accounts)
    }
}
