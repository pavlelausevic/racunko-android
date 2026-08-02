package com.racunko.app

import com.racunko.app.parser.AddressMatcher
import com.racunko.app.parser.AmountParser
import com.racunko.app.parser.BillName
import com.racunko.app.parser.ConfirmationFields
import com.racunko.app.parser.ConfirmationParser
import com.racunko.app.parser.IpsQr
import com.racunko.app.parser.MonthDetector
import com.racunko.app.parser.MonthYear
import com.racunko.app.parser.PairResult
import com.racunko.app.parser.PairingEngine
import com.racunko.app.parser.ProviderDetector
import com.racunko.app.parser.StoredBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §10 acceptance tests + v1.1 test 7 (Erste/OCR) + v1.2 test 8 (AIK image). */
class AcceptanceTest {

    private data class Analysis(
        val provider: String,
        val address: String,
        val ambiguous: Boolean,
        val month: MonthYear?,
        val amount: Long?,
        val name: String
    )

    /** Mirrors the parser stage of the bill pipeline. */
    private fun analyseBill(payload: String?, text: String?): Analysis {
        val ips = payload?.let { IpsQr.parse(it) }
        val provider = ProviderDetector.detect(ips, text)
        val amount = AmountParser.parse(ips, text)
        val month = MonthDetector.detect(provider, ips, text)
        val addr = AddressMatcher.detect(SampleAddresses.MAP, ips, text, provider)
        return Analysis(
            provider, addr.label, addr.ambiguous, month, amount,
            BillName.build(provider, addr.label, month, amount)
        )
    }

    private fun storedBill(payload: String, name: String, addressOverride: String? = null): StoredBill {
        val ips = IpsQr.parse(payload)
        val a = analyseBill(payload, null)
        val ro = IpsQr.roDigits(ips)
        return StoredBill(
            roKey = ro,
            altKey = StoredBill.altKeyOf(ro),
            provider = a.provider,
            address = addressOverride ?: a.address,
            month = a.month,
            amount = a.amount,
            recipientAccount = IpsQr.recipientAccountDigits(ips),
            name = name,
            paired = false
        )
    }

    // ------------------------------------------------------------ bills 1–5

    private val payload1 =
        "K:PR|V:01|C:1|R:200555000123456764|N:SZ DOBRIVOJA STANKOVIĆA 99 Beograd|I:RSD1070,00|SF:189|S:Jun 2026|RO:97040255500012"

    @Test
    fun test1_szBill() {
        val a = analyseBill(payload1, "Stambena zajednica DOBRIVOJA STANKOVIĆA 99 /30 Beograd")
        assertEquals("sz_DS99_jun26_1070", a.name)
    }

    @Test
    fun test2_infostanBill_cyrillic() {
        val a = analyseBill(
            "K:PR|V:01|C:1|R:200220618010100048|N:JKP INFOSTAN TEHNOLOGIJE BEOGRAD|I:RSD11151,71|SF:122|S:OBJEDINJENA NAPLATA|RO:11800512345011-26050-1",
            "ЈКП ИНФОСТАН ТЕХНОЛОГИЈЕ Адреса: КОСТЕ ДРАГОЈЕВИЋА 7 СТ. 15"
        )
        assertEquals("infostan_KD7_maj26_11152", a.name)
    }

    @Test
    fun test3_epsBill_meteringPointBeatsMailing() {
        val a = analyseBill(
            "K:PR|V:01|C:1|R:845000000040848487|N:EPS AD Beograd|I:RSD3962,69|SF:289|S:Uplata po računu za el. energiju|RO:9725020055555552605",
            "Адреса мерног места: БУЛЕВАР ДУШАНА СИМИЋА 95"
        )
        assertEquals("eps_BDS95_maj26_3963", a.name)
    }

    @Test
    fun test4_yettelBill_periodEndDate() {
        val a = analyseBill(
            "K:PR|V:01|C:1|R:160000000100510845|N:Yettel d.o.o. Beograd|I:RSD2699,00|SF:221|P:PETROVIĆ PETAR\r\nKOSTE DRAGOJEVIĆA 7 /2/15\r\n11000 BEOGRAD|S:Usluge|RO:97202012345161",
            "Račun za period 01.05.2026 - 31.05.2026"
        )
        assertEquals("yettel_KD7_maj26_2699", a.name)
    }

    @Test
    fun test5_mtsBill_monthFromSField() {
        val a = analyseBill(
            "K:PR|V:01|C:1|R:160000000100223963|N:Telekom Srbija A.D. Beograd|I:RSD3282,21|SF:221|P:PETAR PETROVIĆ\r\nKOSTE DRAGOJEVIĆA 7\r\n11000 BEOGRAD 35|S:MTS Račun 05/2026 12345678/1|RO:97742911111115870",
            null
        )
        assertEquals("mts_KD7_maj26_3282", a.name)
    }

