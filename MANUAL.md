# Računko — User Manual

*(Srpska verzija: [UPUTSTVO.md](UPUTSTVO.md) · На русском: [РУКОВОДСТВО.md](%D0%A0%D0%A3%D0%9A%D0%9E%D0%92%D0%9E%D0%94%D0%A1%D0%A2%D0%92%D0%9E.md))*

Računko is a free Android app that organizes Serbian utility bills: it
recognizes them, names the files nicely, gives you a payment QR code and keeps
track of which payments are confirmed. It works **entirely offline** — your
bills never leave your phone.

---

## 1. Installation (no technical knowledge required)

Računko is not (yet) on Google Play. It installs from an APK file — the normal
way to install Android apps outside the store:

1. On your phone, open the project page, tap **Releases**, and download the
   newest file ending in **`.apk`** (~36 MB).
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

## 2. First launch — there is nothing to set up

You open the app and it works. No permission prompts, no screen before the first
screen, no folder to approve.

Bills and confirmations are kept **inside the app**. Nothing is asked of the
phone and nothing is visible to other apps.

> If you want to see them outside Računko, Settings (section 11) gives you two
> options: **"Export to a folder"** whenever you want a copy, or **"Keep a copy
> in my folder"** so every bill is written there as well.
>
> Because the archive lives inside the app, **uninstalling deletes it**. If the
> archive matters to you, export it now and then.

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

The file is moved into the app's archive and renamed, e.g.:

```
infostan_KD7_maj26_11152.pdf
   │       │    │      └─ amount (rounded)
   │       │    └─ month + year
   │       └─ your address label (from the address book)
   └─ provider
```

> 💡 Got a whole folder of bills? You do not have to add them one by one —
> **Settings → Import from a folder** copies them all in at once. They land in
> the "Files in folder" section and wait for you to tap **"Process"**.

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

## 8. Due date and reminder

Every card carries a **due date**. Računko reads it off the bill, but only
where the bill says exactly that — „rok plaćanja“, „datum dospeća“,
„valuta plaćanja“, „platiti do“. **The issue date is never used as the due
date**, because it is not the same date; when a bill prints no deadline the
field stays empty and you can fill it in yourself.

Tap the due date on a card to set it up:

- **Remind me when the payment is due** — switched on **per bill**.
- **How many days before** — 3 days ahead, for example.

When you open the app, a banner at the top lists the bills whose deadline is
approaching (and how many are already late). **Show** narrows the list to
exactly those. An overdue bill gets a red marker but **keeps its place** in the
list — the order stays by month.

> ⏰ The chosen **time** is remembered but does nothing yet: the reminder
> appears when you open the app, not as a system notification. The screen says
> so too — the time is waiting for notifications to be added.

## 9. Organizing the list — addresses, months, selection

- **Total at the top** — how much is left to pay and how much is paid.
- **Address chips** — they appear once you have more than one address; a tap
  narrows the list to that address.
- **Address sections fold.** With several addresses the screen opens folded —
  an overview you unfold. With a single address there is nothing to choose
  between, so it stays open. Bills **with no address come first**, because
  those are the ones asking for your input.
- **Order** — by the month each bill is *for*, newest at the top.
- **Selecting bills** — a **long press** on a card enters selection mode; from
  then on a plain tap selects. „Select all“ takes **only what is currently
  shown** — with an address filter on, only that address.

## 10. Report

Select bills (long press, then taps) → **Make report**: a summary by month and
address (provider, amount, total), ready to copy or share — handy for
flatmates, family or your accountant.

```
JUL  KD7
EPS        4.200 RSD
InfoStan   4.650 RSD
∑          8.850 RSD
```

The spacing is tuned so the amounts still line up **after you paste it** into
Viber, WhatsApp or a note — not just while it sits in the app.

## 11. Settings

- **Language** — Serbian / English / Russian / follow system.
- **My addresses** — the address book (section 4).
- **Provider labels** — how each provider is written in the file name.
- **Storage location** — tells you the archive is inside the app. There is
  nothing to choose.
- **Export to a folder** — writes bills, confirmations and `racunko.json` into a
  folder you pick. The files stay plain files you can open with anything; the
  manifest beside them carries what a file name cannot — deadlines, reminders,
  which confirmation belongs to which bill, the address book, payee memory.
- **Import from a folder** — brings all of that back, on this phone or another.
  It also works with an ordinary folder of PDFs; you then get what the file
  names carry.
- **Keep a copy in my folder** — when on, every bill is also written to a folder
  you pick, so you can see it outside Računko. Turning it on rewrites the
  existing archive into it immediately. Turning it off **deletes nothing**.
- **Deletions** — pairing history, remembered payees, or emptying the whole
  archive (double-confirmed, irreversible).

## 12. FAQ

**Where is my data?** Only on your phone, in the app's own private storage.
Other apps cannot see it, and Računko has no internet permission — verify that
yourself in Android's app settings. The app is also excluded from Android's
automatic backup, so your bills never reach Google Drive.

**What if I uninstall the app?** **The archive goes with it.** That is the price
of asking nothing of the phone. This is what **Export to a folder** is for — an
exported folder is ordinary files that survive uninstalling, and Import brings
them back together with deadlines, pairings and the address book. If the archive
matters to you, export it before you uninstall or switch phones.

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
