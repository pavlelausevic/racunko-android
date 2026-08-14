package com.racunko.app.parser

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * v1.6 — the payment deadline printed on a bill („rok plaćanja", „datum
 * dospeća", „valuta plaćanja"). Read like every other field here: anchored to
 * its printed label, never by position, and never invented. A bill that does
 * not print one simply has none, and the card offers an optional manual entry.
 *
 * It deliberately does NOT match the ISSUE date („datum izdavanja", „datum
 * računa"): silently presenting the issue date as a deadline would be worse
 * than showing no deadline at all.
 */
object DueDateParser {

    /**
     * Labels that genuinely mark a deadline, most specific first. Every one of
     * them must be followed by an actual date within ~25 characters, which is
     * also what keeps „Valuta: RSD" (the currency) from ever matching.
     */
    private val LABELS = listOf(
        // both printed endings occur in the wild: „rok plaćanja" and „rok za plaćanje"
        "rok (?:za )?placanj[ea]",
        "rok uplate",
        "datum dospec[ae]",
        "dospeva (?:za )?placanj[ea]",
        "dospece",
        "valut[ae] placanj[ea]",
        "(?:uplatiti|platiti|plativo|placanje) do",
        // Both the bare label and „datum valute" occur; the genitive ending is
        // the whole difference between them, and without it an SZ bill's value
        // date went unread (found on device 14.08.2026). Widening the ending
        // cannot revive the „Валута РСД" decoy — that one is stopped by the date
        // requirement below, not by the ending.
        "\\bvalut[ae]\\b"
    )

    private val PATTERNS = LABELS.map { label ->
        Regex("$label[^0-9]{0,25}(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{2,4})")
    }

    /** Deadline from the document text, or null when none is printed. */
    fun parse(text: String?): LocalDate? {
        val t = Normalizer.norm(text)
        if (t.isEmpty()) return null
        for (pattern in PATTERNS) {
            val m = pattern.find(t) ?: continue
            val day = m.groupValues[1].toIntOrNull() ?: continue
            val month = m.groupValues[2].toIntOrNull() ?: continue
            val rawYear = m.groupValues[3].toIntOrNull() ?: continue
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            if (month !in 1..12 || day !in 1..31 || year !in 2020..2045) continue
            // LocalDate.of rejects 31.02 and friends — a malformed date is no date.
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return it }
        }
        return null
    }

    /** Days from [today] to the deadline; negative means it has already passed. */
    fun daysUntil(due: LocalDate?, today: LocalDate): Long? =
        due?.let { ChronoUnit.DAYS.between(today, it) }

    /**
     * Should this bill be surfaced in the „dospeva" banner? Only an UNPAID bill
     * with a known deadline qualifies, and only inside its own reminder window
     * — an overdue bill always qualifies.
     */
    fun isDueWithin(due: LocalDate?, today: LocalDate, daysBefore: Int): Boolean {
        val days = daysUntil(due, today) ?: return false
        return days <= daysBefore
    }
}
