package com.racunko.app

import com.racunko.app.parser.registry.NormalizedDoc
import com.racunko.app.parser.registry.SourceKind
import com.racunko.app.parser.registry.TemplateRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4.4 Change 1 — "Potraži u Download" only surfaces real candidates. A file
 * qualifies iff it has a decodable IPS QR or matches a specific template; an
 * arbitrary document (contract, e-book) never does. Content, not filename.
 */
class LooksLikeBillTest {

    private val registry = TemplateRegistry.default()

    private fun doc(text: String?, ipsQr: Map<String, String>? = null) =
        NormalizedDoc.of(text, ipsQr, SourceKind.PDF_OCR)

    @Test
    fun ipsQrDocumentQualifies() {
        val ips = mapOf("K" to "PR", "R" to "200555000123456764", "I" to "RSD1070,00")
        assertTrue(registry.looksLikeBill(doc(text = null, ipsQr = ips)))
    }

    @Test
    fun uplatnicaTextQualifies() {
        val slip = """
            primalac SZ DOBRIVOJA STANKOVICA 99
            racun primaoca 200-5550001234567-64
            poziv na broj 040255500012
            za uplatu 1.500,00
        """.trimIndent()
        assertTrue(registry.looksLikeBill(doc(slip)))
    }

    @Test
    fun arbitraryContractDoesNotQualify() {
        val contract = "Lorem ipsum dolor sit amet, ugovor o zakupu, clan 1, potpis stranaka."
        assertFalse(registry.looksLikeBill(doc(contract)))
        assertFalse(registry.looksLikeBill(doc("")))
    }
}
