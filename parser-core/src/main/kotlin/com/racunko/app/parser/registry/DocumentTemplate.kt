package com.racunko.app.parser.registry

/**
 * The community's main extension point (Change 1). Adding support for a new
 * bank / utility / paper slip means adding ONE `DocumentTemplate` and its
 * tests — nothing else changes. See `ADDING_A_TEMPLATE.md`.
 *
 * Contract:
 *  - [matches] must be **cheap and specific** — label co-occurrence on
 *    `doc.normText`, never a heavy parse.
 *  - [extract] uses **named regex constants anchored to labels**, never
 *    positional assumptions (OCR interleaves two-column layouts).
 *  - Never trust an account you didn't checksum (`AccountChecksum.isValid`);
 *    an unverified account sets `accountVerified = false`.
 */
interface DocumentTemplate {
    /** Stable id, also used as the filename token / diagnostics label. */
    val id: String

    /**
     * v1.5.2 Change A2: which document type this template's fingerprint proves.
     * Bank-confirmation templates override to CONFIRMATION; bill/uplatnica
     * templates keep the BILL default. Drives [TemplateRegistry.classifyDocType].
     */
    val docType: DocType get() = DocType.BILL

    fun matches(doc: NormalizedDoc): Boolean

    fun extract(doc: NormalizedDoc): ExtractedFields
}
