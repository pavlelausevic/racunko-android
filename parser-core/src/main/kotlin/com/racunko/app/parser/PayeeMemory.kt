package com.racunko.app.parser

/**
 * Payee memory prefill logic (Change 6) — pure JVM half; the Room table lives
 * in the app. Subscription bills repeat monthly with the same payee (only the
 * amount and month change), so once a checksum-valid recipient account is known
 * we can prefill provider + address from month 2 onward and skip most OCR.
 */
data class PayeeProfile(
    /** Checksum-valid 18-digit recipient account — the key. */
    val account: String,
    val provider: String,
    val addressLabel: String,
    val displayName: String
)

/** Result of a prefill: values plus which of them were *suggested* from memory. */
data class PrefillResult(
    val provider: String,
    val addressLabel: String,
    val providerSuggested: Boolean,
    val addressSuggested: Boolean
)

object PayeeMemory {

    /**
     * Fills the missing PROVIDER from a known payee. Values the current document
     * clearly provides are NEVER overridden — memory only fills gaps, and a
     * filled value is flagged "suggested" (predloženo) for the UI. Lookup only
     * happens for a **checksum-valid** account.
     *
     * **It deliberately does NOT fill the address any more (v1.7.1).** The key
     * here is the RECIPIENT account, and that identifies the ISSUER, not the
     * place the bill is for: InfoStan's is institutional and shared by every
     * customer and every flat, EPS's likewise, and a telecom's is the operator's
     * own. Keyed that way, memory can only ever return "whichever address was
     * seen last", which merely looks correct while a person has one property per
     * issuer.
     *
     * Device 20.08.2026, and it is worth keeping the shape of it: with the SG26
     * label deleted from the book, a bill for a COMPLETELY DIFFERENT address was
     * filed under SG26 — because the matcher found nothing and memory filled the
     * blank from the shared account. This is the same wrong-address failure the
     * cmap regression produced, arriving through a different door; the guard
     * added then (`textIsReadable`) only refuses when the document is
     * UNREADABLE, and this document read perfectly.
     *
     * The address half of this prefill could only ever run when the matcher had
     * already failed — which is exactly the moment a guess is unsafe. So it is
     * gone, and the card now says „adresa?" plus what the bill prints. Provider
     * stays: that IS a property of the account, and cannot be wrong.
     *
     * Two sharper keys were considered and neither justifies guessing in the
     * meantime: account + InfoStan IDENT is issuer-specific and `SpaceId.detect`
     * loses the IDENT without the `118` prefix (fixture
     * `ro_without_model_prefix`), and learning which accounts are
     * non-discriminating only helps AFTER the first conflict — too late for a
     * clean install, which is exactly the case above.
     */
    fun prefill(
        account: String?,
        currentProvider: String,
        currentAddress: String,
        lookup: (String) -> PayeeProfile?
    ): PrefillResult {
        val profile = account
            ?.takeIf { AccountChecksum.isValid(it) }
            ?.let(lookup)
            ?: return PrefillResult(currentProvider, currentAddress, false, false)

        val fillProvider = currentProvider.isBlank() && profile.provider.isNotBlank()
        return PrefillResult(
            provider = if (fillProvider) profile.provider else currentProvider,
            // the document's address, or nothing — never the remembered one
            addressLabel = currentAddress,
            providerSuggested = fillProvider,
            addressSuggested = false
        )
    }

    /** A payee is only remembered when its account is checksum-valid. */
    fun rememberable(account: String?): Boolean = AccountChecksum.isValid(account)
}
