package com.racunko.app.parser.registry

import com.racunko.app.parser.AccountChecksum
import com.racunko.app.parser.BankTemplate
import com.racunko.app.parser.ConfirmationFields

/**
 * Re-registers an existing v1.2 confirmation [BankTemplate] (Intesa/Erste/AIK)
 * through the new registry WITHOUT touching its logic. The wrapped template's
 * `matches`/`extract` run byte-identically; this only maps the resulting
 * [ConfirmationFields] onto [ExtractedFields], applying the checksum gate to
 * the chosen recipient account (Change 5).
 */
class ConfirmationTemplateAdapter(private val bank: BankTemplate) : DocumentTemplate {

    override val id: String = bank.name

    override val docType: DocType = DocType.CONFIRMATION

    override fun matches(doc: NormalizedDoc): Boolean = bank.matches(doc.normText)

    override fun extract(doc: NormalizedDoc): ExtractedFields =
        mapFields(bank.extract(doc.rawText, doc.normText, doc.ipsQr))
}

/** The generic fallback — always the last registry entry; matches everything. */
class GenericFallbackTemplate(private val generic: BankTemplate) : DocumentTemplate {

    override val id: String = generic.name

    override fun matches(doc: NormalizedDoc): Boolean = true

    override fun extract(doc: NormalizedDoc): ExtractedFields =
        mapFields(generic.extract(doc.rawText, doc.normText, doc.ipsQr))
}

/**
 * Picks the recipient account (first checksum-valid one, else the first seen),
 * applies the templated-amount rule, and carries the credit-side reference.
 */
internal fun mapFields(f: ConfirmationFields): ExtractedFields {
    val account = f.accounts.firstOrNull { AccountChecksum.isValid(it) } ?: f.accounts.firstOrNull()
    return ExtractedFields(
        provider = f.provider,
        month = null, // confirmations don't carry the billing month
        amount = f.templatedAmount ?: f.amount,
        recipientAccount = account,
        accountVerified = AccountChecksum.isValid(account),
        paymentReference = f.referenceCandidates.firstOrNull()
    )
}
