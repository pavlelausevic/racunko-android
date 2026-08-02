package com.racunko.app.parser

/**
 * Serbian bank accounts print as BBB-MMMMMMMMM-CC while the IPS QR R field
 * stores the full 18-digit form with the middle part zero-padded to 13 digits
 * (190-99870-10 <-> 190000000009987010).
 */
object Accounts {

    private val RX_DASHED = Regex("\\b(\\d{3})-(\\d{1,13})-(\\d{2})\\b")
    private val RX_PLAIN18 = Regex("\\b(\\d{18})\\b")

    fun normalize(bank: String, middle: String, control: String): String =
        bank + middle.padStart(13, '0') + control

    /** Every 18-digit-normalized account candidate found in raw text. */
    fun extractAll(text: String?): Set<String> {
        val out = LinkedHashSet<String>()
        if (text == null) return out
        for (m in RX_DASHED.findAll(text)) {
            out.add(normalize(m.groupValues[1], m.groupValues[2], m.groupValues[3]))
        }
        for (m in RX_PLAIN18.findAll(text)) out.add(m.groupValues[1])
        return out
    }
}
