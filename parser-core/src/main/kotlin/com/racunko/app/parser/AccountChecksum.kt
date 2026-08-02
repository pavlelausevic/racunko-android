package com.racunko.app.parser

import java.math.BigInteger

/**
 * Serbian domestic bank account checksum (Change 5).
 *
 * An account is 18 digits = 3-digit bank + 13-digit body + 2-digit control.
 * The control digits are computed with ISO 7064 MOD 97-10 (the IBAN-style
 * scheme): control = 98 − ((bank·body · 100) mod 97), which makes the whole
 * 18-digit number satisfy `n mod 97 == 1`. That single identity is the check.
 *
 * The algorithm is **pinned by real valid accounts as test vectors**
 * (`AccountChecksumTest`); a single flipped digit always fails (97 is prime).
 *
 * A number that fails this check is `verified = false` and MUST NOT be used as
 * a layer-2 pairing key or to generate an IPS QR — the UI asks the user to
 * confirm it instead of silently trusting an OCR digit.
 */
object AccountChecksum {

    private val N97 = BigInteger.valueOf(97)
    private val ONE = BigInteger.ONE

    /** True iff [account] is exactly 18 digits and passes MOD 97-10. */
    fun isValid(account: String?): Boolean {
        if (account == null) return false
        if (account.length != 18 || !account.all { it.isDigit() }) return false
        return BigInteger(account).mod(N97) == ONE
    }

    /**
     * Builds the 2-digit control for a bank (3) + body (≤13) pair, zero-padding
     * the body to 13 digits first (the IPS `R` form). Used by `mkaccount` and by
     * fixtures that need a synthetic-but-valid account.
     */
    fun controlDigits(bank: String, body: String): String {
        val base = bank + body.padStart(13, '0')
        val r = BigInteger(base + "00").mod(N97).toInt()
        val control = 98 - r
        return control.toString().padStart(2, '0')
    }

    /** Convenience: full 18-digit account from bank + body. */
    fun build(bank: String, body: String): String =
        bank + body.padStart(13, '0') + controlDigits(bank, body)
}
