package com.racunko.app.parser

/**
 * Provider display names (v1.4.2 Change 5) — the nice-cased form shown in the
 * summary report and elsewhere, distinct from the lowercase filename token.
 * The bank/issuer template IDs stay lowercase; this is presentation only.
 */
object ProviderNames {
    private val MAP = mapOf(
        "infostan" to "InfoStan",
        "eps" to "EPS",
        "mts" to "MTS",
        "yettel" to "Yettel",
        "sz" to "SZ"
    )

    fun display(token: String): String =
        MAP[token.lowercase()] ?: token.replaceFirstChar { it.uppercase() }
}

/** One selected bill, flattened for the report. */
data class ReportLine(
    val addressLabel: String,
    val month: MonthYear,
    val providerDisplay: String,
    val amount: Long
)

/**
 * Summary report (Change 5): pure, deterministic grouping + formatting so a
 * tenant can copy/share one clean block per address+month. Lives in parser-core
 * and is unit-tested; the locale thousands separator is injected, never
 * hard-coded.
 *
 *   JUL  BDS95
 *   InfoStan  6.500 RSD
 *   EPS       2.500 RSD
 *   ∑         9.000 RSD
 *
 * v1.6 — the columns are aligned for a PROPORTIONAL font, not a monospace one.
 * This text exists to be pasted into Viber/WhatsApp/a note, and those render it
 * in the system UI font where „InfoStan" is far wider than „EPS" even though it
 * is only five characters longer. Counting characters therefore lined the block
 * up nowhere it was actually read. See [Padding].
 */
object Report {

    /** Gap between the provider column and the amount column: one em. */
    private const val GUTTER_EM = 1000
    private const val SUFFIX = " RSD"
    private const val SUM = "∑"

    fun buildSummary(lines: List<ReportLine>, thousands: Char = '.'): String {
        if (lines.isEmpty()) return ""
        val blocks = mutableListOf<String>()
        // addresses in first-appearance order, months chronological within an address
        for (address in lines.map { it.addressLabel }.distinct()) {
            val forAddress = lines.filter { it.addressLabel == address }
            val months = forAddress.map { it.month }.distinct()
                .sortedWith(compareBy({ it.year2 }, { it.month }))
            for (my in months) {
                blocks.add(formatGroup(address, my, forAddress.filter { it.month == my }, thousands))
            }
        }
        return blocks.joinToString("\n\n")
    }

    private fun formatGroup(
        address: String,
        month: MonthYear,
        group: List<ReportLine>,
        thousands: Char
    ): String {
        val header = Months.NAMES[month.month - 1].uppercase() + "  " + address
        val labels = group.map { it.providerDisplay } + SUM
        val amounts = group.map { formatAmount(it.amount, thousands) } +
            formatAmount(group.sumOf { it.amount }, thousands)

        // Labels are left-aligned to the widest label; amounts are right-aligned
        // to the widest amount, so the „ RSD" suffixes end on one edge.
        val labelCol = labels.maxOf { Padding.widthOf(it) }
        val amountCol = amounts.maxOf { Padding.widthOf(it) }

        val sb = StringBuilder(header)
        for (i in labels.indices) {
            sb.append('\n')
                .append(labels[i])
                .append(Padding.spacer(labelCol - Padding.widthOf(labels[i]) + GUTTER_EM))
                .append(Padding.spacer(amountCol - Padding.widthOf(amounts[i])))
                .append(amounts[i])
                .append(SUFFIX)
        }
        return sb.toString()
    }

    /** Integer RSD with a thousands separator; no decimals. */
    fun formatAmount(value: Long, thousands: Char): String {
        val negative = value < 0
        val digits = (if (negative) -value else value).toString()
        val sb = StringBuilder()
        for ((i, c) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(thousands)
            sb.append(c)
        }
        return (if (negative) "-" else "") + sb
    }
}

/**
 * Column alignment for text that will be read in a proportional font.
 *
 * Widths are advances in **thousandths of an em**, taken from Roboto — the
 * system font on Android, and therefore the font the report is rendered in both
 * in the app's own preview and in every messaging app it gets pasted into. Any
 * humanist sans (Roboto, Noto, Helvetica, Arial, San Francisco) is close enough
 * to these numbers that the columns still read as columns; the table does not
 * have to be exact, it has to be far better than "every character is equally
 * wide", which is what counting characters assumes.
 *
 * The gap is then filled with real space glyphs of known width — EN SPACE
 * (½ em), the ordinary space (¼ em) and HAIR SPACE (⅒ em) — so the residual
 * error per line is under a tenth of an em. Only one ordinary space is ever
 * emitted in a row, which keeps the block intact if something on the receiving
 * end collapses runs of ASCII whitespace.
 */
internal object Padding {

    /** U+2002 EN SPACE, half an em. */
    private const val EN_SPACE = ' '
    /** U+200A HAIR SPACE, a tenth of an em. */
    private const val HAIR_SPACE = ' '

    private const val DIGIT = 569
    private const val SPACE = 248
    private const val FALLBACK_UPPER = 680
    private const val FALLBACK_OTHER = 560

    // Roboto Regular advances, per 1000 em, in alphabet order.
    private val UPPER = intArrayOf(
        667, 653, 680, 682, 608, 595, 702, 720, 286, 552, 665, 569, 881,
        741, 721, 654, 721, 661, 654, 611, 681, 656, 928, 646, 619, 607
    )
    private val LOWER = intArrayOf(
        561, 578, 535, 578, 550, 349, 578, 573, 255, 255, 522, 255, 877,
        573, 582, 578, 578, 371, 522, 350, 572, 512, 778, 509, 512, 486
    )

    /** Width of [text] in thousandths of an em. */
    fun widthOf(text: String): Int = text.fold(0) { acc, c -> acc + advance(c) }

    private fun advance(c: Char): Int = when {
        c in '0'..'9' -> DIGIT
        c == EN_SPACE -> 500
        c == HAIR_SPACE -> 100
        c in 'a'..'z' -> LOWER[c - 'a']
        c in 'A'..'Z' -> UPPER[c - 'A']
        // Serbian latin: the diacritic rides above the base letter, same advance.
        c == 'č' || c == 'ć' -> LOWER['c' - 'a']
        c == 'š' -> LOWER['s' - 'a']
        c == 'ž' -> LOWER['z' - 'a']
        c == 'đ' -> LOWER['d' - 'a']
        c == 'Č' || c == 'Ć' -> UPPER['C' - 'A']
        c == 'Š' -> UPPER['S' - 'A']
        c == 'Ž' -> UPPER['Z' - 'A']
        c == 'Đ' -> UPPER['D' - 'A']
        c == ' ' || c == '.' || c == ',' || c == ':' || c == ';' || c == '\'' -> SPACE
        c == '∑' -> 620
        c == '-' || c == '–' -> 341
        c == '/' -> 431
        c == '(' || c == ')' -> 341
        c == '+' -> 596
        c == '%' -> 745
        c == '&' -> 664
        c.isUpperCase() -> FALLBACK_UPPER
        else -> FALLBACK_OTHER
    }

    /**
     * A run of spaces about [target] thousandths of an em wide. Never negative,
     * never longer than it needs to be; residual error stays under 100 (⅒ em).
     */
    fun spacer(target: Int): String {
        if (target <= 0) return ""
        var left = target
        val sb = StringBuilder()
        while (left >= 500) { sb.append(EN_SPACE); left -= 500 }
        if (left >= SPACE) { sb.append(' '); left -= SPACE }
        while (left >= 100) { sb.append(HAIR_SPACE); left -= 100 }
        return sb.toString()
    }
}
