# Računko — User Manual

*(Srpska verzija: [UPUTSTVO.md](UPUTSTVO.md))*

Računko is a free Android app that organizes Serbian utility bills: it
recognizes them, names the files nicely, gives you a payment QR code and keeps
track of which payments are confirmed. It works **entirely offline** — your
bills never leave your phone.

---

## 1. Installation (no technical knowledge required)

Računko is not (yet) on Google Play. It installs from an APK file — the normal
way to install Android apps outside the store:

1. On your phone, open the project page, tap **Releases**, and download the
   newest file ending in **`.apk`** (~99 MB).
2. When the download finishes, open the file from the notification shade or
   from your *Download* folder.
3. Android will say installs from this source aren't allowed — tap
   **Settings**, enable **"Allow from this source"**, and go back.
4. Tap **Install**. Done.

> ❓ **Why does the phone warn me?** Android does this for every app outside
> the Play Store. Računko is open source — anyone can inspect exactly what it
> does — and it has no internet permission, so it cannot send your data
> anywhere.

> ❓ **Is there a PC (.exe) or iPhone version?** No — Računko is Android-only
> (Android 10+).

## 2. First launch — one tap

On first launch Računko creates the **`Download/Racunko`** folder and asks you
to confirm it:

1. Tap **"Confirm folder"**.
2. A system screen opens already positioned on the *Racunko* folder — just tap
   **"Use this folder"** and confirm.

That's the whole setup. Your bills (`Racuni`), confirmations (`Potvrde`) and
QR images live in that folder — always accessible from any file manager too.

> If "Use this folder" is greyed out, you are in the *Download* folder — open
> *Racunko* first, then confirm (the ⓘ button explains this in-app).

## 3. Adding bills

Three ways, all from the **➕** button on the "Računi" (Bills) tab:

| Method | When |
|---|---|
| **📄 Add from file** | A PDF bill arrived by email or was downloaded from a provider portal — the picker opens at *Download* |
| **📷 Add photo** | You already have a picture/screenshot of the bill |
| **📸 Scan bill** | A paper bill/payment slip is in your hand — point the camera at the IPS QR code |

Then tap **Obradi** (Process). Računko reads the bill and shows a card with
four fields: **provider, address, month, amount**. Whatever was read with
certainty is filled in; anything uncertain shows a question mark (e.g.
"address?") — tap the field to complete it. *Računko never guesses — it asks.*

The file is moved to `Download/Racunko/Racuni` and renamed, e.g.:

```
infostan_KD7_maj26_11152.pdf
   │       │    │      └─ amount (rounded)
   │       │    └─ month + year
   │       └─ your address label (from the address book)
   └─ provider
```

> 💡 You can also drop a bill straight into `Download/Racunko/Racuni` with a
> file manager — Računko picks it up automatically on next open.

## 4. Address book (recommended, takes 2 minutes)

The address is the one thing Računko cannot derive by itself. In
**Settings → My addresses** enter:

- **left**: a short label used in file names (e.g. `KD7`)
- **right**: the address text as printed on the bill — Cyrillic or Latin

Multiple rows may share one label (different providers write the same address
differently). Without the book everything still works — you just fill the
address manually per bill.

## 5. The payment QR

On a bill's card:

- **show QR** — scan it straight from your banking app.
- **QR image** — saves the code to your gallery (with the bill name printed
  under it), for banks that scan from the gallery.
- **Make QR** — for bills **without** a QR (old-style paper slips): Računko
  builds a valid NBS IPS code itself. For safety this only works when the
  recipient's account number passes the checksum (MOD 97) and the amount is
  known. For a generated code, always verify recipient, amount and reference
  in your bank before confirming payment.

> Računko **never pays on your behalf** — it only prepares the code. You
> always confirm payments in your own bank.

## 6. Payment confirmations

After paying, keep the proof with the bill. Two ways:

1. **From your banking app:** Share the confirmation (PDF or image) → choose
   **Računko**. It pairs with the bill automatically.
2. **➕ on the bill's card** ("add confirmation") — binds directly to that bill.

Pairing runs in three layers: by **payment reference**, then by **recipient
account + amount**, and if nothing is certain, Računko lets you pick the bill
yourself. A paired confirmation is renamed `uplata_` + the bill's name and the
bill is marked **paid ✓**.

## 7. Multiple units at one address (flat + garage)

Two InfoStan bills for the same address differ by subscriber id. The first
time names would collide, Računko asks for a short unit tag (`G1`, `STAN`,
`LOKAL`…) with a **"Remember for this unit"** option — from the next month the
file names itself, e.g. `infostan_SG26-G1_jun26_1200.pdf`.

## 8. Report

Select bills in the list → **Make report**: a summary by month and address
(provider, amount, paid/unpaid), ready to copy or share — handy for flatmates,
family or your accountant.

## 9. Settings

Language (Serbian/English/system), address book, provider display names,
storage location, and destructive actions (clear pairing history, clear
remembered payees, empty the folder — double-confirmed, irreversible).

## 10. FAQ

**Where is my data?** Only on your phone, in `Download/Racunko` (plus the
app's local database). The app has no internet permission — verify it yourself
in Android's app settings.

**What if I uninstall the app?** The files in `Download/Racunko` remain —
they're your normal files. Only the pairing memory is lost.

**My bank isn't recognized?** The confirmation will ask for manual pairing —
it works, just not automatically. Want your bank supported? Open an issue on
the project's GitHub page with a **redacted** sample (cover your name, address
and account numbers!).

**How do I build the APK myself?** Install the free
[Android Studio](https://developer.android.com/studio), open the downloaded
project (`File → Open`), let it sync, then
`Build → Build App Bundle(s) / APK(s) → Build APK(s)`. The APK lands in
`app/build/outputs/apk/gms/debug/`. Power users: commands are in
[README.md](README.md).

**How do I report a bug without knowing GitHub?** Create a free account on
github.com, open the project page → **Issues** tab → **New issue** → describe
what happened (Serbian is fine). Screenshots help — but **never attach a full
bill** with your personal data.

---

*Računko is an open-source project under the [Apache-2.0](LICENSE) license.
Contact: pavle@lausevic.com*
