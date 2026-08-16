# Računko — Architecture

Računko renames Serbian utility bills, extracts (or generates) the NBS IPS payment QR, and pairs bank payment confirmations back to their bills — **entirely on-device, with no network permission**. This document explains how the pieces fit so you can extend it safely.

## Design principles

1. **Deterministic, not AI.** Fields come from decoded QR codes, label-anchored OCR templates, and checksum-verified numbers. When a value can't be *proven*, the app asks the user — it never guesses.
2. **The parser is the extension surface.** All parsing/pairing lives in a pure JVM module with no Android dependencies, so it is testable, reviewable, and contributable without an emulator.
3. **No proprietary lock-in in the core.** QR and OCR engines sit behind interfaces; a FOSS flavor swaps them out for F-Droid.
4. **Privacy by construction.** No `INTERNET` permission. All memory is local. Contributions travel as redacted text fixtures, never as images.

## Module map

```
:parser-core     Pure Kotlin/JVM. No Android. The brain.
                 - text normalization (Cyrillic→Latin, diacritic folding)
                 - NormalizedDoc / ExtractedFields models
                 - DocumentTemplate interface + TemplateRegistry
                 - bank/uplatnica templates (Intesa, Erste, AIK, Uplatnica, generic)
                 - IPS QR payload parse + build
                 - AccountChecksum (ISO 7064 MOD 97-10)
                 - pairing engine (layers 1–3)
                 - DueDateParser (label-anchored ONLY; never the issue date)
                 - Report + Padding (columns spaced by glyph WIDTH, so the
                   shared summary still aligns in a proportional font)
                 - ALL unit tests live here

:platform-api    Tiny interfaces the core needs from the device:
                 QrDecoder, QrEncoder, TextRecognizer, LiveQrScanner

:app             Android. UI (Compose, Material 3, Serbian-first; sr/en/ru),
                 storage (app-private files; an optional SAF mirror), CameraX,
                 export/import, DI wiring,
                 flavor-specific engine implementations.
                 - MainActivity: singleTask, the ONLY long-lived window
                 - ShareTargetActivity: invisible trampoline that owns the
                   SEND intent-filters and relaunches MainActivity from our
                   own process, so a sender's launch flags cannot spawn a
                   second task in Recents
```

**Dependency rule:** `:app` → `:parser-core` + `:platform-api`. `:parser-core` depends on **nothing Android**. If you find yourself importing `android.*` inside `parser-core`, the abstraction belongs in `platform-api` instead.

## Build flavors (engine swap)

| Flavor | Target | QR decode | QR encode | OCR |
|---|---|---|---|---|
| `gms` (default) | Play Store | ML Kit barcode (bundled) | ZXing | Tesseract (`srp`, `srp_latn`, `eng`) |
| `foss` | F-Droid | ZXing | ZXing | Tesseract (`srp`, `srp_latn`, `eng`) |

**OCR is Tesseract in both since v1.7.** ML Kit's recognizer is Latin-script and
no Cyrillic model exists for it, while every bill a public utility prints here is
Cyrillic — on a screenshot of one it returned nothing usable. The swap also made
the Play build *smaller* (37.1 -> 33.5 MB). ML Kit stays for QR decoding, where
it is better and has no substitute in that flavor. `TesseractTextRecognizer` is
therefore the one engine that lives in the shared source set rather than under a
flavor folder — the rule exists to keep a *proprietary* engine out of shared
code, and Tesseract is Apache-2.0.

Both implement the same `platform-api` interfaces and are injected via DI. The core and every test run without either engine, because tests feed text and QR strings directly.

## Data flow

```
INPUT
 ├─ PDF (text)      → PDFBox text ─┐
 ├─ PDF (image)     → OCR ─────────┤
 ├─ Image/screenshot→ OCR ─────────┤
 └─ Live camera     → QR decode ───┤ (+ optional still → OCR)
                                   ▼
                        NormalizedDoc { rawText, normText, ipsQr?, sourceKind }
                                   ▼
                     TemplateRegistry.firstMatching(doc)      ← extension point
                                   ▼
                        ExtractedFields { provider, addressCandidates,
                                          month, amount, recipientAccount,
                                          paymentReference, per-field verified }
                                   ▼
             AccountChecksum verify ──► verified flags gate pairing & QR generation
                                   ▼
        PayeeMemory prefill (account → provider+address)  +  address anchor-zone
                                   ▼
   BILL: build final name → store → (extract or GENERATE IPS QR)
   CONFIRMATION: pairing engine (layer 1 reference → 2 account+amount → 3 manual)
```

