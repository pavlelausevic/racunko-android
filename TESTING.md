# Računko — Testing

Računko's correctness is defined by its tests. Because the whole point is to move money-adjacent data around a paper-and-screenshot mess, the bar is: **a wrong pairing or a wrong number must be impossible, and an unreadable document must fail loudly, never silently.** Almost everything is tested in `:parser-core` on the JVM — no emulator needed.

Run everything:
```
./gradlew :parser-core:test
```

## Test taxonomy

| Suite | Question it answers | Lives in |
|---|---|---|
| Fixture corpus | Do the shipped bill path AND template registry produce the expected fields — and the expected file name — for each known issuer? | `fixtures/{issuer}/*` + `FixtureTest` |
| Acceptance (pairing) | Do confirmations bind to the right bill across all three layers? | `AcceptanceTest` (#6 Intesa, #7 Erste, #8 AIK) |
| Checksum | Are account/reference numbers proven, and are bad ones rejected? | `AccountChecksumTest` |
| Pairing (false positives) | Do distractor numbers bind to **nothing**? | `FalsePositiveTest` |
| Address resolution | Anchor zones, boundaries (`7` vs `71`, `46b`), and never guessing a label | `ScanAddressTest`, `NeverGuessAddressTest` |
| IPS QR round-trip | Does generate→decode return identical fields? | `IpsQrRoundTripTest` |
| Payee memory | Is month-2 prefilled, and cleared on reset? | `PayeeMemoryTest` |
| Registry | Does first-match hold, and does removal fall through cleanly? | `RegistryTest`, `ClassifyDocTypeTest` |
| Due date | Is the deadline read ONLY from its own label, and never from the issue date? | `DueDateParserTest` |
| Report layout | Do the amounts line up by rendered WIDTH (proportional font), not by character count? | `ReportTest` |
| Degradation (dev tool) | How bad can a photo be before the QR/OCR fails? | `tools/degradation/` (not shipped) |

## Coverage and known gaps

What is covered is deliberately narrow, and the gaps below are **not bugs** —
they are documents nobody has supplied a sample of yet. Računko asks the user
rather than guessing, so an unrecognized issuer degrades into manual entry, not
into a wrong value.

**Bill issuers recognized by name** (`ProviderDetector.PROVIDERS`): `infostan`,
`eps`, `mts`, `yettel` (incl. SBB), `sz` (stambena zajednica). Each has its own
regex; the list is hard-coded on purpose. Any other issuer — vodovod, gradska
toplana, another telco — is read as far as the generic uplatnica template gets
(amount, account, reference from the IPS QR) and the provider is typed by hand.
**Wanted:** one redacted bill per missing issuer.

**Payment deadline** (`DueDateParser`) is verified against the printed layout of
MTS, EPS, InfoStan, SBB/Yettel and Yettel, including each one's decoy dates —
see the per-issuer cases in `DueDateParserTest`. Issuers outside that list are
unverified: if a deadline is not picked up, the fix is one label pattern, and
the useful bug report is **the exact wording of the label**, not the document.

**Bank confirmation templates**: Banca Intesa, Erste, AIK, plus a generic
fallback. Everything else pairs through the generic template, which works but
leans on the payment reference alone; when it cannot decide, the app asks.
**Wanted:** redacted confirmations from other banks — Raiffeisen is the next one
expected.

**Not covered at all, by design:** issuers outside Serbia, and any document
without either an IPS QR or printed labels (a photo of a handwritten slip).

## Fixture format (the contribution unit)

The corpus **is the specification.** A Kotlin test proves the Kotlin code works;
a fixture proves *an* implementation works, in a form a Swift or any other port
runs unchanged. That is why vectors belong here rather than in Kotlin strings.

Each case is two files side by side:

```
parser-core/src/test/fixtures/{issuer}/{case}.txt            # redacted extracted text ("" if QR-only)
parser-core/src/test/fixtures/{issuer}/{case}.expected.json  # what must come out
```

`FixtureTest` runs **both** extraction paths per case, because the app uses both
and they are different code:

| path | code | what it decides |
|---|---|---|
| bill path | ProviderDetector → AmountParser → MonthDetector → AddressMatcher → BillName | the card fields and the **file name the user sees** (`Pipeline.buildBillCard`) |
| template registry | `TemplateRegistry.extract` | document classification, and the recipient account on a **QR-less** bill |

`{case}.expected.json` — every key is optional except `sourceKind`; **a fixture
asserts only the keys it contains**, so a case that knows nothing about the
address book simply omits `addressLabel`. An **unknown key fails the test**, so a
typo cannot silently disable an assertion.

```json
{
  "note": "free text: what this case is here to prove",
  "sourceKind": "PDF_TEXT",
  "ips": "K:PR|V:01|C:1|R:200220618010100048|N:JKP INFOSTAN…|RO:11800512345011-26050-1",

  "provider": "infostan",
  "addressLabel": "KD7",
  "addressAmbiguous": false,
  "month": "maj26",
  "amount": 11152,
  "expectedName": "infostan_KD7_maj26_11152",

  "recipientAccount": "200220618010100048",
  "accountVerified": true,
  "paymentReference": "800614276087260501",
  "spaceId": "800512345011"
}
```

- `ips` is the raw IPS QR payload; omit it for a document that carries none.
- `month` is the **filename token** (`maj26`), or `null` when the bill prints none.
- `addressLabel` resolves against the fictional `SampleAddresses` book; `null`
  means it must stay **empty** — the address is never guessed.
- `expectedName` is the whole point of most cases: it pins the end result, with
  `X` standing in for any field that could not be proven.

Rules for fixtures:
- **Redacted text only**, never images. Personal names masked; the payer's own account masked.
- Any account present must be **synthetic but checksum-valid** (helper: `mkaccount`), so `accountVerified` is meaningful.
- Keep OCR noise in the fixture (status bars, mangled diacritics like `Raéun`) — that's what the parser must survive.

**Pairing is deliberately not expressible here.** Pairing needs a corpus of other
bills to pair against — context a single-document fixture does not have — so it
lives in `AcceptanceTest` / `FalsePositiveTest`, where that context can be built.

## Checksum vectors (pin the algorithm)

`AccountChecksumTest` must assert these validate **true**, and that each with a single flipped digit validates **false**:
```
190000000009987010   200220618010100048
170003000505000876   200555000123456764
```
If your MOD 97-10 implementation doesn't validate all four, fix the implementation — do not weaken the test. A template that extracts a checksum-failing account must set `accountVerified=false`, which bars it from layer-2 pairing and from QR generation.

## False-positive pairing suite (the safety net)

Feed confirmations whose only numbers are distractors and assert **zero** pairings:
- AIK bank-internal `Referenca` (16 digits) — never a pairing key.
- AIK `Stanje nakon promene` (e.g. `900,01`) — never the amount.
- Yettel card number `4111111*****1234` — never a candidate.
- Random dates, phone numbers, PIB/matični brojevi.
Additionally: seed two unpaired bills of amounts `900` and `971` on the same recipient account, feed the AIK `971` confirmation, and assert it uniquely selects `971` (templated-amount rule from v1.2).

## IPS QR round-trip

For each of the 5 original sample bills: `QrEncoder.build(fields)` → `QrDecoder.decode` → parsed fields equal on provider, account, amount, RO, and name. This guards the "generate a QR for QR-less bills" feature end to end.

## Address resolution

- `koste dragojevića 7` matches `koste dragojevića 7 st. 15` and `KOSTE DRAGOJEVIĆA 7 /2/15` but **not** `koste dragojevića 71`; `46b` allowed.
- EPS metering-point anchor beats the mailing address; InfoStan `adresa:` anchor beats the postal block (the SG26-vs-KD7 case).
- Two genuinely different labels in one zone → `ambiguous=true` → chips, no silent pick.

## Degradation matrix (developer harness, not shipped)

`tools/degradation/` (Python) answers "how bad can a photo be." Given a few source bill images, it generates a matrix and measures IPS-QR decode rate + field accuracy:

- **downscale:** 100 → 25% in steps
- **blur:** Gaussian radius 0 → 6 px
- **rotation:** −15° … +15°
- **perspective:** mild → strong keystone
- **shadow/brightness:** even → harsh gradient

Run:
```
python tools/degradation/run.py --input samples/ --out report.csv
```
It prints (a) the empirical threshold below which QR decoding drops off, and (b) the user-facing guidance copy for the camera/photo screens. Record the **current measured threshold and copy in this file** whenever the harness is re-run, so the camera UX guidance stays grounded in data rather than guesswork. This tool is a dev aid — it is **not** part of the shipped app and must not pull proprietary engines into the build.

## CI

CI runs `:parser-core:test` on every PR and builds both flavors (`assembleGmsDebug`, `assembleFossDebug`) to catch engine-abstraction leaks. A PR that adds a template without a fixture, or that weakens a checksum/false-positive assertion, should not be merged.
