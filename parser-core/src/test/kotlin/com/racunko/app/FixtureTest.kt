package com.racunko.app

import com.racunko.app.parser.AddressEntry
import com.racunko.app.parser.AddressMatcher
import com.racunko.app.parser.AmountParser
import com.racunko.app.parser.BillName
import com.racunko.app.parser.IpsQr
import com.racunko.app.parser.MonthDetector
import com.racunko.app.parser.Months
import com.racunko.app.parser.ProviderDetector
import com.racunko.app.parser.SpaceId
import com.racunko.app.parser.registry.NormalizedDoc
import com.racunko.app.parser.registry.SourceKind
import com.racunko.app.parser.registry.TemplateRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The fixture corpus: every `fixtures/{issuer}/{case}.txt` + `.expected.json`
 * pair is fed through the shipped code and checked field by field.
 *
 * The corpus is the specification. A Kotlin test proves the Kotlin code works;
 * this corpus proves *an* implementation works, in a form a Swift or any other
 * port can run unchanged. That is the whole reason it exists, and the reason
 * vectors belong here rather than embedded in Kotlin strings.
 *
 * **Both extraction paths are exercised per case**, because the app uses both
 * and they are not the same code:
 *
 *  - the **bill path** — ProviderDetector → AmountParser → MonthDetector →
 *    AddressMatcher → BillName — is what `Pipeline.buildBillCard` runs and what
 *    produces the file name the user actually sees;
 *  - the **template registry** — `TemplateRegistry.extract` — is what classifies
 *    documents and what recovers the recipient account on a QR-less bill.
 *
 * A fixture asserts only the keys it contains: a case that knows nothing about
 * the address book simply omits `addressLabel`. Adding a field to a fixture is
 * therefore always safe, and never silently unchecked — an unknown key is a
 * failure, so a typo cannot quietly disable an assertion.
 *
 * Pairing is deliberately NOT expressible here. Pairing needs a corpus of other
 * bills to pair against, which is context a single-document fixture does not
 * have; it lives in `PairingTest` / `FalsePositiveTest` where that context can
 * be built.
 */
class FixtureTest {

    private val registry = TemplateRegistry.default()

    /** Every key a fixture may set. Anything else is a typo, and fails. */
    private val knownKeys = setOf(
        "sourceKind", "ips", "note",
        "provider", "addressBook", "addressLabel", "addressAmbiguous", "month", "amount",
        "recipientAccount", "accountVerified", "paymentReference", "spaceId",
        "expectedName",
        "looksLikeBill", "docType", "docTypeConfidence", "docTypeLean"
    )

    private fun fixturesDir(): File = listOf(
        File("src/test/fixtures"),
        File("parser-core/src/test/fixtures")
    ).first { it.isDirectory }

    @Test
    fun everyFixtureExtractsItsExpectedFields() {
        val expectedFiles = fixturesDir().walkTopDown()
            .filter { it.isFile && it.name.endsWith(".expected.json") }
            .sortedBy { it.path }
            .toList()
        assertTrue("no fixtures found", expectedFiles.isNotEmpty())

        for (expectedFile in expectedFiles) {
            val case = expectedFile.name.removeSuffix(".expected.json")
            val textFile = File(expectedFile.parentFile, "$case.txt")
            assertTrue("missing ${textFile.name}", textFile.exists())
            check(expectedFile.parentFile.name + "/" + case, expectedFile.readText(), textFile.readText())
        }
    }

