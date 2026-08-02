package com.racunko.app.parser

/**
 * NBS IPS QR payload generation (Change 5b) — the pure-JVM counterpart to
 * reading it with [IpsQr]. Turns a QR-less bill into a scannable payment.
 *
 * Field order and character set follow the NBS IPS spec: identification `K:PR`,
 * version `V:01`, charset `C:1` (UTF-8), recipient account `R` (18 digits),
 * name `N`, amount `I` (`RSD` + integer + `,dd`), purpose code `SF`, purpose
 * `S`, and model+reference `RO`. Payer `P` is included only when known.
 *
 * Generation is **gated** on a checksum-valid account and a present amount
 * (enforced by the caller / [canGenerate]); the app renders this string to a
 * bitmap through the flavor's QrEncoder. A round-trip (build → [IpsQr.parse])
 * must return identical fields — see `IpsQrRoundTripTest`.
 */
object IpsQrPayload {

    /** Bills may only get a generated QR when the account is proven and an amount exists. */
    fun canGenerate(recipientAccount: String?, amount: Long?): Boolean =
        amount != null && AccountChecksum.isValid(recipientAccount)

    fun build(
        recipientAccount: String,
        recipientName: String,
        amount: Long,
        purposeCode: String = "189",
        purpose: String = "Uplata po racunu",
        paymentReference: String? = null,
        payer: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("K:PR|V:01|C:1")
        sb.append("|R:").append(recipientAccount)
        sb.append("|N:").append(sanitize(recipientName))
        sb.append("|I:RSD").append(amount).append(",00")
        sb.append("|SF:").append(purposeCode)
        sb.append("|S:").append(sanitize(purpose))
        if (!payer.isNullOrBlank()) sb.append("|P:").append(sanitize(payer))
        if (!paymentReference.isNullOrBlank()) sb.append("|RO:").append(paymentReference)
        return sb.toString()
    }

    /** Values must not contain the `|` delimiter or CR/LF that would break parsing. */
    private fun sanitize(v: String): String =
        v.replace('|', ' ').replace('\r', ' ').replace('\n', ' ').trim()

    /**
     * Decodes a payload back into the fields a round-trip test compares:
     * provider, account, amount, RO and name. Uses the same readers the app
     * uses for scanned QR codes, so build/decode symmetry is real, not asserted
     * against a private parser.
     */
    data class DecodedFields(
        val provider: String,
        val recipientAccount: String,
        val amount: Long?,
        val paymentReference: String,
        val recipientName: String
    )

    fun decodeFields(payload: String): DecodedFields {
        val ips = IpsQr.parse(payload)
        return DecodedFields(
            provider = ProviderDetector.detect(ips, null),
            recipientAccount = IpsQr.recipientAccountDigits(ips),
            amount = AmountParser.parse(ips, null),
            paymentReference = IpsQr.roDigits(ips),
            recipientName = ips["N"] ?: ""
        )
    }
}
