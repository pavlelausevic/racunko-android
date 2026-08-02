package com.racunko.app

import com.racunko.app.parser.MonthDetector
import com.racunko.app.parser.MonthYear
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.4.1 Bug 1 — the InfoStan billing month must be read from the hyphen-anchored
 * suffix of the RO, not from the first "..0-." found anywhere in the ident. The
 * old pattern accidentally worked for May samples but misread a June bill whose
 * ident produced "...080-" earlier in the number (`avgust98` instead of `jun26`).
 */
class InfostanMonthTest {

    private fun month(ro: String): MonthYear? =
        MonthDetector.detect("infostan", mapOf("RO" to ro), null)

    @Test
    fun juneBill_theReportedFailure() {
        // Racun_598102.pdf — was misparsed as avgust98
        assertEquals(MonthYear(6, 26), month("11800598102080-26069-1"))
    }

    @Test
    fun mayBill_withModelPrefix_staysCorrect() {
        assertEquals(MonthYear(5, 26), month("11800512345011-26050-1"))
    }

    @Test
    fun mayBill_withoutModelPrefix_staysCorrect() {
        assertEquals(MonthYear(5, 26), month("800614276087-26050-1"))
    }
}