    // ------------------------------------------- test 6: Intesa confirmation

    private val intesaText = """
        Potvrda transakcije UPLATA/ISPLATA REFERENCA NAZIV PLATIOCA 954OMIN2600000AB PETAR PETROVIĆ
        BROJ RAČUNA PLATIOCA IZNOS TRANSAKCIJE 160000123456789070 1.070,00 RSD
        DATUM IZVRŠENJA OPIS PLAĆANJA 02.07.2026 Bezgotovinski prenos u RSD 200555000123456764 SZ DOBRIVOJA STANKOVICA 99
        DATUM POTVRDE ŠIFRA PLAĆANJA 02.07.2026 289
        BROJ RAČUNA PRIMAOCA NAZIV PRIMAOCA 200555000123456764 SZ DOBRIVOJA STANKOVICA 99
        NAZIV PLATIOCA MODEL I POZIV NA BROJ ZADUŽENJA PETAR PETROVIĆ 97 -
        MODEL I POZIV NA BROJ ODOBRENJA - 040255500012
    """.trimIndent()

    @Test
    fun test6_intesaConfirmation_layer1Pairing() {
        val bill = storedBill(payload1, "sz_DS99_jun26_1070")
        val fields = ConfirmationParser.parse(intesaText, null)
        assertEquals("intesa", fields.templateName)

        val result = PairingEngine.pair(fields, listOf(bill))
        assertTrue(result is PairResult.Matched)
        result as PairResult.Matched
        assertEquals(1, result.layer)
        assertEquals("uplata_sz_DS99_jun26_1070.pdf", "uplata_${result.bill.name}.pdf")
    }

    @Test
    fun test6_intesaConfirmation_standaloneFields() {
        val fields = ConfirmationParser.parse(intesaText, null)
        // highest-priority reference is the ODOBRENJA value
        assertEquals("040255500012", fields.referenceCandidates.first())
        assertEquals(1070L, fields.amount)
        assertEquals(1070L, fields.templatedAmount)
        assertTrue("200555000123456764" in fields.accounts)
        assertEquals("sz", fields.provider)
        val addr = AddressMatcher.detect(SampleAddresses.MAP, null, intesaText, fields.provider)
        assertEquals("DS99", addr.label)
        assertNull(MonthDetector.detect(fields.provider, null, intesaText))
    }

    @Test
    fun test6_intesaConfirmation_layer2AloneAndPayerNeverKey() {
        val bill = storedBill(payload1, "sz_DS99_jun26_1070")
        val fields = ConfirmationParser.parse(intesaText, null)
        // payer account is never a pairing key
        assertFalse("160000123456789070" in fields.accounts)
        assertTrue(fields.referenceCandidates.none { it.contains("160000123456789070") })
        // layer 2 alone (references removed) still pairs via account + amount
        val result = PairingEngine.pair(fields.copy(referenceCandidates = emptyList()), listOf(bill))
        assertTrue(result is PairResult.Matched)
        assertEquals(2, (result as PairResult.Matched).layer)
    }

    // ---------------------------------- test 7 (v1.1): Erste OCR confirmation

    private val erstePayload =
        "K:PR|V:01|C:1|R:190000000009987010|N:EPS AD Beograd|I:RSD7028,82|SF:289|S:Uplata po računu za el. energiju|RO:9719020055555552605"

    private val ersteOcrText = """
        ERSTES Bank J
        EPS AD BEOGRAD
        Uplata po racunu -7.028,82 RSD
        Ime platioca Marko Marić
        Adresa platioca Primerska 18/4 11000 Beograd - Vracar
        Raéun platioca 340000987654321097
        Ime primaoca EPS AD BEOGRAD
        Raéun primaoca 190000000009987010
        Model - poziv na broj 00 19-02005555555-2605
        Sifra plaéanja 289
        Referenca knjizenja Uplata po racunu
        Datum knjizenja 10.06.2026
        Datum valute 09.06.2026
        Referenca knjizenja FT260000AB1K
    """.trimIndent()

    @Test
    fun test7_ersteBillName_manualAddress() {
        val a = analyseBill(erstePayload, null)
        assertEquals("eps", a.provider)
        assertEquals(MonthYear(5, 26), a.month)
        assertEquals(7029L, a.amount)
        // address not in the mapping -> user sets TEST manually
        assertEquals("eps_TEST_maj26_7029", BillName.build(a.provider, "TEST", a.month, a.amount))
    }

