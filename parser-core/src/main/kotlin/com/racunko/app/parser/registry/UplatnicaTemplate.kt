package com.racunko.app.parser.registry

import com.racunko.app.parser.AccountChecksum
import com.racunko.app.parser.Accounts
import com.racunko.app.parser.AmountParser
import com.racunko.app.parser.MonthDetector
import com.racunko.app.parser.ProviderDetector

/**
 * Paper-bill "uplatnica" template (Change 4) — for Serbian utility bills that
 * have NO IPS QR at all (typically paper-only stambena-zajednica services).
 *
 * This is NOT AI: it is the same label-anchored OCR + regex approach as the
 * bank templates, plus the checksum safety net. Detection is cheap label
 * co-occurrence; extraction anchors every value to its printed label, never to
 * a position, because OCR interleaves two-column slips.
 */
class UplatnicaTemplate : DocumentTemplate {

    override val id: String = "uplatnica"

    override fun matches(doc: NormalizedDoc): Boolean {
        val t = doc.normText
        val hasPrimalac = "primalac" in t
        val hasAccount = "racun primaoca" in t || "racun" in t
        val hasRef = "poziv na broj" in t || "model" in t
        val hasAmount = "iznos" in t || "za uplatu" in t || "svrha uplate" in t || "uplatite" in t
        return hasPrimalac && hasAccount && hasRef && hasAmount
    }

    override fun extract(doc: NormalizedDoc): ExtractedFields {
        val t = doc.normText

        // account, label-anchored, then normalized to 18 digits + checksum-gated
        val accountToken = RX_ACCOUNT.find(t)?.groupValues?.get(1)
        val account = accountToken?.let { Accounts.extractAll(it).firstOrNull() }
        val accountVerified = AccountChecksum.isValid(account)

        val reference = RX_REFERENCE.find(t)?.groupValues?.get(1)?.filter { it.isDigit() }
            ?.takeIf { it.length >= 5 }

        val amount = RX_AMOUNT.find(t)?.let {
            AmountParser.roundHalfUp(it.groupValues[1].replace(".", "").toLong(), it.groupValues[2].toInt())
        } ?: AmountParser.parse(null, t)

        val provider = ProviderDetector.detect(null, t)

        // The template does NOT resolve the address (v1.4.2 Change 4: no personal
        // address data in parser-core). Address comes from the user's own mapping
        // via payee memory, or manual chips downstream — same as the bank templates.
        return ExtractedFields(
            provider = provider,
            addressCandidates = emptyList(),
            month = MonthDetector.detect(provider, null, t), // period/issue date, else null
            amount = amount,
            recipientAccount = account,
            accountVerified = accountVerified,
            paymentReference = reference,
            spaceId = com.racunko.app.parser.SpaceId.detect(provider, null, doc.rawText)
        )
    }

    companion object {
        // anchor each regex to its label; tolerate OCR spacing and optional colon
        private val RX_ACCOUNT =
            Regex("""(?:racun primaoca|racun)\s*:?\s*(\d{3}-?\d{1,13}-?\d{2})""")
        private val RX_REFERENCE =
            Regex("""poziv na broj\s*(?:odobrenja)?\s*(\d[\d\-\s/]{4,}\d)""")
        private val RX_AMOUNT =
            Regex("""(?:za uplatu|iznos|uplatite|svrha uplate)[^\d]{0,30}([\d.]+),(\d{2})""")
    }
}
