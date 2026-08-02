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
 *   InfoStan   6.500 RSD
 *   EPS        2.500 RSD
 *   ∑          9.000 RSD
 */
object Report {

    private const val GUTTER = "   "
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
        val amounts = group.map { formatAmount(it.amount, thousands) }
        val totalStr = formatAmount(group.sumOf { it.amount }, thousands)

        val labelWidth = (group.map { it.providerDisplay } + SUM).maxOf { it.length }
        val amountWidth = (amounts + totalStr).maxOf { it.length }

        val sb = StringBuilder(header)
        for (i in group.indices) {
            sb.append('\n')
                .append(group[i].providerDisplay.padEnd(labelWidth))
                .append(GUTTER).append(amounts[i].padStart(amountWidth)).append(SUFFIX)
        }
        sb.append('\n')
            .append(SUM.padEnd(labelWidth))
            .append(GUTTER).append(totalStr.padStart(amountWidth)).append(SUFFIX)
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
