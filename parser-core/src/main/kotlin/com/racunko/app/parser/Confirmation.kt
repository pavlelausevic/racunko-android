package com.racunko.app.parser

/**
 * Fields extracted from a payment confirmation, ready for layered pairing (§5.9).
 * Bank layouts differ, so field extraction is isolated per-bank in [BankTemplate]s.
 */
data class ConfirmationFields(
    /** Digits-only payment-reference candidates, highest priority first. */
    val referenceCandidates: List<String>,
    /** 18-digit-normalized recipient-side account candidates (payer account excluded). */
    val accounts: Set<String>,
    /** All rounded amounts found in the text. */
    val amounts: Set<Long>,
    /** Best single amount for display, if any. */
    val amount: Long?,
    /** Provider guess from recipient text. */
    val provider: String,
    /**
     * THE transaction amount when a bank template positively identified it
     * (v1.2 Change 3). When set, layer 2 matches on this single amount only —
     * never on balances/fees also present in the document.
     */
    val templatedAmount: Long? = null,
    /** Which template produced these fields (for tests/diagnostics). */
    val templateName: String = "generic"
)

interface BankTemplate {
    val name: String
    fun matches(normText: String): Boolean
    fun extract(rawText: String, normText: String, ips: Map<String, String>?): ConfirmationFields
}

/** Shared candidate collection used by the templates that do have a payment reference. */
internal object ConfirmationCommon {

    private val RX_SEQ = Regex("\\d[\\d\\-/ ]{5,}\\d")

    /**
     * Priority reference, both banks (v1.1 Change 2):
     * Intesa "MODEL I POZIV NA BROJ ODOBRENJA - 040255500012"
     * Erste  "Model - poziv na broj 00 19-02005555555-2605"
     * (Intesa's debit-side "MODEL I POZIV NA BROJ ZADUŽENJA" is not matched —
     * the " i " between model and poziv breaks the first alternative.)
     */
    val RX_PRIORITY_REF =
        Regex("(?:model\\s*[-–]?\\s*poziv na broj|poziv na broj odobrenja)[^0-9]{0,25}(\\d[\\d\\-/ ]{4,}\\d)")

    fun referenceCandidates(rawText: String, normText: String, ips: Map<String, String>?): MutableList<String> {
        val out = mutableListOf<String>()
        fun push(d: String) {
            if (d.length >= 8 && d !in out) out.add(d)
        }
        // priority 1: explicit credit reference; Erste glues the 2-digit model in
        // front of it, so the same digits with the model stripped are added too
        for (m in RX_PRIORITY_REF.findAll(normText)) {
            val digits = m.groupValues[1].filter { it.isDigit() }
            push(digits)
            if (digits.length > 10) push(digits.substring(2))
        }
        // priority 2: QR RO if the confirmation itself carries an IPS QR
        val ro = IpsQr.roDigits(ips)
        if (ro.isNotEmpty()) push(ro)
        // priority 3: every digit sequence of length >= 8 (dashes/slashes/spaces inside)
        for (m in RX_SEQ.findAll(rawText)) push(m.value.filter { it.isDigit() })
        return out
    }

    fun roundAmount(whole: String, decimals: String): Long? =
        whole.replace(".", "").toLongOrNull()?.let { AmountParser.roundHalfUp(it, decimals.toInt()) }
}

/**
 * Banca Intesa (Intesa Mobi) "Potvrda transakcije" — two-column layout, so text
 * extraction interleaves labels and values. Named regex constants per §5.9.
 */
class IntesaTemplate : BankTemplate {

    companion object {
        val RX_TITLE = Regex("potvrda transakcije")

        /**
         * IZNOS TRANSAKCIJE prints as "1.070,00 RSD"; because of label/value
         * interleaving it must be found via amount-followed-by-RSD, NOT label proximity.
         */
        val RX_AMOUNT_RSD = Regex("([\\d.]+),(\\d{2})\\s*rsd")

        /** Payer's own account — must NEVER be used as a layer-2 pairing key. */
        val RX_PAYER_ACCOUNT = Regex("broj racuna platioca[^0-9]{0,60}(\\d{18})")
    }

    override val name = "intesa"

    override fun matches(normText: String): Boolean = RX_TITLE.containsMatchIn(normText)

    override fun extract(rawText: String, normText: String, ips: Map<String, String>?): ConfirmationFields {
        val payer = RX_PAYER_ACCOUNT.find(normText)?.groupValues?.get(1) ?: ""

        val refs = ConfirmationCommon.referenceCandidates(rawText, normText, ips)
            .filterNot { payer.isNotEmpty() && (it.contains(payer) || payer.contains(it)) }

        val accounts = Accounts.extractAll(rawText).filterNot { it == payer }.toSet()
        val amounts = AmountParser.extractAll(rawText)
        val templated = RX_AMOUNT_RSD.find(normText)?.let {
            ConfirmationCommon.roundAmount(it.groupValues[1], it.groupValues[2])
        }

        return ConfirmationFields(
            referenceCandidates = refs,
            accounts = accounts,
            amounts = amounts,
            amount = templated ?: amounts.firstOrNull(),
            provider = ProviderDetector.detect(ips, rawText),
            templatedAmount = templated,
            templateName = name
        )
    }
}

