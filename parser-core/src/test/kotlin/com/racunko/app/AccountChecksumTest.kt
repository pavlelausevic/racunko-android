package com.racunko.app

import com.racunko.app.parser.AccountChecksum
import com.racunko.app.parser.registry.ExtractedFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Change 5 — the checksum is pinned by real valid accounts. If MOD 97-10
 * doesn't validate all four, the implementation is wrong, not the test.
 */
class AccountChecksumTest {

    private val validAccounts = listOf(
        "190000000009987010", // EPS,      from 190-99870-10
        "200220618010100048", // InfoStan, from 200-2206180101000-48
        "170003000505000876", // Yettel,   from 170-0030005050008-76
        "200555000123456764"  // SZ,       from 200-5550001234567-64
    )

    @Test
    fun allFourRealAccountsValidate() {
        for (a in validAccounts) assertTrue("expected $a valid", AccountChecksum.isValid(a))
    }

    @Test
    fun anySingleFlippedDigitFails() {
        for (a in validAccounts) {
            for (i in a.indices) {
                val orig = a[i]
                val other = if (orig == '0') '1' else '0'
                val flipped = a.substring(0, i) + other + a.substring(i + 1)
                assertFalse("flip at $i of $a should fail", AccountChecksum.isValid(flipped))
            }
        }
    }

    @Test
    fun nonEighteenDigitOrNonNumericIsInvalid() {
        assertFalse(AccountChecksum.isValid(null))
        assertFalse(AccountChecksum.isValid(""))
        assertFalse(AccountChecksum.isValid("19000000000998701"))     // 17 digits
        assertFalse(AccountChecksum.isValid("1900000000099870100"))   // 19 digits
        assertFalse(AccountChecksum.isValid("19000000000998x010"))    // non-numeric
    }

    @Test
    fun buildProducesValidAccounts() {
        assertEquals("190000000009987010", AccountChecksum.build("190", "99870"))
        assertTrue(AccountChecksum.isValid(AccountChecksum.build("205", "123456")))
    }

    @Test
    fun unverifiedAccountIsBarredFromPairing() {
        val bad = "190000000009987011" // last valid vector with control off by one
        assertFalse(AccountChecksum.isValid(bad))
        val fields = ExtractedFields(
            provider = "eps",
            recipientAccount = bad,
            accountVerified = AccountChecksum.isValid(bad),
            amount = 7029
        )
        assertFalse(fields.accountVerified)
        assertNull("checksum-failing account must not be a pairing key", fields.pairingAccount)

        val good = ExtractedFields(
            provider = "eps",
            recipientAccount = "190000000009987010",
            accountVerified = AccountChecksum.isValid("190000000009987010"),
            amount = 7029
        )
        assertEquals("190000000009987010", good.pairingAccount)
    }
}
