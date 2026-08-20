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

/**
 * Yettel banka „Potvrda o izvrsenom nalogu za prenos" — the NBS paper transfer
 * order („Obrazac br. 3") rendered to PDF. The text layer is clean, but the form
 * is TWO-COLUMN and prints each value BEFORE the label it belongs to
 * („… 2294.15 iznos valuta"), so the anchors here read BACKWARDS from the label —
 * the opposite direction from every other template in this file.
 *
 * It shares its TITLE with [NlbTemplate], so neither may match on the title
 * alone. This one is fingerprinted by the paper-form furniture — „pecat i potpis
 * banke", „poziv na broj (odobrenje)" — which NLB's report layout does not print;
 * NLB in turn now also requires one of its own markers.
 *
 * The amount is printed with a DOT decimal separator („2294.15"), which is NOT
 * the Serbian form the shared helpers expect: `AmountParser.extractAll` does not
 * see it at all. It is therefore parsed here and folded back into `amounts`, so
 * layer-2 pairing still has a number to match on.
 */
class YettelTemplate : BankTemplate {

    companion object {
        val RX_TITLE = Regex("potvrda o izvrsenom nalogu za prenos")

        /** Paper-form furniture — this is what separates it from the NLB report. */
        val RX_FORM = Regex("pecat i potpis banke|poziv na broj [(]odobrenje[)]|obrazac br")

        /**
         * Value BEFORE its label: „… 2294.15 iznos".
         *
         * NO trailing word boundary after „iznos" — PDFBox glues this form's
         * labels together and emits „iznosvaluta" as one run (device 20.08.2026),
         * so `iznos\\b` matched nothing while the amount sat right in front of it.
         */
        val RX_AMOUNT = Regex("([0-9][0-9.,]*)\\s+iznos")

        /** „… 190000000009987010 racun primaoca" — the label FOLLOWS its value. */
        val RX_RECIPIENT = Regex("([0-9]{18})[^0-9]{0,20}racun primaoca")

        /** „racun platioca … 115000000000012345" — this one follows its label. */
        val RX_PAYER = Regex("racun platioca[^0-9]{0,60}([0-9]{18})")

        /** Bounded so it cannot swallow the model or the account that follow it. */
        val RX_REF = Regex("poziv na broj [(]odobrenje[)][^0-9]{0,25}([0-9]{6,20})")

        /** „2.294,15" (Serbian) or „2294.15" (this form) — both mean the same money. */
        fun amountOf(token: String): Long? {
            Regex("^([0-9.]+),([0-9]{2})$").find(token)?.let {
                return ConfirmationCommon.roundAmount(it.groupValues[1], it.groupValues[2])
            }
            Regex("^([0-9]+)[.]([0-9]{2})$").find(token)?.let {
                return ConfirmationCommon.roundAmount(it.groupValues[1], it.groupValues[2])
            }
            return null
        }
    }

    override val name = "yettel"

    override fun matches(normText: String): Boolean =
        RX_TITLE.containsMatchIn(normText) && RX_FORM.containsMatchIn(normText)

