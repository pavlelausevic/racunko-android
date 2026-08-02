package com.racunko.app

import com.racunko.app.parser.registry.DocType
import com.racunko.app.parser.registry.GuessConfidence
import com.racunko.app.parser.registry.IntakeAction
import com.racunko.app.parser.registry.IntakeGuard
import com.racunko.app.parser.registry.NormalizedDoc
import com.racunko.app.parser.registry.SourceKind
import com.racunko.app.parser.registry.TemplateRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.5.2 Change A — type/tab mismatch guard. The classifier (not QR absence!)
 * decides when to warn: a clear confirmation added as a bill warns with
 * „Dodaj kao potvrdu"; a QR-less SZ bill passes silently; garbage asks.
 */
class ClassifyDocTypeTest {

    private val registry = TemplateRegistry.default()

    private fun doc(text: String?, ipsQr: Map<String, String>? = null) =
        NormalizedDoc.of(text, ipsQr, SourceKind.PDF_TEXT)

    private val intesaConfirmation = """
        Potvrda transakcije UPLATA/ISPLATA REFERENCA NAZIV PLATIOCA 954OMIN2600000AB PETAR PETROVIĆ
        BROJ RAČUNA PLATIOCA IZNOS TRANSAKCIJE 160000123456789070 1.070,00 RSD
        BROJ RAČUNA PRIMAOCA NAZIV PRIMAOCA 200555000123456764 SZ DOBRIVOJA STANKOVICA 99
        MODEL I POZIV NA BROJ ODOBRENJA - 040255500012
    """.trimIndent()

    private val qrlessSzBill = """
        primalac SZ DOBRIVOJA STANKOVICA 99
        racun primaoca 200-5550001234567-64
        poziv na broj 040255500012
        za uplatu 1.500,00
    """.trimIndent()

    private val ipsQr = mapOf(
        "K" to "PR", "R" to "200555000123456764",
        "N" to "SZ DOBRIVOJA STANKOVIĆA 99", "I" to "RSD1070,00", "RO" to "97040255500012"
    )

    /** 1. A real bank confirmation added via „Dodaj račun" → warn, suggest potvrda. */
    @Test
    fun confirmationAddedAsBill_warnsWithSuggestion() {
        val guess = registry.classifyDocType(doc(intesaConfirmation))
        assertEquals(DocType.CONFIRMATION, guess.type)
        assertEquals(GuessConfidence.HIGH, guess.confidence)
        assertEquals(
            IntakeAction.WARN_SUGGEST_CONFIRMATION,
            IntakeGuard.decide(guess, intended = DocType.BILL)
        )
    }

    /** 2. KEY regression guard: a QR-less SZ paper bill is NOT nagged. */
    @Test
    fun qrlessSzBill_passesWithoutPrompt() {
        val guess = registry.classifyDocType(doc(qrlessSzBill))
        assertEquals(DocType.BILL, guess.type)
        assertEquals(GuessConfidence.HIGH, guess.confidence)
        assertEquals(IntakeAction.PROCEED, IntakeGuard.decide(guess, intended = DocType.BILL))
    }

    /** 3. A normal bill with an IPS QR → BILL, no prompt. */
    @Test
    fun ipsQrBill_passesWithoutPrompt() {
        val guess = registry.classifyDocType(doc(text = null, ipsQr = ipsQr))
        assertEquals(DocType.BILL, guess.type)
        assertEquals(IntakeAction.PROCEED, IntakeGuard.decide(guess, intended = DocType.BILL))
    }

    /** 4. Indeterminate/garbage → UNKNOWN → the type question is shown. */
    @Test
    fun garbage_asksForType() {
        val contract = "Lorem ipsum dolor sit amet, ugovor o zakupu, clan 1, potpis stranaka."
        val guess = registry.classifyDocType(doc(contract))
        assertEquals(DocType.UNKNOWN, guess.type)
        assertEquals(IntakeAction.ASK_TYPE, IntakeGuard.decide(guess, intended = DocType.BILL))
        // share-into also asks — no silent routing on UNKNOWN
        assertEquals(IntakeAction.ASK_TYPE, IntakeGuard.decide(guess, intended = null))
    }

    /** 5. Mirror: a clear bill pushed through „Dodaj potvrdu" → warn it's a bill. */
    @Test
    fun billAddedAsConfirmation_mirrorWarns() {
        val slip = registry.classifyDocType(doc(qrlessSzBill))
        assertEquals(
            IntakeAction.WARN_SUGGEST_BILL,
            IntakeGuard.decide(slip, intended = DocType.CONFIRMATION)
        )
        val qrBill = registry.classifyDocType(doc(text = null, ipsQr = ipsQr))
        assertEquals(
            IntakeAction.WARN_SUGGEST_BILL,
            IntakeGuard.decide(qrBill, intended = DocType.CONFIRMATION)
        )
        // share-into with a clear type routes silently (no expectation to conflict with)
        assertEquals(IntakeAction.PROCEED, IntakeGuard.decide(qrBill, intended = null))
    }
}
