# Računko — Testing

Računko's correctness is defined by its tests. Because the whole point is to move money-adjacent data around a paper-and-screenshot mess, the bar is: **a wrong pairing or a wrong number must be impossible, and an unreadable document must fail loudly, never silently.** Almost everything is tested in `:parser-core` on the JVM — no emulator needed.

Run everything:
```
./gradlew :parser-core:test
```

## Test taxonomy

| Suite | Question it answers | Lives in |
|---|---|---|
| Template extraction | Does each template read the right fields from real (redacted) documents? | `fixtures/{issuer}/*` + parameterized test |
| Acceptance (pinned) | Do the historical cases still produce the exact expected names? | §10 originals, #7 Erste, #8 AIK |
| Checksum | Are account/reference numbers proven, and are bad ones rejected? | `AccountChecksumTest` |
| Pairing (true positives) | Do confirmations bind to the right bill across all three layers? | `PairingTest` |
| Pairing (false positives) | Do distractor numbers bind to **nothing**? | `FalsePositiveTest` |
| Address resolution | Anchor zones, boundaries (`7` vs `71`, `46b`), ambiguity chips | `AddressTest` |
| IPS QR round-trip | Does generate→decode return identical fields? | `IpsQrRoundTripTest` |
| Payee memory | Is month-2 prefilled, and cleared on reset? | `PayeeMemoryTest` |
| Registry | Does first-match hold, and does removal fall through cleanly? | `RegistryTest` |
| Degradation (dev tool) | How bad can a photo be before the QR/OCR fails? | `tools/degradation/` (not shipped) |

## Fixture format (the contribution unit)

Each case is two files, side by side, matching the in-app "Prijavi neprepoznat dokument" export 1:1:

```
parser-core/src/test/fixtures/{issuer}/{case}.txt            # redacted OCR/extracted text
parser-core/src/test/fixtures/{issuer}/{case}.expected.json  # expected ExtractedFields
```

`{case}.expected.json`:
```json
{
  "sourceKind": "IMAGE_OCR",
  "provider": "infostan",
  "recipientAccount": "200220618010100048",
  "accountVerified": true,
  "amount": 971,
  "month": "maj26",
  "paymentReference": "800614276087260501",
  "addressLabel": null,
  "pairsWith": "infostan_SG26_maj26_971",
  "mustNotPairWith": ["5670260000000017", "900"]
}
```
Rules for fixtures:
- **Redacted text only**, never images. Personal names masked; the payer's own account masked.
- Any account present must be **synthetic but checksum-valid** (helper: `mkaccount`), so `accountVerified` is meaningful.
- Keep OCR noise in the fixture (status bars, mangled diacritics like `Raéun`) — that's what the parser must survive.

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