    private fun check(where: String, json: String, text: String) {
        for (key in keysIn(json)) {
            assertTrue("$where: unknown fixture key \"$key\"", key in knownKeys)
        }

        val ips = strOrNull(json, "ips")?.let { IpsQr.parse(it) }
        val doc = NormalizedDoc.of(
            rawText = text,
            ipsQr = ips,
            sourceKind = SourceKind.valueOf(str(json, "sourceKind"))
        )

        // ---- bill path: what the card and the file name are built from -------
        val provider = ProviderDetector.detect(ips, text)
        val amount = AmountParser.parse(ips, text)
        val month = MonthDetector.detect(provider, ips, text)
        val address = AddressMatcher.detect(addressBook(json), ips, text, provider)

        if (has(json, "provider")) assertEquals("$where provider", str(json, "provider"), provider)
        if (has(json, "amount")) assertEquals("$where amount", longOrNull(json, "amount"), amount)
        if (has(json, "month")) {
            assertEquals("$where month", strOrNull(json, "month"), month?.let { Months.token(it) })
        }
        if (has(json, "addressLabel")) {
            assertEquals("$where addressLabel", strOrNull(json, "addressLabel").orEmpty(), address.label)
        }
        if (has(json, "addressAmbiguous")) {
            assertEquals("$where addressAmbiguous", boolean(json, "addressAmbiguous"), address.ambiguous)
        }
        // The space id keys the sub-label, so it belongs to the naming path, not
        // to the registry. `ExtractedFields.spaceId` is a different value on the
        // same name: for a bill carrying a QR it is null, because the registry
        // never reaches the RO layout. Asserting that one here would pin `null`
        // for every QR bill and prove nothing about the file name.
        if (has(json, "spaceId")) {
            assertEquals("$where spaceId", strOrNull(json, "spaceId"), SpaceId.detect(provider, ips, text))
        }
        if (has(json, "expectedName")) {
            assertEquals(
                "$where expectedName",
                str(json, "expectedName"),
                BillName.build(provider, address.label, month, amount)
            )
        }

        // ---- template registry: classification + QR-less account recovery ----
        val f = registry.extract(doc)
        if (has(json, "recipientAccount")) {
            assertEquals("$where recipientAccount", strOrNull(json, "recipientAccount"), f.recipientAccount)
        }
        if (has(json, "accountVerified")) {
            assertEquals("$where accountVerified", boolean(json, "accountVerified"), f.accountVerified)
        }
        if (has(json, "paymentReference")) {
            assertEquals("$where paymentReference", strOrNull(json, "paymentReference"), f.paymentReference)
        }

        // ---- what kind of document is this? ----------------------------------
        // Answered by fingerprint, never by filename and never by QR absence.
        // `looksLikeBill` gates what a folder scan offers the user; `docType`
        // drives the intake question. Both are claims about the document, so
        // they belong here; what the app DOES with a guess is a decision table
        // and stays in ClassifyDocTypeTest.
        if (has(json, "looksLikeBill")) {
            assertEquals(
                "$where looksLikeBill",
                boolean(json, "looksLikeBill"),
                registry.looksLikeBill(doc)
            )
        }
        if (has(json, "docType") || has(json, "docTypeConfidence") || has(json, "docTypeLean")) {
            val guess = registry.classifyDocType(doc)
            if (has(json, "docType")) {
                assertEquals("$where docType", str(json, "docType"), guess.type.name)
            }
            if (has(json, "docTypeConfidence")) {
                assertEquals(
                    "$where docTypeConfidence",
                    str(json, "docTypeConfidence"),
                    guess.confidence.name
                )
            }
            if (has(json, "docTypeLean")) {
                assertEquals("$where docTypeLean", strOrNull(json, "docTypeLean"), guess.lean?.name)
            }
        }
    }

    /**
     * The address book this case resolves against. Most cases want the shared
     * fictional book, so `addressBook` is omitted and [SampleAddresses] applies.
     *
     * A case that is ABOUT the book — one entry, a blank pattern — declares its
     * own, in a one-line form the flat schema can carry:
     * `"LABEL=pattern|other pattern;LABEL2=pattern"`. Entries split on `;`,
     * a label from its patterns on `=`, patterns from each other on `|`.
     */
    private fun addressBook(json: String): List<AddressEntry> {
        val spec = strOrNull(json, "addressBook") ?: return SampleAddresses.MAP
        return spec.split(";").filter { it.isNotEmpty() }.map { entry ->
            AddressEntry(entry.substringBefore("="), entry.substringAfter("=").split("|"))
        }
    }

    // --- tiny readers for the flat fixture schema (no JSON dependency) ---

    private fun keysIn(json: String): List<String> =
        Regex("\"([A-Za-z]+)\"\\s*:").findAll(json).map { it.groupValues[1] }.toList()

    private fun has(json: String, key: String): Boolean =
        Regex("\"$key\"\\s*:").containsMatchIn(json)

    private fun str(json: String, key: String): String =
        strOrNull(json, key) ?: error("missing string $key")

    private fun strOrNull(json: String, key: String): String? {
        val m = Regex("\"$key\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|null)").find(json) ?: return null
        if (m.groupValues[1].isEmpty() && m.value.trimEnd().endsWith("null")) return null
        // An IPS payload carries CR/LF inside its P: field, so the escapes a
        // fixture may legitimately contain have to survive the round trip.
        return m.groupValues[1]
            .replace("\\r", "\r")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun boolean(json: String, key: String): Boolean =
        Regex("\"$key\"\\s*:\\s*(true|false)").find(json)!!.groupValues[1].toBoolean()

    private fun longOrNull(json: String, key: String): Long? {
        val m = Regex("\"$key\"\\s*:\\s*(-?\\d+|null)").find(json) ?: return null
        return m.groupValues[1].toLongOrNull()
    }
}
