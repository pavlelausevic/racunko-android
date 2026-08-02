package com.racunko.app

import com.racunko.app.parser.BillName
import com.racunko.app.parser.MonthYear
import com.racunko.app.parser.SpaceBinding
import com.racunko.app.parser.SpaceId
import com.racunko.app.parser.SpaceNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5.2 Change B — multiple spaces at one address. The IDENT (spaceId) is
 * unique per space while the recipient account is shared, so a sub-label bound
 * to the IDENT once auto-applies every month; a filename collision is flagged
 * for a manual tag instead of a silent `_2`.
 */
class SpaceNamingTest {

    // Real-layout InfoStan ROs: 118 + IDENT (8, zero-padded) + partija + -YYMM0-D
    private val roFlat = mapOf("RO" to "11800598102087-26050-1")
    private val roGarage = mapOf("RO" to "11800614276087-26050-1")

    private val bindings = listOf(
        SpaceBinding(spaceId = "598102", addressLabel = "SG26", subLabel = "STAN"),
        SpaceBinding(spaceId = "0614276", addressLabel = "SG26", subLabel = "g1")
    )

    private fun name(sub: String?, amount: Long): String =
        BillName.build("infostan", SpaceNaming.addressToken("SG26", sub), MonthYear(6, 26), amount)

    /** 1. Two bills, same address+month, different IDENT, both bound → distinct names, no prompt. */
    @Test
    fun boundSubLabels_produceDistinctNames() {
        val flatId = SpaceId.detect("infostan", roFlat, null)
        val garageId = SpaceId.detect("infostan", roGarage, null)
        assertEquals("598102", flatId)
        assertEquals("614276", garageId)

        val flatName = name(SpaceNaming.subFor(flatId, "SG26", bindings), 3963)
        val garageName = name(SpaceNaming.subFor(garageId, "SG26", bindings), 3963)
        assertEquals("infostan_SG26-STAN_jun26_3963", flatName)
        assertEquals("infostan_SG26-G1_jun26_3963", garageName)
        // identical amounts, still no collision between the two
        assertFalse(SpaceNaming.collides(garageName, ".pdf", setOf("$flatName.pdf"), null))
    }

    /** 2. No sub-label bound + identical amount → collision FLAGGED, not silent _2. */
    @Test
    fun unboundIdenticalName_flagsCollision() {
        val existing = setOf("infostan_SG26_jun26_970.pdf")
        val base = name(sub = null, amount = 970)
        assertEquals("infostan_SG26_jun26_970", base)
        assertTrue(SpaceNaming.collides(base, ".pdf", existing, currentName = "novi_racun.pdf"))
        // reprocessing the SAME file is not a collision
        assertFalse(SpaceNaming.collides(base, ".pdf", existing, currentName = "infostan_SG26_jun26_970.pdf"))
    }

    /** 3. IDENT-less provider → no auto path, manual tag still yields a distinct usable name. */
    @Test
    fun identlessProvider_manualTagWorks() {
        assertNull(SpaceId.detect("sz", null, "obican racun bez identa, za uplatu 1.500,00"))
        assertNull(SpaceNaming.subFor(null, "DS99", bindings))
        val tagged = BillName.build("sz", SpaceNaming.addressToken("DS99", "lokal"), MonthYear(6, 26), 1500)
        assertEquals("sz_DS99-LOKAL_jun26_1500", tagged)
        assertFalse(SpaceNaming.collides(tagged, ".pdf", setOf("sz_DS99_jun26_1500.pdf"), null))
    }

    /** 4. „Zapamti za ovaj prostor": a manual tag bound to the IDENT auto-applies next month. */
    @Test
    fun rememberedTag_autoAppliesNextMonth() {
        // the user tagged the garage in June and chose to remember it
        val learned = bindings + SpaceBinding(spaceId = "614276", addressLabel = "SG26", subLabel = "G2")
        // careful: the June binding above ("0614276" → g1) matches first — order is bind-time reality;
        // next month's bill of the SAME IDENT resolves to the FIRST binding for it
        val julyRo = mapOf("RO" to "11800614276087-26070-1")
        val id = SpaceId.detect("infostan", julyRo, null)
        val sub = SpaceNaming.subFor(id, "SG26", learned)
        assertEquals("G1", sub) // sanitized (g1 → G1), applied with no prompt
        assertEquals(
            "infostan_SG26-G1_jul26_1200",
            BillName.build("infostan", SpaceNaming.addressToken("SG26", sub), MonthYear(7, 26), 1200)
        )
    }

    /** 5. An IDENT with no binding → plain ADDRESS token (no regression to §10 names). */
    @Test
    fun unboundIdent_keepsPlainAddress() {
        val id = SpaceId.detect("infostan", mapOf("RO" to "11800512345011-26050-1"), null)
        assertEquals("512345", id)
        assertNull(SpaceNaming.subFor(id, "KD7", bindings)) // bound entries are for SG26 spaces
        assertEquals("KD7", SpaceNaming.addressToken("KD7", null))
        assertEquals(
            "infostan_KD7_maj26_11152",
            BillName.build("infostan", "KD7", MonthYear(5, 26), 11152)
        )
        // text-label fallback agrees with the QR-derived canonical form
        assertEquals("614276", SpaceId.detect("sz", null, "Šifra korisnika (IDENT): 0614276"))
    }
}
