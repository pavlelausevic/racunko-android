package com.racunko.app

import com.racunko.app.data.LazyOnce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.3 Change 7 — storage setup is create-once by construction.
 *
 * The MediaStore backend needs no directory creation at all (RELATIVE_PATH
 * resolves implicitly and dedupes by path). The SAF backend funnels its
 * `ensureFolders` through [LazyOnce]; these tests assert the discipline that
 * fixes the duplicate-folders bug: a successful resolve NEVER runs again, so
 * processing N bills and M confirmations can never create `Racuni (2)`, …
 * (The end-to-end folder count check is a documented manual test in README.)
 */
class StorageDisciplineTest {

    @Test
    fun ensureStorage_resolvesExactlyOnce_acrossManyCalls() {
        var creations = 0
        val once = LazyOnce {
            creations++
            "folders"
        }
        // simulate a batch: N bills + M confirmations, each touching storage
        repeat(25) { assertEquals("folders", once.get()) }
        assertEquals(1, once.resolveCalls)
        assertEquals(1, creations)
    }

    @Test
    fun failedResolve_retries_butSuccessIsCachedForever() {
        var attempts = 0
        val once = LazyOnce {
            attempts++
            if (attempts < 3) null else "folders" // e.g. tree briefly inaccessible
        }
        assertNull(once.get())
        assertNull(once.get())
        assertEquals("folders", once.get())
        // subsequent calls never re-run the resolver
        repeat(10) { assertEquals("folders", once.get()) }
        assertEquals(3, once.resolveCalls)
    }

    @Test
    fun reset_allowsExactlyOneNewResolve() {
        var creations = 0
        val once = LazyOnce {
            creations++
            "folders"
        }
        once.get()
        once.reset() // e.g. the user picked a different tree (Change 4)
        repeat(5) { once.get() }
        assertEquals(2, creations)
    }
}