    @Test
    fun test7_ersteConfirmation() {
        val bill = storedBill(erstePayload, "eps_TEST_maj26_7029", addressOverride = "TEST")
        val fields = ConfirmationParser.parse(ersteOcrText, null)
        assertEquals("erste", fields.templateName)

        // (e) priority candidates: with and without the glued 2-digit model
        assertTrue("0019020055555552605" in fields.referenceCandidates)
        assertTrue("19020055555552605" in fields.referenceCandidates)
        // (d) amount positive despite the minus sign
        assertEquals(7029L, fields.templatedAmount)
        // (c) payer account never a pairing key
        assertFalse("340000987654321097" in fields.accounts)
        assertTrue(fields.referenceCandidates.none { it.contains("340000987654321097") })

        // (a) layer 1 pairs — model ignored via suffix/alt-key matching
        val r1 = PairingEngine.pair(fields, listOf(bill))
        assertTrue(r1 is PairResult.Matched)
        r1 as PairResult.Matched
        assertEquals(1, r1.layer)
        assertEquals("uplata_eps_TEST_maj26_7029.pdf", "uplata_${r1.bill.name}.pdf")

        // (b) layer 2 alone pairs via recipient account + amount
        val r2 = PairingEngine.pair(fields.copy(referenceCandidates = emptyList()), listOf(bill))
        assertTrue(r2 is PairResult.Matched)
        assertEquals(2, (r2 as PairResult.Matched).layer)
    }

    // ------------------------------------ test 8 (v1.2): AIK image screenshot

    private val aikBillPayload =
        "K:PR|V:01|C:1|R:200220618010100048|N:JKP INFOSTAN TEHNOLOGIJE BEOGRAD|I:RSD970,52|SF:122|S:OBJEDINJENA NAPLATA|RO:11800614276087-26050-1"

    private val aikBillText = """
        ЈКП ИНФОСТАН ТЕХНОЛОГИЈЕ БЕОГРАД
        ПЕТРОВИЋ ПЕТАР
        ВРАЧАР
        КОСТЕ ДРАГОЈЕВИЋА 7 СТ. 15
        Адреса: СВЕТОЗАРА ГЛИШИЋА 26 ГА. 356
    """.trimIndent()

    private val aikOcrText = """
        09:02 FE 111 93%
        <
        Detalji transakcije
        Datum izvrSenja
        03.07.2026.
        Svrha pla¢anja
        OBJEDINJENA NAPLATA (ZaduZenje (EPP IPS),prenos
        na Rn: 200220618010100048-JKP INFOSTAN
        TEHNOLOGIJE BEOGRAD)
        Uplata/Isplata
        -970,52 RSD
        Datum obrade
        03.07.2026.
        Referenca
        5670260000000017
        Stanje nakon promene
        900,01 RSD
        << O III
    """.trimIndent()

    @Test
    fun test8_aikBill_anchorZoneWinsWithoutAmbiguity() {
        val a = analyseBill(aikBillPayload, aikBillText)
        assertEquals("infostan_SG26_maj26_971", a.name)
        // adresa: anchor resolves SG26 even though KOSTE DRAGOJEVIĆA 7 is in the full text
        assertEquals("SG26", a.address)
        assertFalse(a.ambiguous)
        assertEquals(MonthYear(5, 26), a.month)
        assertEquals(971L, a.amount)
    }

    @Test
    fun test8_aikImageConfirmation() {
        val bill971 = storedBill(aikBillPayload, "infostan_SG26_maj26_971")
        val fields = ConfirmationParser.parse(aikOcrText, null)

        // (a) AIK template detected
        assertEquals("aik", fields.templateName)
        // (b) layer 1 skipped; the bank-internal Referenca is never a candidate
        assertTrue(fields.referenceCandidates.isEmpty())
        assertTrue(fields.referenceCandidates.none { it.contains("5670260000000017") })
        // (c) templated amount is 971; the 900,01 balance is not considered
        assertEquals(971L, fields.templatedAmount)
        // (d) recipient account extracted from the Rn: pattern
        assertTrue("200220618010100048" in fields.accounts)

        // (e) layer 2 pairs; extension preserved for images
        val r = PairingEngine.pair(fields, listOf(bill971))
        assertTrue(r is PairResult.Matched)
        r as PairResult.Matched
        assertEquals(2, r.layer)
        assertEquals("uplata_infostan_SG26_maj26_971.jpg", "uplata_${r.bill.name}.jpg")

        // (f) an additional unpaired InfoStan bill of 900 RSD on the same account
        // must NOT confuse the pairing (Change 3: templated amount only)
        val bill900 = bill971.copy(
            roKey = "11800614276099260401",
            altKey = "800614276099260401",
            amount = 900L,
            month = MonthYear(4, 26),
            name = "infostan_SG26_apr26_900"
        )
        val r2 = PairingEngine.pair(fields, listOf(bill900, bill971))
        assertTrue(r2 is PairResult.Matched)
        assertEquals("infostan_SG26_maj26_971", (r2 as PairResult.Matched).bill.name)
    }
}
