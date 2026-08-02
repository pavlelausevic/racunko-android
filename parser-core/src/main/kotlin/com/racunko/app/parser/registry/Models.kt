package com.racunko.app.parser.registry

import com.racunko.app.parser.MonthYear
import com.racunko.app.parser.Normalizer

/** Where a document's text came from (affects nothing in the core, but templates may care). */
enum class SourceKind { PDF_TEXT, PDF_OCR, IMAGE_OCR, QR_ONLY }

/**
 * A document ready for template matching (Change 1). Carries the raw text, the
 * normalized text (Cyrillic→Latin, diacritic-folded — the surface every
 * `matches`/`extract` works on), an optional decoded IPS QR map, and the source
 * kind. Templates must read `normText`, never re-normalize.
 */
data class NormalizedDoc(
    val rawText: String,
    val normText: String,
    val ipsQr: Map<String, String>? = null,
    val sourceKind: SourceKind
) {
    companion object {
        fun of(
            rawText: String?,
            ipsQr: Map<String, String>? = null,
            sourceKind: SourceKind
        ): NormalizedDoc = NormalizedDoc(
            rawText = rawText.orEmpty(),
            normText = Normalizer.norm(rawText),
            ipsQr = ipsQr,
            sourceKind = sourceKind
        )
    }
}

/**
 * The fields a [DocumentTemplate] extracts (Change 1). Per-field `verified`
 * flags carry the checksum result (Change 5): an unverified account must not
 * pair (layer 2) or generate a QR (Change 5b). `month` is null on documents
 * that don't carry it (most confirmations) — never invent one.
 */
data class ExtractedFields(
    val provider: String,
    val addressCandidates: List<String> = emptyList(),
    val month: MonthYear? = null,
    val amount: Long? = null,
    val recipientAccount: String? = null,
    val accountVerified: Boolean = false,
    val paymentReference: String? = null,
    /** Set true by templates when two different address labels hit one zone. */
    val addressAmbiguous: Boolean = false,
    /**
     * v1.5.2 Change B1: per-space id (canonical, leading zeros stripped) when
     * the document carries one — InfoStan IDENT first; other providers map
     * their subscriber id here later. Null when the provider has none.
     */
    val spaceId: String? = null
) {
    /** Amount has no check digit; "verified" simply means present. */
    val amountVerified: Boolean get() = amount != null

    /**
     * The account usable as a layer-2 pairing key / QR-generation input:
     * null unless it passed the checksum (Change 5). This is how an OCR digit
     * error becomes "confirm this number," never a silent wrong pairing.
     */
    val pairingAccount: String? get() = if (accountVerified) recipientAccount else null
}
