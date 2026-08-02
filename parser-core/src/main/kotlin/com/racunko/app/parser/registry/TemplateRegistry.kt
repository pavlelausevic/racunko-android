package com.racunko.app.parser.registry

import com.racunko.app.parser.AikTemplate
import com.racunko.app.parser.ErsteTemplate
import com.racunko.app.parser.GenericTemplate
import com.racunko.app.parser.IntesaTemplate

/**
 * Ordered template list (Change 1): the FIRST template whose [DocumentTemplate.matches]
 * returns true handles the document; a generic fallback is always last, so a
 * document that matches a specific template never falls through to it.
 *
 * The existing bank confirmation templates (Intesa, Erste, AIK) are re-registered
 * here through thin adapters WITHOUT changing their logic — behavior stays
 * byte-identical, proven by the unchanged acceptance tests. `UplatnicaTemplate`
 * (paper bills) and any community template are ordinary entries.
 */
class TemplateRegistry(templates: List<DocumentTemplate>) {

    private val templates: List<DocumentTemplate> = templates.toList()

    val ids: List<String> get() = templates.map { it.id }

    /** First matching template; falls back to the last entry (generic) if none match. */
    fun firstMatching(doc: NormalizedDoc): DocumentTemplate =
        templates.firstOrNull { it.matches(doc) } ?: templates.last()

    fun extract(doc: NormalizedDoc): ExtractedFields = firstMatching(doc).extract(doc)

    /**
     * v1.4.4 Change 1: does this document look like a bill/confirmation? True iff
     * it carries a decodable IPS QR OR matches a SPECIFIC template (bank/uplatnica)
     * — never the generic fallback. Filtering is by CONTENT, not filename, so a
     * random contract/e-book in Downloads never qualifies.
     */
    fun looksLikeBill(doc: NormalizedDoc): Boolean =
        doc.ipsQr != null || firstMatching(doc) !is GenericFallbackTemplate

    /**
     * v1.5.2 Change A2: BILL if a bill/uplatnica template matches or an IPS QR
     * is present; CONFIRMATION if a bank-confirmation template matches; UNKNOWN
     * otherwise. A matching SPECIFIC template outranks the QR check (a bank
     * confirmation that embeds an IPS QR is still a confirmation). QR absence
     * is never used to infer CONFIRMATION.
     */
    fun classifyDocType(doc: NormalizedDoc): DocTypeGuess {
        val specific = templates.firstOrNull { it !is GenericFallbackTemplate && it.matches(doc) }
        return when {
            specific != null -> DocTypeGuess(specific.docType, GuessConfidence.HIGH)
            doc.ipsQr != null -> DocTypeGuess(DocType.BILL, GuessConfidence.HIGH)
            else -> DocTypeGuess(DocType.UNKNOWN, GuessConfidence.LOW, lean = leanOf(doc.normText))
        }
    }

    /** A weak keyword lean for the UNKNOWN dialog's pre-highlight — never routes on its own. */
    private fun leanOf(normText: String): DocType? = when {
        normText.isBlank() -> null
        "potvrda" in normText || "uplata/isplata" in normText || "nalog" in normText ->
            DocType.CONFIRMATION
        "racun" in normText || "za uplatu" in normText || "iznos" in normText -> DocType.BILL
        else -> null
    }

    fun withoutSpecific(): TemplateRegistry =
        TemplateRegistry(templates.filter { it is GenericFallbackTemplate })

    companion object {
        /**
         * The shipped registry. Bank confirmation templates first (adapted from
         * the v1.2 implementations), then the paper-bill [UplatnicaTemplate],
         * then the generic fallback — ALWAYS last.
         */
        fun default(): TemplateRegistry = TemplateRegistry(
            listOf(
                ConfirmationTemplateAdapter(IntesaTemplate()),
                ConfirmationTemplateAdapter(ErsteTemplate()),
                ConfirmationTemplateAdapter(AikTemplate()),
                UplatnicaTemplate(),
                GenericFallbackTemplate(GenericTemplate())
            )
        )
    }
}
