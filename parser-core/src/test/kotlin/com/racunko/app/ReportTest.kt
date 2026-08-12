package com.racunko.app

import com.racunko.app.parser.MonthYear
import com.racunko.app.parser.Padding
import com.racunko.app.parser.ProviderNames
import com.racunko.app.parser.Report
import com.racunko.app.parser.ReportLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.2 Change 5 — summary report grouping/formatting (pure, deterministic).
 *
 * v1.6 changed what „aligned" means. The block is pasted into apps that render
 * it in a proportional font, so the columns are spaced by estimated glyph WIDTH,
 * not by character count. The assertions below pin that property — every amount
 * in a block starts at the same width from the left margin — instead of pinning
 * a literal run of ASCII spaces, which is exactly the thing that turned out not
 * to align anywhere the report is actually read.
 */
class ReportTest {

    private val jul = MonthYear(7, 26)
    private val avg = MonthYear(8, 26)

    /** Width, in thousandths of an em, from each line's start to its amount. */
    private fun columnsOf(block: String): List<Int> =
        block.lines().drop(1).filter { it.isNotBlank() }.map { line ->
            Padding.widthOf(line.substring(0, line.indexOfFirst { it.isDigit() }))
        }

    @Test
    fun specExample_oneAddressOneMonth_headerRowsAndSubtotal() {
        val lines = listOf(
            ReportLine("BDS95", jul, "InfoStan", 6500),
            ReportLine("BDS95", jul, "EPS", 2500)
        )
        val out = Report.buildSummary(lines)
        val rows = out.lines()
        assertEquals("JUL  BDS95", rows[0])
        assertEquals(4, rows.size)                       // header + 2 bills + sum
        assertTrue(rows[1].startsWith("InfoStan"))
        assertTrue(rows[2].startsWith("EPS"))
        assertTrue(rows[3].startsWith("∑"))
        assertTrue(rows[1].endsWith("6.500 RSD"))
        assertTrue(rows[2].endsWith("2.500 RSD"))
        assertTrue(rows[3].endsWith("9.000 RSD"))        // subtotal
    }

    /**
     * The point of the whole exercise: „InfoStan" is five characters longer than
     * „EPS" but nowhere near five spaces wider, so with amounts of equal length
     * the two must still begin within a tenth of an em of each other. Amounts
     * chosen so the sum has the same digit count as the rows — right-alignment is
     * covered separately below.
     */
    @Test
    fun amountsLineUpByWidth_notByCharacterCount() {
        // amounts chosen so the rows AND the subtotal are all four digits
        val lines = listOf(
            ReportLine("JA2", jul, "EPS", 4200),
            ReportLine("JA2", jul, "InfoStan", 4650)
        )
        val cols = columnsOf(Report.buildSummary(lines))
        assertEquals(3, cols.size)                        // 2 bills + ∑, all 4-digit
        val spread = cols.max() - cols.min()
        assertTrue("amount column spread was $spread/1000 em", spread < 100)
        // and the naive character count really would NOT have lined up
        assertTrue(Padding.widthOf("InfoStan") - Padding.widthOf("EPS") > 1500)
    }

    /** Right-aligned amounts: a 3-digit and a 5-digit amount end on one edge. */
    @Test
    fun amountsAreRightAligned() {
        val lines = listOf(
            ReportLine("BDS95", jul, "EPS", 500),
            ReportLine("BDS95", jul, "MTS", 12000)
        )
        val rows = Report.buildSummary(lines).lines().drop(1)
        val widths = rows.map { Padding.widthOf(it) }
        assertTrue("line widths $widths", widths.max() - widths.min() < 100)
    }

    @Test
    fun twoAddresses_twoHeaderedGroupsSeparatedByBlankLine() {
        val lines = listOf(
            ReportLine("BDS95", jul, "InfoStan", 6500),
            ReportLine("KD7", jul, "Yettel", 2699)
        )
        val out = Report.buildSummary(lines)
        val blocks = out.split("\n\n")
        assertEquals(2, blocks.size)
        assertEquals("JUL  BDS95", blocks[0].lines()[0])
        assertEquals("JUL  KD7", blocks[1].lines()[0])
        assertTrue(blocks[0].lines()[2].endsWith("6.500 RSD"))   // ∑ = the single bill
    }

    @Test
    fun monthsWithinAnAddressAreChronological() {
        val lines = listOf(
            ReportLine("BDS95", avg, "EPS", 3000),
            ReportLine("BDS95", jul, "EPS", 2500)
        )
        val out = Report.buildSummary(lines)
        assertEquals(0, out.indexOf("JUL  BDS95"))        // July block first
        assertTrue(out.indexOf("AVGUST  BDS95") > out.indexOf("JUL  BDS95"))
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
        val rows = Report.buildSummary(lines).lines()
        assertEquals("JUL  MR1", rows[0])
        assertTrue(rows[1].startsWith("MTS"))
        assertTrue(rows[1].endsWith("3.282 RSD"))
        assertTrue(rows[2].endsWith("3.282 RSD"))
    }

    // ---------------------------------------------------------------- padding

    @Test
    fun spacerNeverOvershoots_andIsEmptyWhenNothingToFill() {
        assertEquals("", Padding.spacer(0))
        assertEquals("", Padding.spacer(-500))
        for (target in 1..3000) {
            val w = Padding.widthOf(Padding.spacer(target))
            assertTrue("target=$target got=$w", w <= target && target - w < 100)
        }
    }

    /** Only ever ONE ASCII space in a run, so HTML-ish receivers can't collapse it. */
    @Test
    fun spacerEmitsAtMostOneOrdinarySpace() {
        for (target in 1..3000) {
            assertTrue(!Padding.spacer(target).contains("  "))
        }
    }
}
