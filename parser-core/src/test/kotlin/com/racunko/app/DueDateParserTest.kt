package com.racunko.app

import com.racunko.app.parser.DueDateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * v1.6 — the deadline is read from its printed label or not at all. The issue
 * date must never stand in for it, and „Valuta: RSD" (the currency) must never
 * be mistaken for a value date.
 */
class DueDateParserTest {

    @Test
    fun latinLabel_isRead() {
        assertEquals(
            LocalDate.of(2026, 9, 15),
            DueDateParser.parse("Ukupno za uplatu 11.151,71\nRok plaćanja: 15.09.2026.")
        )
    }

    @Test
    fun cyrillicLabels_areRead() {
        assertEquals(
            LocalDate.of(2026, 9, 15),
            DueDateParser.parse("РОК ПЛАЋАЊА 15.09.2026.")
        )
        assertEquals(
            LocalDate.of(2026, 10, 5),
            DueDateParser.parse("Датум доспећа: 05.10.2026.")
        )
    }

    @Test
    fun issueDateAlone_isNeverADeadline() {
        val onlyIssued = """
            Datum izdavanja: 01.09.2026.
            Datum računa 01.09.2026.
            Obračunski period 01.08.2026 - 31.08.2026
        """.trimIndent()
        assertNull(DueDateParser.parse(onlyIssued))
        // …and when both are printed, the labelled deadline is the one taken.
        assertEquals(
            LocalDate.of(2026, 9, 20),
            DueDateParser.parse("$onlyIssued\nValuta plaćanja 20.09.2026.")
        )
    }

    @Test
    fun currencyIsNotAValueDate_andGarbageIsRejected() {
        assertNull(DueDateParser.parse("Valuta: RSD  Iznos: 1.070,00"))
        assertNull(DueDateParser.parse("Rok plaćanja: 45.13.2026."))   // impossible date
        assertNull(DueDateParser.parse("Rok plaćanja: 31.02.2026."))   // February 31st
        assertNull(DueDateParser.parse("Rok plaćanja: 15.09.1999."))   // outside 2020..2045
        assertNull(DueDateParser.parse(null))
        assertNull(DueDateParser.parse(""))
    }

    @Test
    fun twoDigitYearAndSeparators() {
        assertEquals(LocalDate.of(2026, 9, 15), DueDateParser.parse("Rok plaćanja 15.09.26."))
        assertEquals(LocalDate.of(2026, 9, 15), DueDateParser.parse("Rok za plaćanje: 15/09/2026"))
        assertEquals(LocalDate.of(2026, 9, 5), DueDateParser.parse("Platiti do 5-9-2026"))
    }

    @Test
    fun daysUntil_andReminderWindow() {
        val today = LocalDate.of(2026, 9, 12)
        val due = LocalDate.of(2026, 9, 15)
        assertEquals(3L, DueDateParser.daysUntil(due, today))
        // an overdue bill counts negative and is always inside the window
        assertEquals(-2L, DueDateParser.daysUntil(LocalDate.of(2026, 9, 10), today))
        assertTrue(DueDateParser.isDueWithin(LocalDate.of(2026, 9, 10), today, 3))

        assertTrue(DueDateParser.isDueWithin(due, today, 3))
        assertFalse(DueDateParser.isDueWithin(due, today, 1))
        assertFalse(DueDateParser.isDueWithin(null, today, 30))
    }
}
