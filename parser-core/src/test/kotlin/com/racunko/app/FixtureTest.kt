package com.racunko.app

import com.racunko.app.parser.registry.NormalizedDoc
import com.racunko.app.parser.registry.SourceKind
import com.racunko.app.parser.registry.TemplateRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parameterized fixture test (Change 4 / Change 9.3): every
 * `fixtures/{issuer}/{case}.txt` + `.expected.json` pair — the exact format the
 * in-app "Prijavi neprepoznat dokument" export produces (see TESTING.md) — is
 * fed through the shipped registry and checked field by field. Adding a
 * template's support is: drop in these two files, done.
 */
class FixtureTest {

    private val registry = TemplateRegistry.default()

    private fun fixturesDir(): File {
        // src/test/fixtures relative to the module root, robust to the run CWD
        val candidates = listOf(
            File("src/test/fixtures"),
            File("parser-core/src/test/fixtures")
        )
        return candidates.first { it.isDirectory }
    }

    @Test
    fun allFixturesExtractExpectedFields() {
        val expectedFiles = fixturesDir().walkTopDown()
            .filter { it.isFile && it.name.endsWith(".expected.json") }
            .toList()
        assertTrue("no fixtures found", expectedFiles.isNotEmpty())

        for (expectedFile in expectedFiles) {
            val caseName = expectedFile.name.removeSuffix(".expected.json")
            val textFile = File(expectedFile.parentFile, "$caseName.txt")
            assertTrue("missing ${textFile.name}", textFile.exists())

            val json = expectedFile.readText()
            val doc = NormalizedDoc.of(
                rawText = textFile.readText(),
                sourceKind = SourceKind.valueOf(str(json, "sourceKind"))
            )
            val f = registry.extract(doc)
            val where = expectedFile.parentFile.name + "/" + caseName

            assertEquals("$where provider", str(json, "provider"), f.provider)
            assertEquals("$where recipientAccount", strOrNull(json, "recipientAccount"), f.recipientAccount)
            assertEquals("$where accountVerified", boolean(json, "accountVerified"), f.accountVerified)
            assertEquals("$where amount", longOrNull(json, "amount"), f.amount)
            assertEquals("$where paymentReference", strOrNull(json, "paymentReference"), f.paymentReference)
            val expectedAddress = strOrNull(json, "addressLabel")
            if (expectedAddress != null) {
                assertTrue("$where address should include $expectedAddress",
                    expectedAddress in f.addressCandidates)
            }
        }
    }

    // --- tiny readers for the flat fixture schema (no JSON dependency) ---

    private fun str(json: String, key: String): String =
        strOrNull(json, key) ?: error("missing string $key")

    private fun strOrNull(json: String, key: String): String? {
        val m = Regex("\"$key\"\\s*:\\s*(?:\"([^\"]*)\"|null)").find(json) ?: return null
        return m.groupValues[1].ifEmpty { if (m.value.endsWith("null")) null else "" }
    }

    private fun boolean(json: String, key: String): Boolean =
        Regex("\"$key\"\\s*:\\s*(true|false)").find(json)!!.groupValues[1].toBoolean()

    private fun longOrNull(json: String, key: String): Long? {
        val m = Regex("\"$key\"\\s*:\\s*(\\d+|null)").find(json) ?: return null
        return m.groupValues[1].toLongOrNull()
    }
}
