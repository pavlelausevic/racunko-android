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
                 - ALL unit tests live here

:platform-api    Tiny interfaces the core needs from the device:
                 QrDecoder, QrEncoder, TextRecognizer, LiveQrScanner

:app             Android. UI (Compose, Material 3, Serbian-first), storage
                 (MediaStore under Download/Racunko), CameraX, DI wiring,
                 flavor-specific engine implementations.
```

**Dependency rule:** `:app` → `:parser-core` + `:platform-api`. `:parser-core` depends on **nothing Android**. If you find yourself importing `android.*` inside `parser-core`, the abstraction belongs in `platform-api` instead.

## Build flavors (engine swap)

| Flavor | Target | QR decode | QR encode | OCR |
|---|---|---|---|---|
| `gms` (default) | Play Store | ML Kit barcode (bundled) | ZXing | ML Kit text-recognition (bundled) |
| `foss` | F-Droid | ZXing | ZXing | Tesseract (`srp`, `srp_latn`, `eng`) |

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

## Storage (app layer, v1.3)

MediaStore under public Downloads, created once, idempotent by path:
```
Download/Racunko/Racuni     renamed bills
Download/Racunko/Potvrde    renamed confirmations
Download/Racunko            generated/extracted QR PNGs (also indexed for the gallery)
```
No SAF folder-picking by default; „Izaberi drugu lokaciju čuvanja" in Settings overrides it.

## Where to start reading

`parser-core/…/TemplateRegistry`, then one bank template (Intesa is the simplest), then `pairing/`, then `AccountChecksum`, then `ipsqr/`. The app layer is thin wiring around these.
