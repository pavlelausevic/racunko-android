# Privacy — Računko

Računko is built so that your bill data **cannot leave your phone**. This is not
a policy promise layered on top of the app — it is enforced by the app's
construction.

## No network, by construction

- The app declares **no `INTERNET` permission**. The manifest even strips it with
  `tools:node="remove"`, so no bundled library can pull it back in. `ACCESS_NETWORK_STATE`
  is stripped the same way. An app with no `INTERNET` permission physically cannot
  open a socket — there is no "trust us," there is the Android permission model.
- On-device engines only: text recognition is **bundled Tesseract in both
  flavors**, QR decoding is bundled ML Kit (`gms`) or ZXing (`foss`). Nothing is
  uploaded for "cloud recognition."
- **The app is excluded from Android Auto Backup** (`allowBackup="false"`). Since
  the archive lives in the app's own storage, leaving backup on would have shipped
  your bills to Google Drive. An app that removes its own `INTERNET` permission
  must not let a system service carry the same data out.

## What is stored, and where

Everything is **local to the device**. Nothing is synced or backed up to a server.

Since v1.7 the archive is kept in the app's **private storage**: no permission is
requested, no folder is granted, and nothing is visible to other apps. A copy in a
folder of your choosing is optional, off by default, and yours to turn on.

| Data | Where | Why | How to clear |
|---|---|---|---|
| Renamed bills / confirmations | app-private storage | the archive itself | select the cards → delete, with „delete files too" |
| Optional visible copy | a folder you pick, only if you turn it on | so you can see the files outside Računko | turn it off (nothing is deleted) and delete the folder yourself |
| Pairing index (bill records) | app-private Room database | matches confirmations to bills | Settings → „Obriši istoriju uparivanja" |
| Payee memory (account → provider + address) | app-private Room database | prefill recurring monthly bills | Settings → „Obriši zapamćene primaoce" |
| QR images | **nowhere by default** — the code is derived when shown | a stored QR is a copy of a payment order you did not ask to keep | „To gallery" writes one to `Pictures/Racunko` only when you tap it, and pairing deletes exactly that copy |
| Exported archive | the folder you pick, only when you run Export | your backup, readable without Računko | delete the folder |
| Settings (addresses, language, provider labels) | app-private DataStore | your configuration | uninstall / clear app data |

The export carries a `racunko.json` next to the files. It holds what the file names
cannot — deadlines, reminders, which confirmation belongs to which bill, the address
book, payee memory. It is plain JSON you can read; it goes only where you export it.

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
