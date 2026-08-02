# Privacy — Računko

Računko is built so that your bill data **cannot leave your phone**. This is not
a policy promise layered on top of the app — it is enforced by the app's
construction.

## No network, by construction

- The app declares **no `INTERNET` permission**. The manifest even strips it with
  `tools:node="remove"`, so no bundled library can pull it back in. `ACCESS_NETWORK_STATE`
  is stripped the same way. An app with no `INTERNET` permission physically cannot
  open a socket — there is no "trust us," there is the Android permission model.
- On-device engines only: QR decoding and OCR run from **bundled** models
  (ML Kit in the `gms` flavor; ZXing + Tesseract in the `foss` flavor). Nothing is
  uploaded for "cloud recognition."

## What is stored, and where

Everything is **local to the device**. Nothing is synced or backed up to a server.

| Data | Where | Why | How to clear |
|---|---|---|---|
| Renamed bills / confirmations | `Download/Racunko/**` (your storage) | the whole point — your files, in your Downloads | delete the files |
| Pairing index (bill records) | app-private Room database | matches confirmations to bills | Settings → „Obriši istoriju uparivanja" |
| Payee memory (account → provider + address) | app-private Room database | prefill recurring monthly bills | Settings → „Obriši zapamćene primaoce" |
| QR images | `Download/Racunko` (+ `Pictures/Racunko`) | scan from the gallery to pay | deleted automatically on pairing; or delete manually |
| Settings (addresses, language, provider labels) | app-private DataStore | your configuration | uninstall / clear app data |

Payee memory keeps only what a bill already prints — the **recipient's** account,
provider and address label. It never stores your own account or personal name as
part of the payee key.

## Reporting a document is redacted text only — never images

„Prijavi neprepoznat dokument" (Change 7) exists so you can help improve the
parser **without leaking anything**:

1. It exports the **normalized OCR/extracted text only** — never the image or the
   original PDF.
2. It runs an **automatic redaction** pass first: it masks obvious PII (personal
   names near `platilac`/`ime platioca`, the payer's own account, card numbers).
3. You then review the masked text in a preview and scrub anything else before
   anything leaves the app.
4. The result maps 1:1 to the test-fixture format, so a maintainer receives a
   ready-to-drop-in test — built from text you approved, with account numbers
   replaced by synthetic checksum-valid ones.

If you contribute fixtures by hand, the same rule applies: **redacted text, no
images, synthetic accounts.** See `CONTRIBUTING.md`.

## In short

No network permission. No cloud. No telemetry. Local storage you can clear at any
time. Contributions travel as redacted text, never as images.
