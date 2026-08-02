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
     * Fills missing provider/address from a known payee. Values the current
     * document clearly provides are NEVER overridden — memory only fills gaps,
     * and filled values are flagged "suggested" (predloženo) for the UI.
     * Lookup only happens for a **checksum-valid** account.
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
        val fillAddress = currentAddress.isBlank() && profile.addressLabel.isNotBlank()
        return PrefillResult(
            provider = if (fillProvider) profile.provider else currentProvider,
            addressLabel = if (fillAddress) profile.addressLabel else currentAddress,
            providerSuggested = fillProvider,
            addressSuggested = fillAddress
        )
    }

    /** A payee is only remembered when its account is checksum-valid. */
    fun rememberable(account: String?): Boolean = AccountChecksum.isValid(account)
}
