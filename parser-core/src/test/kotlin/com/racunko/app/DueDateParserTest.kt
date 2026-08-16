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

    // ------------------------------------------------- per-issuer layouts
    //
    // v1.6.2: one case per issuer Računko actually meets, each mirroring that
    // bill's real LAYOUT — the deadline label in its printed position, and the
    // decoy dates that sit around it. Every issuer prints at least one date that
    // must NOT win, and they are all different: a complaint deadline, a contract
    // expiry, a discount cut-off. Those decoys are the reason these tests exist;
    // reading the right label out of a clean one-line string proves nothing.
    //
    // The text is SYNTHETIC — reconstructed structure with invented names,
    // addresses, numbers and dates. Real bills were used to confirm the label
    // wording and field order, and never left the maintainer's machine.

    @Test
    fun mts_complaintDeadlineDoesNotWin() {
        val text = """
            Racun broj: 10-000-000-0000000
            Mesto i datum izdavanja:
            Beograd, 01.08.2026.
            Datum prometa:
            31.07.2026.
            Rok za placanje:
            15.08.2026.
            Za period: 01.07.2026. - 31.07.2026.
            UKUPNO ZA PLACANJE: 3.559,41
            Ovaj racun je punovazan bez potpisa i pecata.
            Rok za prigovor: 14.09.2026. god.
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 15), DueDateParser.parse(text))
    }

    @Test
    fun eps_cyrillicDeadlineAmongIssueDates() {
        val text = """
            РАЧУН ЗА ЕЛЕКТРИЧНУ ЕНЕРГИЈУ
            Период обрачуна: 02.07.2026 - 04.08.2026.
            Датум издавања рачуна: 06.08.2026.
            Датум промета и акцизе: 04.08.2026.
            В ЗА УПЛАТУ ЗА ЕЛЕКТРИЧНУ ЕНЕРГИЈУ (А+Б) 5.232,50 дин
            Рок за плаћање: 28.08.2026.
            Рок за приговор је 8 дана од дана пријема рачуна.
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 28), DueDateParser.parse(text))
    }

    /**
     * v1.7: „Датум валуте" is a printed value date and IS the deadline — the
     * genitive ending is the only thing separating it from the bare label, and
     * without it an SZ bill's deadline went unread on the device.
     */
    @Test
    fun sz_datumValuteIsTheDeadline() {
        val text = """
            Задужење за услуге за месец ЈУЛ 2026
            Датум промета: 31.07.2026.
            Датум валуте: 15.08.2026.
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 15), DueDateParser.parse(text))
    }

    /**
     * The same widening must not revive the decoy: „Валута РСД" is a currency
     * column. What stops it is the requirement for a real date, not the ending.
     */
    @Test
    fun valuteEndingStillRejectsTheCurrencyColumn() {
        val slip = """
            Валута  РСД   Износ  1.234,00
            Датум промета: 31.07.2026.
        """.trimIndent()
        assertNull(DueDateParser.parse(slip))
    }

    /** The payment slip alone prints „Валута РСД" — a currency, not a value date. */
    @Test
    fun eps_slipCurrencyColumnIsNotADeadline() {
        val slipOnly = """
            Шиф. плаћ. 189   Валута  РСД   Износ
            Шиф. ком. 114    Текући рачун примаоца
            Број модела 97   Позив на број
        """.trimIndent()
        assertNull(DueDateParser.parse(slipOnly))
    }

    /**
     * The layout InfoStan actually prints, found on device 14.08.2026: the four
     * column headings in one row, their values in the next. „Датум доспећа" is
     * therefore separated from its date by the BILL NUMBER and two other dates,
     * and the label-adjacent pass cannot reach it — three real bills read
     * `due date?` while their deadline was on the page.
     *
     * The case below is the real STRUCTURE with invented values (§9): the ident,
     * the bill number and the dates are made up. Break the table fallback and
     * this test fails; break it by widening the strict pass instead, and
     * [tableFallbackNeverBeatsAnAdjacentLabel] fails.
     */
    @Test
    fun infostan_tableLayout_dueDateIsInTheValueRow() {
        val text = """
            Шифра корисника (ИДЕНТ): 0123456
            Рачун за јул 2026. године
            Број рачуна
            Место и датум издавања
            Датум испоруке добара и услуга
            Датум доспећа
            2026/07-0123456
            Београд, 31.07.2026. године
            31.07.2026. године
            31.08.2026. године
            СП
            Назив пружаоца услуге
            За уплату
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 31), DueDateParser.parse(text))
    }

    /**
     * The fallback must stay a FALLBACK. Yettel prints „Рок за плаћање" right next
     * to its date and, a few lines on, a much later contract-expiry date. If the
     * window pass ever ran first — or ran at all here — the deadline would jump to
     * 2027 and the bill would look like it has months left.
     */
    @Test
    fun tableFallbackNeverBeatsAnAdjacentLabel() {
        val text = """
            Датум израде рачуна: 31.07.2026.
            Рок за плаћање: 20.08.2026.
            Место издавања рачуна: Београд
            Уговорна обавеза истиче: 31.03.2027.
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 20), DueDateParser.parse(text))
    }

    /** A bill with no deadline label at all still has no deadline. */
    @Test
    fun tableFallbackDoesNotInventADeadline() {
        val text = """
            Датум издавања рачуна: 31.07.2026.
            Датум промета: 31.07.2026.
            Период обрачуна: 01.07.2026 - 31.07.2026
        """.trimIndent()
        assertNull(DueDateParser.parse(text))
    }

    @Test
    fun infostan_discountCutoffDoesNotBeatDospece() {
        val text = """
            Датум испоруке добара и услуга: 31.07.2026. године
            Датум доспећа: 31.08.2026. године
            Стање обавеза на дан 31.07.2026. године са уплатама
            евидентираним до 20.07.2026. године
            Попуст за наредни месец остварује се уплатом укупних обавеза до
            15.08.2026. године, а право на субвенцију уплатом свих обавеза
            доспелих до 30.06.2026. године.
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 31), DueDateParser.parse(text))
    }

    @Test
    fun sbbYettel_rokPlacanjaAmongServiceDates() {
        val text = """
            Racun broj: 0000000000
            Rok placanja: 25.08.2026
            Period izvrsenja usluga: 01.07.2026 - 31.07.2026
            Datum prometa usluga: 31.07.2026
            Datum izdavanja racuna: 31.07.2026
            Rok za podnosenje prigovora: 30 DANA
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 25), DueDateParser.parse(text))
    }

    /** Yettel prints a CONTRACT expiry a year out; it must never be the deadline. */
    @Test
    fun yettel_contractExpiryDoesNotWin() {
        val text = """
            Obracunski period: 01.07.2026. - 31.07.2026.
            Datum izrade racuna: 31.07.2026.
            Datum prometa: 31.07.2026.
            Rok za placanje: 20.08.2026.
            Ugovorna obaveza istice: 31.03.2027.
            Datum dospeca ili rok za uplatu racuna je 20 dana od datuma izrade racuna.
            UKUPNO ZA PLACANJE 2.999,00
        """.trimIndent()
        assertEquals(LocalDate.of(2026, 8, 20), DueDateParser.parse(text))
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
