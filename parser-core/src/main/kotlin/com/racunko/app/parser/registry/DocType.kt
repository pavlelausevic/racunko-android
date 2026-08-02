package com.racunko.app.parser.registry

/**
 * v1.5.2 Change A2 — document-type classification shared by manual add and
 * share-into. The core only classifies; the app decides the UI from the guess.
 */
enum class DocType { BILL, CONFIRMATION, UNKNOWN }

enum class GuessConfidence { HIGH, LOW }

data class DocTypeGuess(
    val type: DocType,
    val confidence: GuessConfidence,
    /** For UNKNOWN: the slightly-more-likely type, if the text leans at all. */
    val lean: DocType? = null
)

/**
 * v1.5.2 Change A1 — what the intake flow should do for a picked/shared file.
 * One shared decision so „Dodaj račun", „Dodaj potvrdu" and share-into behave
 * identically.
 */
enum class IntakeAction {
    /** Type agrees with the intent (or share-in routes by the guess) — no prompt. */
    PROCEED,
    /** A clear confirmation was added as a bill — suggest „Dodaj kao potvrdu". */
    WARN_SUGGEST_CONFIRMATION,
    /** Mirror: a clear bill was added as a confirmation — suggest „Dodaj kao račun". */
    WARN_SUGGEST_BILL,
    /** Neither fingerprint matched — ask „račun ili potvrda?". */
    ASK_TYPE
}

object IntakeGuard {

    /**
     * [intended] is the tab/flow the user aimed at, or null for share-into
     * (no expectation — a clear type routes silently). QR absence is NEVER a
     * signal here: UNKNOWN asks a type question, it does not imply confirmation.
     */
    fun decide(guess: DocTypeGuess, intended: DocType?): IntakeAction = when {
        guess.type == DocType.UNKNOWN -> IntakeAction.ASK_TYPE
        intended == null || guess.type == intended -> IntakeAction.PROCEED
        guess.type == DocType.CONFIRMATION -> IntakeAction.WARN_SUGGEST_CONFIRMATION
        else -> IntakeAction.WARN_SUGGEST_BILL
    }
}
