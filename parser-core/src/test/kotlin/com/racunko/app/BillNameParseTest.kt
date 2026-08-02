package com.racunko.app

import com.racunko.app.parser.BillName
import com.racunko.app.parser.MonthYear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.4.3 Change 1 — recover fields from a processed file name for backfill. */
class BillNameParseTest {

    @Test
    fun parsesBillName() {
        val p = BillName.parse("infostan_BDS95_jun26_6955.pdf")!!
        assertFalse(p.confirmation)
        assertEquals("infostan", p.provider)
        assertEquals("BDS95", p.address)
        assertEquals(MonthYear(6, 26), p.month)
        assertEquals(6955L, p.amount)
    }

    @Test
    fun parsesConfirmationAndCollisionSuffix() {
        val p = BillName.parse("uplata_mts_KD7_maj26_3282_2.pdf")!!
        assertTrue(p.confirmation)
        assertEquals("mts", p.provider)
        assertEquals(3282L, p.amount)
    }

    @Test
    fun rejectsNonProcessedNames() {
        assertNull(BillName.parse("Racun_598102.pdf"))
        assertNull(BillName.parse("random.pdf"))
    }
}
