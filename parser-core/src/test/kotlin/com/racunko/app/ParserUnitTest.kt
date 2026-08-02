package com.racunko.app

import com.racunko.app.parser.Accounts
import com.racunko.app.parser.AddressEntry
import com.racunko.app.parser.AddressMatcher
import com.racunko.app.parser.AmountParser
import com.racunko.app.parser.BillName
import com.racunko.app.parser.ConfirmationFields
import com.racunko.app.parser.Months
import com.racunko.app.parser.MonthYear
import com.racunko.app.parser.Normalizer
import com.racunko.app.parser.OcrPolicy
import com.racunko.app.parser.PairResult
import com.racunko.app.parser.PairingEngine
import com.racunko.app.parser.StoredBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserUnitTest {

    // ---------------------------------------------------------- rounding

    @Test
    fun rounding_halfUp() {
        assertEquals(11152L, AmountParser.roundHalfUp(11151, 71))
        assertEquals(3963L, AmountParser.roundHalfUp(3962, 69))
        assertEquals(2699L, AmountParser.roundHalfUp(2699, 0))
        assertEquals(101L, AmountParser.roundHalfUp(100, 50))
        assertEquals(100L, AmountParser.roundHalfUp(100, 49))
    }

    // ----------------------------------------------------- normalization

    @Test
    fun normalization_cyrillicAndDiacritics() {
        assertEquals("koste dragojevica 7", Normalizer.norm("КОСТЕ ДРАГОЈЕВИЋА 7"))
        assertEquals("sdjccz", Normalizer.norm("šđčćž"))
        assertEquals("ljubljana", Normalizer.norm("Љубљана"))
        assertEquals("dzak njiva", Normalizer.norm("џак њива"))
        assertEquals("dragise lovcevica 27", Normalizer.norm("Драгише  Ловчевића 27"))
    }

    // ------------------------------------------------- address boundaries

    private val addresses = listOf(
        AddressEntry("KD7", listOf("koste dragojevića 7")),
        AddressEntry("AJ46b", listOf("arse jankovića 46b"))
    )

    @Test
    fun address_trailingDigitBoundary() {
        val hit = AddressMatcher.detect(addresses, null, "stan u ulici KOSTE DRAGOJEVIĆA 7 /2/15", "")
        assertEquals("KD7", hit.label)
        val miss = AddressMatcher.detect(addresses, null, "stan u ulici KOSTE DRAGOJEVIĆA 71", "")
        assertEquals("", miss.label)
        val cyr = AddressMatcher.detect(addresses, null, "Адреса: КОСТЕ ДРАГОЈЕВИЋА 7 СТ. 15", "")
        assertEquals("KD7", cyr.label)
    }

    @Test
    fun address_trailingLetterAllowed() {
        val hit = AddressMatcher.detect(addresses, null, "ARSE JANKOVIĆA 46B, Beograd", "")
        assertEquals("AJ46b", hit.label)
    }

    // ------------------------------------- pairing by RO with/without model

    private fun fields(refs: List<String>, accounts: Set<String> = emptySet(), amounts: Set<Long> = emptySet(), templated: Long? = null) =
        ConfirmationFields(refs, accounts, amounts, templated ?: amounts.firstOrNull(), "", templated)

    private val bill = StoredBill(
        roKey = "97040255500012",
        altKey = StoredBill.altKeyOf("97040255500012"),
        provider = "sz", address = "DS99",
        month = MonthYear(6, 26), amount = 1070L,
        recipientAccount = "200555000123456764",
        name = "sz_DS99_jun26_1070", paired = false
    )

    @Test
    fun pairing_roWithModelPrefix() {
        val r = PairingEngine.pair(fields(listOf("97040255500012")), listOf(bill))
        assertTrue(r is PairResult.Matched)
    }

    @Test
    fun pairing_roWithoutModelPrefix() {
        assertEquals("040255500012", StoredBill.altKeyOf("97040255500012"))
        val r = PairingEngine.pair(fields(listOf("040255500012")), listOf(bill))
        assertTrue(r is PairResult.Matched)
    }

    @Test
    fun pairing_suffixMatch() {
        // longer candidate that ends with the stored key
        val r = PairingEngine.pair(fields(listOf("0097040255500012")), listOf(bill))
        assertTrue(r is PairResult.Matched)
    }

    @Test
    fun pairing_noMatch() {
        val r = PairingEngine.pair(fields(listOf("11111111")), listOf(bill))
        assertTrue(r is PairResult.None)
    }

    // ------------------------------------------ layer-2 account normalization

    @Test
    fun accounts_dashNormalization() {
        assertTrue("190000000009987010" in Accounts.extractAll("racun 190-99870-10 primaoca"))
        assertTrue("200220618010100048" in Accounts.extractAll("racun 200-2206180101000-48"))
        assertTrue("200220618010100048" in Accounts.extractAll("na racun 200220618010100048 uplata"))
    }

    @Test
    fun accounts_layer2WithAmountCrossCheck() {
        val f = fields(
            refs = emptyList(),
            accounts = Accounts.extractAll("uplata na 200-5550001234567-64 iznos 1.070,00 RSD"),
            amounts = AmountParser.extractAll("uplata na 200-5550001234567-64 iznos 1.070,00 RSD")
        )
        val r = PairingEngine.pair(f, listOf(bill))
        assertTrue(r is PairResult.Matched)
        assertEquals(2, (r as PairResult.Matched).layer)

        // same account, wrong amount -> no pairing
        val f2 = fields(refs = emptyList(), accounts = f.accounts, amounts = setOf(999L))
        assertTrue(PairingEngine.pair(f2, listOf(bill)) is PairResult.None)
    }

    // ------------------------------------------------- collision suffixing

    @Test
    fun filename_collisionSuffix() {
        assertEquals("a.pdf", BillName.unique("a", ".pdf", emptySet()))
        assertEquals("a_2.pdf", BillName.unique("a", ".pdf", setOf("a.pdf")))
        assertEquals("a_3.pdf", BillName.unique("a", ".pdf", setOf("a.pdf", "a_2.pdf")))
    }

    // ------------------------------------------------------- month tokens

    @Test
    fun month_tokens() {
        assertEquals("maj26", Months.token(MonthYear(5, 26)))
        assertEquals(MonthYear(5, 26), Months.fromToken("maj26"))
        assertEquals(MonthYear(8, 26), Months.fromToken("avgust 26"))
        assertNull(Months.fromToken("xyz99"))
    }

    // ---------------------------------------------------- OCR trigger (v1.1)

    @Test
    fun ocrTrigger_shortTextRoutesToOcr() {
        assertTrue(OcrPolicy.needsOcr(null))
        assertTrue(OcrPolicy.needsOcr(""))
        assertTrue(OcrPolicy.needsOcr("   \n  "))
        assertTrue(OcrPolicy.needsOcr("kratak tekst"))
        assertFalse(OcrPolicy.needsOcr("x".repeat(40)))
    }

    // ------------------------------------------------- processed-name regex

    @Test
    fun processedName_detection() {
        assertTrue(BillName.PROCESSED.matches("infostan_KD7_maj26_11152.pdf"))
        assertTrue(BillName.PROCESSED.matches("uplata_sz_DS99_jun26_1070.pdf"))
        assertTrue(BillName.PROCESSED.matches("eps_BDS95_maj26_3963_2.pdf"))
        assertFalse(BillName.PROCESSED.matches("racun-jul-2026.pdf"))
    }
}
