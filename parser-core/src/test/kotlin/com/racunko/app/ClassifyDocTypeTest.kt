package com.racunko.app

import com.racunko.app.parser.registry.DocType
import com.racunko.app.parser.registry.DocTypeGuess
import com.racunko.app.parser.registry.GuessConfidence
import com.racunko.app.parser.registry.IntakeAction
import com.racunko.app.parser.registry.IntakeGuard
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.5.2 Change A — the type/tab mismatch guard, now reduced to what it actually
 * is: a decision table over (what the document looks like) × (what the user
 * aimed at). No document is needed to state it, so none is used.
 *
 * Recognizing the type from a document IS a claim about that document, and it
 * moved to the fixture corpus — `fixtures/confirmation/`, `fixtures/unknown/`,
 * and the `docType` keys on the bill cases. What remains here is the mapping the
 * app applies to a guess, and it is now pinned for **every** combination rather
 * than for the five that happened to have a sample.
 *
 * Two rules the table must never lose:
 *  - UNKNOWN always asks. QR absence is not evidence of a confirmation.
 *  - Share-into (`intended = null`) has no expectation to contradict, so a clear
 *    type routes silently — but an unclear one still asks.
 */
class ClassifyDocTypeTest {

    private fun guess(type: DocType) = DocTypeGuess(
        type,
        if (type == DocType.UNKNOWN) GuessConfidence.LOW else GuessConfidence.HIGH
    )

    private fun decide(type: DocType, intended: DocType?) =
        IntakeGuard.decide(guess(type), intended)

    /** Intent matches the fingerprint → straight through, both tabs. */
    @Test
    fun typeAgreesWithIntent_proceeds() {
        assertEquals(IntakeAction.PROCEED, decide(DocType.BILL, DocType.BILL))
        assertEquals(IntakeAction.PROCEED, decide(DocType.CONFIRMATION, DocType.CONFIRMATION))
    }

    /** A bank confirmation pushed through „Dodaj račun" → suggest the other tab. */
    @Test
    fun confirmationAddedAsBill_warnsWithSuggestion() {
        assertEquals(
            IntakeAction.WARN_SUGGEST_CONFIRMATION,
            decide(DocType.CONFIRMATION, DocType.BILL)
        )
    }

    /** The mirror, which is the one an asymmetric implementation drops. */
    @Test
    fun billAddedAsConfirmation_warnsWithSuggestion() {
        assertEquals(IntakeAction.WARN_SUGGEST_BILL, decide(DocType.BILL, DocType.CONFIRMATION))
    }

    /**
     * UNKNOWN asks — from either tab and from share-into alike. This is the rule
     * that keeps a QR-less paper bill from being silently filed as a confirmation
     * just because it carries no QR.
     */
    @Test
    fun unknownAlwaysAsks_neverRoutesOnAbsence() {
        for (intended in listOf(DocType.BILL, DocType.CONFIRMATION, null)) {
            assertEquals(
                "intended=$intended",
                IntakeAction.ASK_TYPE,
                decide(DocType.UNKNOWN, intended)
            )
        }
    }

    /** Share-into carries no expectation, so a clear type never prompts. */
    @Test
    fun shareIn_routesAClearTypeSilently() {
        assertEquals(IntakeAction.PROCEED, decide(DocType.BILL, null))
        assertEquals(IntakeAction.PROCEED, decide(DocType.CONFIRMATION, null))
    }
}
