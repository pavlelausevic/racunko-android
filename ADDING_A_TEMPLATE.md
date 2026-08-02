# Adding a template — a worked example

This walks through adding support for a new bank confirmation end to end. The same four steps apply to a utility issuer or a paper payment slip. Prerequisites: `ARCHITECTURE.md` (registry, checksums) and `CONTRIBUTING.md` (rules, PR checklist). No Android device required.

Say a user reports a confirmation from **"Banka X"** that Računko leaves blank.

## Step 1 — Get a redacted text fixture

Ask the reporter to use **„Prijavi neprepoznat dokument"**, or paste the OCR text themselves. You receive something like (noise and mangled diacritics kept on purpose):

```
BANKA X  Potvrda o placanju
Nalogodavac        MARKO M.
Racun nalogodavca  ***masked***
Primalac           EPS AD BEOGRAD
Racun primaoca     190-99870-10
Poziv na broj      97 25-02005555555-2605
Iznos              3.962,69 RSD
Datum              06.05.2026
Ref                BX9931204471
```

Replace any real recipient account with a synthetic **checksum-valid** one if needed:
```
./gradlew :parser-core:run --args="mkaccount 190 99870"   # → 190000000009987010
```
Save it:
```
parser-core/src/test/fixtures/bankax/eps_basic.txt
```

## Step 2 — Write the expected result

`parser-core/src/test/fixtures/bankax/eps_basic.expected.json`:
```json
{
  "sourceKind": "PDF_OCR",
  "provider": "eps",
  "recipientAccount": "190000000009987010",
  "accountVerified": true,
  "amount": 3963,
  "month": null,
  "paymentReference": "25020055555552605",
  "addressLabel": null,
  "pairsWith": "eps_*_*_3963",
  "mustNotPairWith": ["BX9931204471"]
}
```
Note: `month` is null (confirmations rarely carry the billing month — it comes from pairing). The bank's own `Ref` must be in `mustNotPairWith`.

## Step 3 — Implement the template

`parser-core/…/templates/BankaXTemplate.kt`:
```kotlin
class BankaXTemplate : DocumentTemplate {
    override val id = "bankax"

    // cheap, specific: label co-occurrence on normalized text
    override fun matches(doc: NormalizedDoc): Boolean {
        val t = doc.normText
        return "banka x" in t && "racun primaoca" in t && "poziv na broj" in t
    }

    override fun extract(doc: NormalizedDoc): ExtractedFields {
        val t = doc.normText

        // label-anchored, never positional
        val account = ACCOUNT.find(t)?.groupValues?.get(1)
            ?.let(::to18Digits)                       // normalize BBB-…-CC → 18 digits
        val accountVerified = account != null && AccountChecksum.isValid(account)

        val reference = REFERENCE.find(t)?.groupValues?.get(1)?.digitsOnly()
        val amount    = AMOUNT.find(t)?.let { halfUp(it.groupValues[1], it.groupValues[2]) }
        val provider  = detectProvider(recipientName = RECIPIENT.find(t)?.groupValues?.get(1), text = t)

        return ExtractedFields(
            provider = provider,
            recipientAccount = account,
            accountVerified = accountVerified,
            amount = amount,
            month = null,                              // not on this document
            paymentReference = reference,
            addressCandidates = emptyList()            // pairing/memory supplies address
        )
    }

    companion object {
        // anchor each regex to its label; tolerate OCR spacing
        private val ACCOUNT   = Regex("""racun primaoca\s+(\d{3}-?\d{1,13}-?\d{2})""")
        private val REFERENCE = Regex("""poziv na broj\s+(\d[\d\-\s]{4,}\d)""")
        private val AMOUNT    = Regex("""iznos\s+([\d.]+),(\d{2})\s*rsd""")
        private val RECIPIENT = Regex("""primalac\s+([a-z0-9 .]+)""")
    }
}
```
Register it **before** the generic fallback:
```kotlin
TemplateRegistry.register(
    IntesaTemplate(), ErsteTemplate(), AikTemplate(), BankaXTemplate(),
    UplatnicaTemplate(),
    GenericFallbackTemplate()   // always last
)
```

Guidelines that keep templates robust:
- **Anchor to labels, not positions.** OCR interleaves two-column layouts, so "the value is 3 lines down" breaks; "the value follows the word `iznos`" holds.
- **Never trust an account you didn't checksum.** `accountVerified=false` disables layer-2 pairing and QR generation for that field.
- **Ignore the debit-side reference** if the document shows both; prefer the credit/approval side, and rely on suffix-matching to absorb model prefixes.
- **Don't invent a month.** If it isn't on the page, leave it null.

## Step 4 — Add the test and run

Point the parameterized fixture test at the new folder (it usually auto-discovers `fixtures/**/*.expected.json`). Then:
```
./gradlew :parser-core:test
```
Confirm: your case passes; the historical acceptance tests (Intesa/Erste/AIK/§10/#7/#8) stay green; and your `mustNotPairWith` entries pair with nothing.

## Step 5 — Open the PR

Follow the checklist in `CONTRIBUTING.md`. A reviewer should be able to see the fixture, the expected JSON, the template, and a passing test in one diff — that's the whole contribution.
