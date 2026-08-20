package com.racunko.app

import com.racunko.app.parser.ConfirmationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yettel banka — the NBS paper transfer order. Two things make it unlike every
 * other confirmation here: each value is printed BEFORE its label, and the
 * amount uses a DOT decimal separator. It also shares its title with the NLB
 * report, so the two must not be able to claim each other's documents.
 *
 * All numbers and names are invented except the recipient account, which is the
 * institutional EPS one already used across this corpus as a public MOD 97-10
 * vector.
 */
class YettelConfirmationTest {

    /**
     * The ACTUAL PDFBox extraction from a device run (20.08.2026), not a tidied
     * copy: PDFBox welds neighbouring labels into one run — „iznosvaluta",
     * „(zaduzenje)model" — and welds the two-digit model onto the END of the
     * reference. A tidied sample hid all three and the template passed here while
     * failing on the phone, so this text is kept exactly as the app sees it.
     */
    private val yettelText =
        "potvrda o izvrsenom nalogu za prenosplatilac svrha placanja uplata po racunu za el. " +
            "energiju primalac eps ad beograd petar petrovic bulevar primera 1 beograd " +
            "(palilula) 289 rsd 2294.15 iznosvaluta obrazac br. 3 sifra placanja datum valute " +
            "hitno x model poziv na broj (odobrenje) 1802000000001234597 190000000009987010 " +
            "racun primaoca racun platioca poziv na broj (zaduzenje)model 115000000000012345 " +
            "17.08.2026 pecat i potpis banke beograd 17.08.2026 mesto i datum prijema"

    /** The NLB report: same title, different furniture. */
    private val nlbText = """
        Iznos
        970,52 RSD
        200-2206180101000-48
        Model i poziv na broj odobrenja
        11
        800614276087-26050-1
        Provizija
        15,00 RSD
        Potvrda o izvrsenom nalogu za prenos
    """.trimIndent()

    @Test
    fun `the paper form is told apart from the NLB report despite the shared title`() {
        assertEquals("yettel", ConfirmationParser.parse(yettelText, null).templateName)
        assertEquals("nlb", ConfirmationParser.parse(nlbText, null).templateName)
    }

    @Test
    fun `a dot decimal separator is still money`() {
        val fields = ConfirmationParser.parse(yettelText, null)
        // 2294.15 is invisible to AmountParser.extractAll, so the template must
        // both parse it AND fold it back in for layer-2 pairing.
        assertEquals(2294L, fields.templatedAmount)
        assertEquals(2294L, fields.amount)
        assertTrue(fields.amounts.contains(2294L))
    }

    @Test
    fun `the payer account never becomes a pairing key`() {
        val fields = ConfirmationParser.parse(yettelText, null)
        assertEquals(setOf("190000000009987010"), fields.accounts)
        assertFalse(fields.accounts.contains("115000000000012345"))
        assertTrue(fields.referenceCandidates.none { it.contains("115000000000012345") })
    }

    @Test
    fun `the reference is offered both with and without the welded model`() {
        val fields = ConfirmationParser.parse(yettelText, null)
        // PDFBox welds the model on: „…12345" + „97". Both readings are offered,
        // the full run first and the model-stripped one right behind it.
        assertEquals("1802000000001234597", fields.referenceCandidates.first())
        assertTrue(fields.referenceCandidates.contains("18020000000012345"))
    }
}