## The template registry (how bills/confirmations are understood)

```kotlin
interface DocumentTemplate {
    val id: String
    fun matches(doc: NormalizedDoc): Boolean
    fun extract(doc: NormalizedDoc): ExtractedFields
}
```

- The registry is an **ordered list**; the first template whose `matches` returns true handles the document. A **generic fallback** is always last.
- Bank templates (Intesa, Erste, AIK) and `UplatnicaTemplate` are ordinary entries. Adding support for a new bank/issuer means adding one `DocumentTemplate` and its tests — nothing else changes. See `ADDING_A_TEMPLATE.md`.
- `matches` should be cheap and specific (label co-occurrence on normalized text). `extract` uses **named regex constants anchored to labels**, never positional assumptions, because OCR interleaves two-column layouts.

## Pairing engine (confirmations → bills)

Three layers, first hit wins (unchanged since v1.2):

1. **Payment reference (poziv na broj).** Highest-priority candidate from the credit-side reference; suffix-match ignores the 2-digit model prefix.
2. **Recipient account + amount.** Account normalized to 18 digits; the amount used is the template's *identified transaction amount only* (never balances/fees). Requires a **checksum-valid** account.
3. **Manual.** The user binds a confirmation to a bill via **+** on the bill card, or via chips on an unpaired confirmation.

Distractor numbers (bank-internal references, balances, card numbers, dates) must never become pairing keys — this is enforced by the false-positive test suite.

## Numbers you can trust: checksums

- **Bank account (18 digits = 3 bank + 13 body + 2 control):** `AccountChecksum.isValid` uses ISO 7064 MOD 97-10 and is pinned by real valid accounts as test vectors. A failing account is `verified=false` and is barred from layer-2 pairing and from QR generation.
- **Poziv na broj (model 97):** optional secondary mod-97 check; confirm digit placement against vectors before letting it gate anything.

This is why OCR mistakes surface as "confirm this number," not as a silent wrong payment link.

## IPS QR: parse and generate

The core both **reads** the NBS IPS payload (pipe-separated `KEY:VALUE`, keys `K,V,C,R,N,I,SF,S,RO,P`) and **builds** it from `ExtractedFields` for bills that lack one. Generation is gated on a checksum-valid account + present amount, and is covered by a **round-trip test** (build → decode → identical fields). Follow the official NBS IPS QR field spec for order, UTF-8 (`C:1`), and length limits.

## Storage (app layer, v1.7 — the model changed, ignore anything older)

The archive is **app-private** and asks nothing of the phone: no permission, no
grant dialog, no setup screen.

```
filesDir/racuni     renamed bills
filesDir/potvrde    renamed confirmations
cacheDir/qr         derived QR PNGs (a cache; the system may clear it)
cacheDir/share      one temporary copy per „Share QR", cleaned by the system
```

`FileStore` is the seam (6 methods + `savePng`). `StorageManager.store()` ALWAYS
returns `PrivateStore`, so it has no failure mode and no "not ready yet" state.
When the user turns on „keep a copy in my folder", a bound SAF tree is wrapped
around it by `MirrorStore`: reads never touch the copy, writes are best-effort,
turning it on rewrites the archive into the folder, turning it off deletes
nothing.

**A private file's uri is `file://` and must not leave the app**
(`FileUriExposedException`): `MainViewModel.shareableUri` converts to a
FileProvider uri before every share; `content://` passes through untouched.

`data/Archive.kt` does export/import — the files plus a `racunko.json` manifest
beside them, no ZIP. The manifest carries what file names cannot: deadlines,
reminders, pairings, payee memory, the address book, space bindings. Both halves
are independently optional. `qrImageUri` and a card's `uri` are deliberately NOT
imported — the first would make pairing delete a file in someone else's gallery,
the second is worthless from another device.

**The QR is derived, never stored by default.** `Pipeline.qrFor` goes cache ->
document -> rebuild; the document comes first because a rebuilt payload is not
the issuer's (payee name, payment code and model are not reconstructed), so a
rebuild raises `qrGenerated` and the card says to check before paying. A gallery
copy is written only when the user taps „To gallery", and pairing deletes exactly
that copy.

## Where to start reading

`parser-core/…/TemplateRegistry`, then one bank template (Intesa is the simplest), then `pairing/`, then `AccountChecksum`, then `ipsqr/`. The app layer is thin wiring around these.
