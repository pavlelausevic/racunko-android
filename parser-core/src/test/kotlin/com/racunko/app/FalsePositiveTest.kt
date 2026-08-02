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
 * Change 9.5 — the safety net. Distractor numbers (bank-internal references,
 * balances, card numbers, dates, phone/PIB) must bind to NOTHING; a wrong
 * pairing must be impossible.
 */
class FalsePositiveTest {

    private fun bill(
        ro: String, account: String, amount: Long, name: String, paired: Boolean = false
    ) = StoredBill(
        roKey = ro,
        altKey = StoredBill.altKeyOf(ro),
        provider = "infostan",
        address = "SG26",
        month = MonthYear(5, 26),
        amount = amount,
        recipientAccount = account,
        name = name,
        paired = paired
    )

    // AIK screenshot: only distractor numbers are the internal Referenca and the balance.
    private val aikText = """
        Detalji transakcije
        Svrha placanja OBJEDINJENA NAPLATA prenos na Rn: 200220618010100048 JKP INFOSTAN
        Uplata/Isplata -970,52 RSD
        Referenca 5670260000000017
        Stanje nakon promene 900,01 RSD
    """.trimIndent()

    @Test
    fun aikInternalReferenceAndBalanceNeverPair() {
        val fields = ConfirmationParser.parse(aikText, null)
        // AIK carries no payment reference at all
        assertTrue(fields.referenceCandidates.isEmpty())
        // the bank-internal Referenca is not a candidate
        assertTrue(fields.referenceCandidates.none { it.contains("5670260000000017") })
        // the balance 900,01 is not THE amount (templated is 971)
        assertEquals(971L, fields.templatedAmount)

        // a bill on a DIFFERENT account must not be dragged in
        val other = bill("97111111111111", "190000000009987010", 971, "eps_x_maj26_971")
        assertTrue(PairingEngine.pair(fields, listOf(other)) is PairResult.None)
    }

    @Test
    fun templatedAmountUniquelySelectsAmongSameAccountBills() {
        val fields = ConfirmationParser.parse(aikText, null)
        val b900 = bill("97800614276099260401", "200220618010100048", 900, "infostan_SG26_apr26_900")
        val b971 = bill("97800614276087260501", "200220618010100048", 971, "infostan_SG26_maj26_971")
        val result = PairingEngine.pair(fields, listOf(b900, b971))
        assertTrue(result is PairResult.Matched)
        assertEquals("infostan_SG26_maj26_971", (result as PairResult.Matched).bill.name)
    }

    @Test
    fun cardNumbersDatesPhonesPibNeverBecomePairingKeys() {
        val distractors = """
            Potvrda
            Kartica 4111111*****1234
            Datum 06.05.2026 15:42
            Telefon 0611234567
            PIB 100002222 Maticni broj 17853034
        """.trimIndent()
        val fields = ConfirmationParser.parse(distractors, null)
        val seeded = bill("97040255500012", "200555000123456764", 1070, "sz_DS99_jun26_1070")
        assertTrue(PairingEngine.pair(fields, listOf(seeded)) is PairResult.None)
        // masked card fragments never form an 8+ digit candidate
        assertFalse(fields.referenceCandidates.any { it.contains("41111111234") })
    }
}
