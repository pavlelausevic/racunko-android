package com.racunko.app.parser

/**
 * Text normalization used by every matcher: lowercase, Serbian Cyrillic -> Latin
 * transliteration (including digraphs), Latin diacritic folding, whitespace collapse.
 */
object Normalizer {

    private val MAP: Map<Char, String> = mapOf(
        // Serbian Cyrillic (lowercase; input is lowercased first)
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "dj",
        'е' to "e", 'ж' to "z", 'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k",
        'л' to "l", 'љ' to "lj", 'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'ћ' to "c", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "c", 'џ' to "dz", 'ш' to "s",
        // Latin diacritics
        'š' to "s", 'č' to "c", 'ć' to "c", 'ž' to "z", 'đ' to "dj"
    )

    private val WS = Regex("\\s+")

    fun norm(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val sb = StringBuilder(s.length + 8)
        for (ch in s.lowercase()) sb.append(MAP[ch] ?: ch)
        return sb.toString().replace(WS, " ")
    }
}