    override fun extract(rawText: String, normText: String, ips: Map<String, String>?): ConfirmationFields {
        val payer = RX_PAYER.find(normText)?.groupValues?.get(1) ?: ""
        val recipient = RX_RECIPIENT.find(normText)?.groupValues?.get(1)

        val refs = mutableListOf<String>()
        RX_REF.find(normText)?.groupValues?.get(1)?.let { raw ->
            // PDFBox runs this form's fields together, so the two-digit model
            // arrives welded to the END of the reference („…2607" + „97" ->
            // „…260797", device 20.08.2026). Offer both readings and let pairing
            // pick — the same shape as the LEADING model Erste glues on.
            refs.add(raw)
            if (raw.length > 12) refs.add(raw.dropLast(2))
        }
        for (c in ConfirmationCommon.referenceCandidates(rawText, normText, ips)) {
            if (c !in refs) refs.add(c)
        }
        val cleanRefs = refs.filterNot { payer.isNotEmpty() && (it.contains(payer) || payer.contains(it)) }

        val templated = RX_AMOUNT.find(normText)?.let { amountOf(it.groupValues[1]) }
        // extractAll cannot see a dot decimal, so the templated amount is folded
        // in — otherwise layer 2 would have an EMPTY amount set to match against.
        val amounts = AmountParser.extractAll(rawText) + setOfNotNull(templated)

        val accounts = when {
            recipient != null -> setOf(recipient)
            payer.isNotEmpty() -> Accounts.extractAll(rawText) - payer
            else -> Accounts.extractAll(rawText)
        }

        return ConfirmationFields(
            referenceCandidates = cleanRefs,
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
 * NLB „Potvrda o izvrsenom nalogu za prenos" — a Microsoft Reporting Services
 * PDF whose text layer is HALF unreadable: labels drawn in the subsetted
 * `ABCDEE+Arial` (Identity-H, NO `/ToUnicode`) come out shifted by a constant —
 * „Racun platioca" arrives as `5D..XQSODWLRFD` — while everything set in
 * Helvetica/WinAnsi reads correctly. Every anchor here is therefore one of the
 * READABLE labels and never one of the shifted ones: the shifted form is an
 * artifact of one renderer's font subset, and it would change the moment the
 * bank re-generates the report or a different extractor reads it.
 *
 * Two traps this layout sets:
 * 1. it prints „Provizija 15,00 RSD" next to „Iznos 26,77 RSD", so the amount
 *    MUST be anchored on its own label — taking the first `…,.. RSD` in the text
 *    would book the FEE as the payment;
 * 2. the payer's own account sits under an UNREADABLE label, so it cannot be
 *    found and excluded the way Intesa's and Erste's are. The RECIPIENT is
 *    identified positively instead — it is the account fenced between the
 *    readable „Iznos … RSD" and the readable „Model i poziv na broj odobrenja" —
 *    and everything else is treated as payer-side. If a future layout breaks
 *    that order the template degrades to „every account" rather than to „no
 *    account": a missing recipient silently stops pairing, which is worse than
 *    the narrow risk the excluded-payer rule exists to prevent.
 *
 * The document carries NO bank name in its text layer (the logo is an image and
 * the metadata says only „Microsoft Reporting Services"), so detection rests on
 * the title. Another bank shipping this same report layout would be claimed by
 * this template — which is acceptable, because everything it then does is
 * correct for that layout regardless of whose logo is on top.
 */
class NlbTemplate : BankTemplate {

    companion object {
        /** No bank name anywhere in the text layer, and [YettelTemplate] prints the
         * SAME title — so the title alone is not a fingerprint; a marker is needed. */
        val RX_TITLE = Regex("potvrda o izvrsenom nalogu za prenos")

        /** NLB report furniture that the NBS paper form does not carry. */
        val RX_MARKER = Regex("provizija|id transakcije|autorizovao")

        /** THE transaction amount, anchored on „Iznos" so „Provizija" can never win. */
        val RX_AMOUNT = Regex("\\biznos\\b[^0-9]{0,40}([\\d.]+),(\\d{2})\\s*rsd")

        /** Recipient account, fenced by the two readable labels that surround it. */
        val RX_RECIPIENT = Regex(
            "\\biznos\\b.{0,60}?rsd.{0,80}?\\b(\\d{3})-(\\d{1,13})-(\\d{2})\\b" +
                ".{0,80}?model i poziv na broj odobrenja"
        )
    }

    override val name = "nlb"

    override fun matches(normText: String): Boolean =
        RX_TITLE.containsMatchIn(normText) && RX_MARKER.containsMatchIn(normText)

    override fun extract(rawText: String, normText: String, ips: Map<String, String>?): ConfirmationFields {
        val recipient = RX_RECIPIENT.find(normText)?.let {
            Accounts.normalize(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        val all = Accounts.extractAll(rawText)
        // Everything that is not the recipient is payer-side and must never
        // become a pairing key — same invariant as Intesa/Erste, reached from
        // the other end because the payer's label is the unreadable one.
        val payerSide = if (recipient != null) all - recipient else emptySet()

        val refs = ConfirmationCommon.referenceCandidates(rawText, normText, ips)
            .filterNot { c -> payerSide.any { p -> c.contains(p) || p.contains(c) } }

        val amounts = AmountParser.extractAll(rawText)
        val templated = RX_AMOUNT.find(normText)?.let {
            ConfirmationCommon.roundAmount(it.groupValues[1], it.groupValues[2])
        }

        return ConfirmationFields(
            referenceCandidates = refs,
            accounts = if (recipient != null) setOf(recipient) else all,
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
        listOf(
            IntesaTemplate(), ErsteTemplate(), AikTemplate(),
            YettelTemplate(), NlbTemplate(), GenericTemplate()
        )

    fun parse(rawText: String, ips: Map<String, String>?): ConfirmationFields {
        val normText = Normalizer.norm(rawText)
        val template = templates.first { it.matches(normText) }
        return template.extract(rawText, normText, ips)
    }
}