/**
 * Erste Bank mBanking confirmation (v1.1 Change 2) — arrives as a JPEG wrapped
 * in a PDF with no text layer, so the text here is OCR output with mangled
 * diacritics ("Raéun platioca"); regexes are written against stable substrings.
 */
class ErsteTemplate : BankTemplate {

    companion object {
        val RX_DETECT_LABELS = Regex("model\\s*[-–]?\\s*poziv na broj")

        /** "Raéun platioca 340000987654321097" — OCR may mangle the č. */
        val RX_PAYER_ACCOUNT = Regex("ra.{0,2}un platioca[^0-9]{0,30}(\\d{18})")

        /** Signed amount: Erste prints outflows negative — the sign is stripped. */
        val RX_AMOUNT_SIGNED = Regex("[-–]?\\s*([\\d.]+),(\\d{2})\\s*rsd")
    }

    override val name = "erste"

    override fun matches(normText: String): Boolean =
        normText.contains("erste") ||
            (normText.contains("ime platioca") && RX_DETECT_LABELS.containsMatchIn(normText))

    override fun extract(rawText: String, normText: String, ips: Map<String, String>?): ConfirmationFields {
        val payer = RX_PAYER_ACCOUNT.find(normText)?.groupValues?.get(1) ?: ""

        val refs = ConfirmationCommon.referenceCandidates(rawText, normText, ips)
            .filterNot { payer.isNotEmpty() && (it.contains(payer) || payer.contains(it)) }

        val accounts = Accounts.extractAll(rawText).filterNot { it == payer }.toSet()
        val amounts = AmountParser.extractAll(rawText)
        val templated = RX_AMOUNT_SIGNED.find(normText)?.let {
            ConfirmationCommon.roundAmount(it.groupValues[1], it.groupValues[2])
        }

        return ConfirmationFields(
            referenceCandidates = refs,
            accounts = accounts,
            amounts = amounts,
            amount = templated ?: amounts.firstOrNull(),
            provider = ProviderDetector.detect(ips, rawText),
            templatedAmount = templated,
            templateName = name
        )
    }
}

/**
 * AIK Banka "Detalji transakcije" screenshot (v1.2 Change 2). No payment
 * reference exists on this document type → layer 1 is skipped silently; the
 * bank-internal "Referenca" (16 digits) must never become a pairing candidate,
 * and the "Stanje nakon promene" balance must never be taken as the amount.
 */
class AikTemplate : BankTemplate {

    companion object {
        /** Recipient account glued inside svrha plaćanja: "…prenos na Rn: 2002206…". */
        val RX_RN_ACCOUNT = Regex("rn:?\\s*(\\d{18})")

        /** THE transaction amount (signed): "Uplata/Isplata -970,52 RSD". */
        val RX_AMOUNT = Regex("uplata/isplata\\s*[-–]?\\s*([\\d.]+),(\\d{2})\\s*rsd")
    }

    override val name = "aik"

    override fun matches(normText: String): Boolean =
        normText.contains("detalji transakcije") &&
            (normText.contains("svrha placanja") || normText.contains("stanje nakon promene"))

    override fun extract(rawText: String, normText: String, ips: Map<String, String>?): ConfirmationFields {
        val accounts = LinkedHashSet<String>()
        for (m in RX_RN_ACCOUNT.findAll(normText)) accounts.add(m.groupValues[1])
        accounts.addAll(Accounts.extractAll(rawText))

        val amounts = AmountParser.extractAll(rawText)
        val templated = RX_AMOUNT.find(normText)?.let {
            ConfirmationCommon.roundAmount(it.groupValues[1], it.groupValues[2])
        }

        return ConfirmationFields(
            // no payment reference on this document type → layer 1 skipped
            referenceCandidates = emptyList(),
            accounts = accounts,
            amounts = amounts,
            amount = templated ?: amounts.firstOrNull(),
            provider = ProviderDetector.detect(ips, rawText),
            templatedAmount = templated,
            templateName = name
        )
    }
}

/** Generic fallback template for banks without a dedicated class. */
class GenericTemplate : BankTemplate {

    override val name = "generic"

    override fun matches(normText: String): Boolean = true

    override fun extract(rawText: String, normText: String, ips: Map<String, String>?): ConfirmationFields {
        val amounts = AmountParser.extractAll(rawText)
        return ConfirmationFields(
            referenceCandidates = ConfirmationCommon.referenceCandidates(rawText, normText, ips),
            accounts = Accounts.extractAll(rawText),
            amounts = amounts,
            amount = AmountParser.parse(ips, rawText) ?: amounts.firstOrNull(),
            provider = ProviderDetector.detect(ips, rawText),
            templateName = name
        )
    }
}

object ConfirmationParser {

    private val templates: List<BankTemplate> =
        listOf(IntesaTemplate(), ErsteTemplate(), AikTemplate(), GenericTemplate())

    fun parse(rawText: String, ips: Map<String, String>?): ConfirmationFields {
        val normText = Normalizer.norm(rawText)
        val template = templates.first { it.matches(normText) }
        return template.extract(rawText, normText, ips)
    }
}
