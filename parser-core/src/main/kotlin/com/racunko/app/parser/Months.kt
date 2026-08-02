package com.racunko.app.parser

/** Billing month + 2-digit year, e.g. maj26. */
data class MonthYear(val month: Int, val year2: Int)

object Months {
    /** Filename month names are ALWAYS Serbian lowercase, regardless of UI language. */
    val NAMES = listOf(
        "januar", "februar", "mart", "april", "maj", "jun",
        "jul", "avgust", "septembar", "oktobar", "novembar", "decembar"
    )

    fun name(m: Int): String = NAMES.getOrElse(m - 1) { "" }

    fun token(my: MonthYear): String = name(my.month) + my.year2.toString().padStart(2, '0')

    /** Parse a manual "maj26"-style token; returns null if not recognized. */
    fun fromToken(input: String): MonthYear? {
        val m = Regex("^([a-zšđčćž]+)\\s*(\\d{2})$").find(input.trim().lowercase()) ?: return null
        val prefix = Normalizer.norm(m.groupValues[1]).take(3)
        val idx = NAMES.indexOfFirst { it.startsWith(prefix) }
        if (idx < 0) return null
        return MonthYear(idx + 1, m.groupValues[2].toInt())
    }
}
