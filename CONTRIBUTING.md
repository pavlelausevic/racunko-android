# Contributing to Računko

Thank you for helping make bill-paying less painful. The most valuable contributions are **new document templates** (banks, utilities, payment slips) and **fixtures** for documents Računko doesn't yet handle. You do **not** need an Android device to contribute to the parser.

Please read `ARCHITECTURE.md` first — especially "The template registry" and "Numbers you can trust."

## Ground rules

- **Deterministic only.** No AI/ML for understanding a bill. Templates are label-anchored OCR + regex; numbers are verified by checksum, not guessed.
- **Privacy first.** Never commit real personal data or real bill images. Fixtures are **redacted text**, with account numbers replaced by synthetic-but-checksum-valid ones (see below).
- **No network.** The app has no `INTERNET` permission and must never gain one. Don't add dependencies that phone home.

## Dev setup

1. JDK 17, Android Studio (latest stable).
2. Clone, then run the parser tests with **no emulator needed**:
   ```
   ./gradlew :parser-core:test
   ```
3. Build the app:
   ```
   ./gradlew :app:assembleGmsDebug     # Play-services flavor (ML Kit)
   ./gradlew :app:assembleFossDebug    # F-Droid flavor (ZXing + Tesseract)
   ```
Keep `parser-core` free of Android imports; if you need something from the device, add it to `:platform-api`.

## Adding support for a new bank or utility (the recipe)

This is the same four steps whether it's a bank confirmation or a QR-less paper bill. Full worked example in `ADDING_A_TEMPLATE.md`.

1. **Capture a fixture (redacted text).** Best source: the in-app **„Prijavi neprepoznat dokument"** export, which gives you normalized OCR text already stripped of the image and auto-masked PII. Otherwise, paste the OCR/text output and manually redact. Replace any real account with a **synthetic checksum-valid** one (there's a helper: `./gradlew :parser-core:run --args="mkaccount 190 99870"` prints a valid 18-digit account). Save under `parser-core/src/test/fixtures/{issuer}/{case}.txt`.
2. **Write the expected result** as `{case}.expected.json` next to it: provider, amount, month (or null), recipientAccount, paymentReference, addressLabel (or null), and which fields should be `verified`.
3. **Implement the `DocumentTemplate`.** Add a class in `parser-core/…/templates/`, register it in `TemplateRegistry` **before** the generic fallback. `matches` = cheap label co-occurrence on `doc.normText`. `extract` = named, label-anchored regex constants (never positional). Reuse `AccountChecksum`, the amount half-up rounder, and the month rules.
4. **Add the test.** Point a parameterized test at your fixture + expected JSON. Run `./gradlew :parser-core:test`. Also add at least one **false-positive** assertion if your document contains distractor numbers (balances, card numbers, internal references) — they must not become pairing keys.

Keep each template small and self-contained; that's the whole point of the registry.

## Reporting a document Računko couldn't read (no coding)

Two ways, both privacy-safe:
- **In-app:** „Prijavi neprepoznat dokument" → review the auto-masked text, scrub anything else, fill the expected-fields form → it opens a pre-filled GitHub issue with a ready fixture.
- **By hand:** open an issue using the *Unrecognized document* template, paste the **redacted** OCR text, and state what each field *should* have been. Do not attach images or original PDFs.

A good report is already a failing test; a maintainer can usually turn it into a merged template quickly.

## Localization

UI strings are Serbian-first in `values/strings.xml`, English in `values-en/`. Month tokens in filenames are always Serbian lowercase regardless of UI language — don't localize those. New strings must go through resources.

## FOSS / engine changes

If you touch OCR or QR, respect the `platform-api` interfaces and update **both** flavors (`gms`, `foss`). Don't call ML Kit or ZXing from `parser-core` or the domain layer.

## Pull request checklist

- [ ] `./gradlew :parser-core:test` passes; new fixture + expected JSON included.
- [ ] New template registered before the generic fallback; existing tests still green (Intesa/Erste/AIK/§10/#7/#8 unchanged).
- [ ] Any distractor numbers covered by a false-positive assertion.
- [ ] Checksum used for every account-like field; unverified values don't pair or generate QR.
- [ ] No Android imports in `parser-core`; both flavors build if engines changed.
- [ ] No new permissions; no network calls; no real personal data or images committed.
- [ ] Strings via resources (sr default, en provided).
- [ ] Commits signed off (DCO: `git commit -s`).

## License & sign-off

By contributing you agree your work is licensed under the project license (see `LICENSE`) and you certify the DCO (`Signed-off-by` on each commit).
