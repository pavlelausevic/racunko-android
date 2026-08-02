package com.racunko.app.parser

/**
 * Filename format (§4):
 *   bill          {provider}_{ADDRESS}_{month}{yy}_{amount}.pdf
 *   confirmation  uplata_{provider}_{ADDRESS}_{month}{yy}_{amount}.pdf
 *   QR image      {provider}_{ADDRESS}_{month}{yy}_{amount}_QR.png
 */
object BillName {

    private val MONTH_ALT = Months.NAMES.joinToString("|")

    /** Matches an already-processed filename so reprocessing keeps the name. */
    val PROCESSED: Regex =
        Regex("^(uplata_)?[a-z0-9]+_[A-Za-z0-9\\-]+_($MONTH_ALT)\\d{2}_\\d+(_\\d+)?\\.pdf$")

    fun build(
        provider: String?,
        address: String?,
        month: MonthYear?,
        amount: Long?,
        confirmation: Boolean = false
    ): String {
        val mon = month?.let { Months.token(it) } ?: ""
        val parts = listOf(provider.orEmpty(), address.orEmpty(), mon, amount?.toString() ?: "")
        return (if (confirmation) "uplata_" else "") + parts.joinToString("_") { it.ifEmpty { "X" } }
    }

    fun isComplete(provider: String?, address: String?, month: MonthYear?, amount: Long?): Boolean =
        !provider.isNullOrEmpty() && !address.isNullOrEmpty() && month != null && amount != null

    /** Collision handling: append _2, _3, … until the name is free. */
    fun unique(base: String, ext: String, existing: Set<String>): String {
        var name = "$base$ext"
        var i = 2
        while (name in existing) {
            name = "${base}_$i$ext"
            i++
        }
        return name
    }

    /** Fields recovered from an already-processed file name (v1.4.3 backfill). */
    data class Parsed(
        val confirmation: Boolean,
        val provider: String,
        val address: String,
        val month: MonthYear,
        val amount: Long
    )

    /**
     * Parses a processed file name back into fields, so a bill already on disk
     * (from a previous session, with no DB record) can be shown as processed
     * without reprocessing. Tolerates a `_2` collision suffix.
     */
    fun parse(fileName: String): Parsed? {
        var base = fileName.substringBeforeLast('.')
        val confirmation = base.startsWith("uplata_")
        if (confirmation) base = base.removePrefix("uplata_")
        val parts = base.split('_')
        if (parts.size < 4) return null
        val month = Months.fromToken(parts[2]) ?: return null
        val amount = parts[3].toLongOrNull() ?: return null
        return Parsed(confirmation, parts[0], parts[1], month, amount)
    }

    /** Sanitize a manually entered provider/address token for use in a filename. */
    fun sanitizeToken(v: String): String =
        v.trim()
            .replace(Regex("[^\\w šđčćžŠĐČĆŽ\\-]"), "")
            .replace(Regex("\\s+"), "-")
}
