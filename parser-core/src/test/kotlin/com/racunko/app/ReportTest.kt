package com.racunko.app

import com.racunko.app.parser.MonthYear
import com.racunko.app.parser.ProviderNames
import com.racunko.app.parser.Report
import com.racunko.app.parser.ReportLine
import org.junit.Assert.assertEquals
import org.junit.Test

/** v1.4.2 Change 5 — summary report grouping/formatting (pure, deterministic). */
class ReportTest {

    private val jul = MonthYear(7, 26)
    private val avg = MonthYear(8, 26)

    @Test
    fun specExample_oneAddressOneMonth_alignedColumnsAndSubtotal() {
        val lines = listOf(
            ReportLine("BDS95", jul, "InfoStan", 6500),
            ReportLine("BDS95", jul, "EPS", 2500)
        )
        val expected = """
            JUL  BDS95
            InfoStan   6.500 RSD
            EPS        2.500 RSD
            ∑          9.000 RSD
        """.trimIndent()
        assertEquals(expected, Report.buildSummary(lines))
    }

    @Test
    fun twoAddresses_twoHeaderedGroupsSeparatedByBlankLine() {
        val lines = listOf(
            ReportLine("BDS95", jul, "InfoStan", 6500),
            ReportLine("KD7", jul, "Yettel", 2699)
        )
        val expected = """
            JUL  BDS95
            InfoStan   6.500 RSD
            ∑          6.500 RSD

            JUL  KD7
            Yettel   2.699 RSD
            ∑        2.699 RSD
        """.trimIndent()
        assertEquals(expected, Report.buildSummary(lines))
    }

    @Test
    fun monthsWithinAnAddressAreChronological() {
        val lines = listOf(
            ReportLine("BDS95", avg, "EPS", 3000),
            ReportLine("BDS95", jul, "EPS", 2500)
        )
        val out = Report.buildSummary(lines)
        assertEquals(0, out.indexOf("JUL  BDS95"))        // July block first
        assert(out.indexOf("AVGUST  BDS95") > out.indexOf("JUL  BDS95"))
    }

    @Test
    fun providerDisplayNames() {
        assertEquals("InfoStan", ProviderNames.display("infostan"))
        assertEquals("EPS", ProviderNames.display("eps"))
        assertEquals("SZ", ProviderNames.display("sz"))
        assertEquals("Yettel", ProviderNames.display("yettel"))
    }

    @Test
    fun thousandsSeparatorHonored_integersOnly() {
        assertEquals("1.234.567", Report.formatAmount(1234567, '.'))
        assertEquals("1 000", Report.formatAmount(1000, ' '))
        assertEquals("500", Report.formatAmount(500, '.'))
    }

    @Test
    fun singleBill_headerLineAndSubtotalEqualToTheAmount() {
        val lines = listOf(ReportLine("MR1", jul, "MTS", 3282))
        val expected = """
            JUL  MR1
            MTS   3.282 RSD
            ∑     3.282 RSD
        """.trimIndent()
        assertEquals(expected, Report.buildSummary(lines))
    }
}
