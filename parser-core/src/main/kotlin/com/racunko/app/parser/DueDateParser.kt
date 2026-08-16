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
        BARE_VALUTA
    )

    private const val DATE = "(\\d{1,2})\\s*[.\\-/]\\s*(\\d{1,2})\\s*[.\\-/]\\s*(\\d{2,4})"

    private val PATTERNS = LABELS.map { label -> Regex("$label[^0-9]{0,25}$DATE") }

    /**
     * Second pass, for TABLE layouts. InfoStan prints the four column headings in
     * one row and their values in the next, so „датум доспећа" is separated from
     * its date by the other columns' values — the bill number comes first, and the
     * strict pass above stops at its digits. Widening the strict pass is not the
     * answer: it would let the ISSUE date win on every label-adjacent layout,
     * which is the one mistake this parser exists to avoid.
     *
     * So: only when the strict pass finds nothing, read a bounded window after the
     * label and take the LATEST date in it. Latest is not a positional guess — of
     * the dates a bill prints in that row (issue, delivery, deadline), the
     * deadline is by definition the last one.
     *
     * The bare „валута" label is excluded here: no issuer prints it as a column
     * heading, and it is the one label loose enough to catch a stray date.
     * Found on device 14.08.2026 — three InfoStan bills read `due date?` while
     * their deadline was printed on the page.
     */
    private const val BARE_VALUTA = "\\bvalut[ae]\\b"
    private val TABLE_LABELS = LABELS.filterNot { it == BARE_VALUTA }.map { Regex(it) }
    private val ANY_DATE = Regex(DATE)
    private const val TABLE_WINDOW = 160

    private fun MatchResult.toDate(): LocalDate? {
        val day = groupValues[1].toIntOrNull() ?: return null
        val month = groupValues[2].toIntOrNull() ?: return null
        val rawYear = groupValues[3].toIntOrNull() ?: return null
        val year = if (rawYear < 100) 2000 + rawYear else rawYear
        if (month !in 1..12 || day !in 1..31 || year !in 2020..2045) return null
        // LocalDate.of rejects 31.02 and friends — a malformed date is no date.
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** Deadline from the document text, or null when none is printed. */
    fun parse(text: String?): LocalDate? {
        val t = Normalizer.norm(text)
        if (t.isEmpty()) return null
        for (pattern in PATTERNS) {
            (pattern.find(t) ?: continue).toDate()?.let { return it }
        }
        for (label in TABLE_LABELS) {
            val m = label.find(t) ?: continue
            val from = m.range.last + 1
            val window = t.substring(from, minOf(t.length, from + TABLE_WINDOW))
            ANY_DATE.findAll(window).mapNotNull { it.toDate() }.maxOrNull()?.let { return it }
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
