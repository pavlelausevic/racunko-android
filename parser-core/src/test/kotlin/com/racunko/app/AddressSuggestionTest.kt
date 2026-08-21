package com.racunko.app

import com.racunko.app.parser.AddressMatcher
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the bill PRINTS, when the address book does not know it.
 *
 * The app still never guesses (D4) — this only lets it SAY what it read, because
 * silence was its own defect: a bill whose street was simply missing from the
 * book was indistinguishable from a broken parser.
 */
class AddressSuggestionTest {

    // Every identifier below is INVENTED. Only the SHAPE of a meter id or a space
    // code matters to these assertions — they check the street that comes back —
    // so nothing here needs to be, or is, a real account.

    private val infostan =
        "Шифра простора: 0614276 Адреса: СВЕТОЗАРА ГЛИШИЋА 26 ГА. 356 " +
            "Општина: ПАЛИЛУЛА Насеље: БГ*ПАЛИЛУЛА"

    private val eps =
        "ед број: 5001112223 / 111000111 шифра мерног места:201000100000 " +
            "адреса мерног места:косте драгојевића 7 11120 београд - палилула " +
            "бр.бројила/начин очитавања:01000111"

    @Test
    fun `infostan hint is the property address, not the labels around it`() {
        assertEquals("svetozara glisica 26 ga. 356", AddressMatcher.suggestion(null, infostan, "infostan"))
    }

    @Test
    fun `eps hint is the street, never the meter-point code`() {
        val hint = AddressMatcher.suggestion(null, eps, "eps")
        assertEquals("koste dragojevica 7", hint)
    }

    @Test
    fun `a provider without a property anchor offers nothing`() {
        assertEquals("", AddressMatcher.suggestion(null, infostan, "mts"))
        assertEquals("", AddressMatcher.suggestion(null, "", "infostan"))
    }
}
