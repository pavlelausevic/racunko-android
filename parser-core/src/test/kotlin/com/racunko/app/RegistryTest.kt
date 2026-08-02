package com.racunko.app

import com.racunko.app.parser.registry.NormalizedDoc
import com.racunko.app.parser.registry.SourceKind
import com.racunko.app.parser.registry.TemplateRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Change 9.7 — the ordered registry: a document matching a specific template
 * never falls through to the generic fallback; removing all specific templates
 * routes it to the fallback without crashing.
 */
class RegistryTest {

    private val registry = TemplateRegistry.default()

    private fun doc(text: String, kind: SourceKind = SourceKind.PDF_OCR) =
        NormalizedDoc.of(text, sourceKind = kind)

    private val intesaText = "Potvrda transakcije UPLATA/ISPLATA MODEL I POZIV NA BROJ ODOBRENJA - 040255500012"
    private val uplatnicaText = """
        primalac SZ DOBRIVOJA STANKOVICA 99
        racun primaoca 200-5550001234567-64
        poziv na broj 040255500012
        za uplatu 1.500,00
    """.trimIndent()

    @Test
    fun specificTemplateHandlesItsDocument() {
        assertEquals("intesa", registry.firstMatching(doc(intesaText)).id)
        assertEquals("uplatnica", registry.firstMatching(doc(uplatnicaText)).id)
    }

    @Test
    fun unknownDocumentFallsToGenericLast() {
        assertEquals("generic", registry.firstMatching(doc("nothing recognizable here 123")).id)
    }

    @Test
    fun removingSpecificTemplatesRoutesToFallbackWithoutCrash() {
        val onlyGeneric = registry.withoutSpecific()
        // a doc that WOULD have matched intesa now goes to the generic fallback
        assertEquals("generic", onlyGeneric.firstMatching(doc(intesaText)).id)
        // and it still extracts something rather than throwing
        assertNotNull(onlyGeneric.extract(doc(intesaText)))
    }
}
