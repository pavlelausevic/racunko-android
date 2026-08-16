# CLAUDE.md — Računko working notes

Android app: renames Serbian utility-bill PDFs from the NBS IPS QR + text,
generates a QR for bills that lack one, pairs bank confirmations back to bills.
**Fully on-device, no INTERNET permission** (stripped via `tools:node="remove"`).
Serbian-first UI, English in `values-en`. Deterministic only — no AI to
understand a bill; when a value can't be proven, ask the user.

## Build / test (JAVA_HOME must point at the Android Studio JBR)
```
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"   # PowerShell
.\gradlew.bat :parser-core:test                  # pure-JVM tests (the real gate)
.\gradlew.bat :app:assembleGmsDebug :app:assembleFossDebug     # both flavors MUST build
.\gradlew.bat :app:assembleGmsRelease            # signed APK (keystore note below)
.\gradlew.bat :parser-core:run --args="mkaccount 190 99870"    # → valid 18-digit account
```
Signed release APKs are ~33.5 MB gms / ~23.8 MB foss (R8 on since 1.6.1,
Tesseract bundled in BOTH since v1.7; see the size notes in
app/build.gradle.kts). The signing key lives OUTSIDE this repo entirely — the
git-ignored `keystore.properties` points at it by relative path — so it can never
be swept into a commit or into an archive of this folder. Without that file a
release build silently falls back to the DEBUG signature: check
`apksigner verify --print-certs` before shipping anything. Keep APKs,
`prezentacija/`, `.claude/`, tessdata out of git (already in `.gitignore`).

## Modules (dependency rule: :app → :parser-core + :platform-api)
- **:parser-core** — pure Kotlin/JVM, ZERO Android imports. The brain + ALL unit
  tests. Verify purity: `grep -r "import android" parser-core/src/main` = empty.
- **:platform-api** — Android-library; device interfaces only (QrDecoder,
  QrEncoder, TextRecognizer, LiveQrScanner, PlatformEngines, Engines holder,
  DefaultLiveQrScanner 3-frame IPS debounce). No concrete engine here.
- **:app** — Compose UI + domain + data + flavor engine impls.

## Flavors (engine swap, compile-time)
- `gms` (Play): ML Kit **barcode** (bundled, offline) + ZXing encode.
- `foss` (F-Droid): ZXing decode+encode.
- **OCR is Tesseract in BOTH since v1.7.** ML Kit's recognizer is Latin-script
  and no Cyrillic model exists for it, while every bill a public utility prints
  here is Cyrillic — on a screenshot of one it returned nothing usable. Measured
  before swapping: ML Kit OCR 18.2 MB (no Cyrillic) vs Tesseract 16.1 MB with
  `srp`+`srp_latn`+`eng`, so the swap SHRANK gms 37.1 → 33.5 MB. ML Kit stays
  for QR decoding, where it is better and has no substitute in that flavor.
  If OCR comes up again, this is the decision — see the handoff's decision
  register.
- tessdata_fast is bundled at build time by `fetchTessdata` → git-ignored, zero
  runtime network, both flavors.
Flavor engine impls live ONLY in `app/src/{gms,foss}/java/com/racunko/app/engine/`.
`TesseractTextRecognizer` is the ONE exception and sits in `main`: that rule
exists to keep a PROPRIETARY engine out of shared code, and Tesseract is
Apache-2.0.
`RacunkoApp` sets `Engines.instance = EngineFactory.create(ctx)` (per-flavor).

## Key files (one-liners; sizes: App.kt is the big one ~1170 LOC)
parser-core `com/racunko/app/parser/`:
- Normalizer, IpsQr, ProviderDetector, AmountParser, MonthDetector, Months,
  AddressMatcher (DEFAULTS = demo U1/P2; fictional sample map = test-only SampleAddresses),
  BillName, Accounts, AccountChecksum (MOD 97-10), IpsQrPayload (build+decode),
  PayeeMemory (prefill), Report (buildSummary + ProviderNames), Confirmation
  (Intesa/Erste/AIK/Generic BankTemplate), PairingEngine, OcrPolicy.
- `registry/`: DocumentTemplate, NormalizedDoc/ExtractedFields, TemplateRegistry,
  Adapters (wrap BankTemplate), UplatnicaTemplate (NO address — memory/manual).
app:
- `ui/App.kt` — MainScreen, Card, sheets, FileSection + menu, report/disclaimer.
- `ui/CameraScreen.kt` — CameraX live scan (8d).
- `ui/MainViewModel.kt` — UiState + all actions.
- `domain/Pipeline.kt` — processBill/processImageBill/processScannedQr/
  processConfirmation/buildBillCard, generateQr, pairing, applyEdits.
- `domain/{QrExtractor,PdfText,PdfOcr,ScanArtifact,QrCache}.kt`.
- `data/{Db(Room),Storage(v1.7: PrivateStore is primary, optional SafStore behind
  MirrorStore, StorageManager; NoopStore GONE),Archive(export/import via
  racunko.json),Saf(ensureFolders→Racunko/Racuni/Potvrde),Gallery(QR→Pictures,
  ON REQUEST only),Settings(DataStore)}.kt`.

## Conventions
- Filename: `{provider}_{ADDR}_{monthYY}_{amount}.pdf`; confirmation `uplata_`+name;
  QR `{name}_QR.png`. Month token always Serbian lowercase.
- Storage (**v1.7 — the model CHANGED, ignore anything older**): the archive is
  PRIVATE by default, `filesDir/{racuni,potvrde}` via `PrivateStore`, plain
  `java.io.File`. No permission, no grant dialog, no onboarding: a first-run user
  lands straight in the list. `StorageManager.store()` ALWAYS returns the private
  store, so it has no failure mode and no "not ready yet" state.
  A SAF tree is now only the OPTIONAL visible copy („čuvaj i kopiju u mojoj
  fascikli"): `store()` wraps private in `MirrorStore` when a tree is bound —
  reads never touch the copy, writes are best-effort, turning it ON first
  re-writes the whole archive into the folder (a copy that starts empty reads as
  data loss), turning it OFF deletes nothing.
  DELETED with this, do not look for them: `OnboardingScreen`, `needsOnboarding`,
  `NoopStore`, `StorageManager.ensurePublicFolder()` (the Android-11
  greyed-out-grant workaround — with no grant on the critical path the problem
  stops existing), and the folder picker in Settings.
  **A private file's uri is `file://` and MUST NOT leave the app**
  (`FileUriExposedException`): `MainViewModel.shareableUri` converts to a
  FileProvider uri before every share, `content://` passes through untouched;
  `file_paths.xml` carries `files-path` for `racuni/` and `potvrde/`.
  Export/import is `data/Archive.kt` — the files plus a `racunko.json` manifest
  beside them, no ZIP, both halves independently optional (folder without
  manifest = ordinary files; manifest without files = memory and settings back).
  `qrImageUri` and a card's `uri` are deliberately NOT imported: the first would
  make pairing delete a file in someone else's gallery, the second is worthless
  from another device (`reconcileCards` re-links by name anyway).
  QR PNGs are DERIVED, never stored by default — see the step-1 note further
  down; the gallery copy is written ON REQUEST only, and `Pictures/Racunko` via
  MediaStore.Images stays the ONLY MediaStore use.
  Adding files = ACTION_OPEN_DOCUMENT picker; the BILLS tab accepts
  `application/pdf` ONLY (App.kt), so an image gets in through „Dodaj
  fotografiju" — the Potvrde tab takes both. Imported/dropped-in files surface by
  themselves in the folded „Fajlovi u fascikli" section on start/resume/refresh
  and wait for „Obradi" — there is no „Potraži" button any more. Edge-to-edge
  (setDecorFitsSystemWindows(false)) so IME insets work.
- New user strings → both `values/` and `values-en/`.
- Checksum gates everything: unverified account ≠ layer-2 key, ≠ QR generation.

## Build gotchas (already hit + solved — don't re-derive)
- AGP 9 has built-in Kotlin: modules apply ONLY the Android/JVM plugin, NOT
  `kotlin.android` (double `kotlin` extension). Root `plugins{}` pins all with
  `apply false`.
- In `build.gradle.kts`, `java` = JavaPluginExtension → use `import java.net.URI`
  / `java.io.File`, not `java.net.…` inline.
- Gradle tasks must be configuration-cache safe: capture LOCAL vals, no
  script/Project refs (e.g. no `uri()`/`logger` inside `doLast`).
- Tesseract4Android is on JitPack: `com.github.adaptech-cz.Tesseract4Android:4.8.0`
  (settings.gradle has the scoped jitpack repo). NOT on Maven Central.
- `git push` via PowerShell prints to stderr → exit 255 but the push line
  ("a..b main -> main") confirms success; check `git rev-parse HEAD == origin/main`.

## Version / phase state (update as it moves)

**v1.7.0 executed 2026-08-15** — the storage + QR round, shipped after a full
device pass of BOTH flavors on a second phone (SM-G998U, Android 15, release
APKs). versionCode 11 / versionName 1.7.0.
Ten real bills from five issuers across five addresses, plus eight bank
confirmations: every bill named correctly, **zero `adresa?`**, 7 of 8
confirmations auto-paired and the 8th correctly refused (its amount matches no
bill). Processing wrote **zero** files to the gallery. Export → wipe → import
restored deadlines, pairings, payee memory and the address book intact.
**Three defects the device found, all fixed here, all in `parser-core`:**
(1) **InfoStan never got a deadline** — a table layout the tests never modelled;
see the DueDateParserTest note below.
(2) **`MonthDetector` rule 5 looped over the CALENDAR, not the page.** It
returned the first month NAME that matched anywhere, so a July bill carrying a
back charge for May („заједничка електрична енергија – мај 2026", an ordinary
InfoStan line item) was named `..._maj26_...`. Now the earliest month IN THE
TEXT wins — every issuer prints the billing month in the header and older months
further down. Only reachable when the QR is unreadable, which is why `gms` named
that bill correctly and `foss` did not; the corpus case is
`infostan/back_charge_month_does_not_beat_header_month`.
(3) The `valut[ae]` widening from the previous round is now device-proven: an SZ
bill whose ONLY deadline label is „Датум валуте" reads its rok.
**Known, NOT fixed, `foss` only:** ZXing cannot decode the IPS QR on one
InfoStan bill that ML Kit reads (the same issuer whose QR `decodeThorough`
already targets). Consequence in F-Droid builds: that bill classifies UNKNOWN and
the intake asks „bill or confirmation?" once. Everything downstream is correct
after answering — the two flavors produce byte-identical names and totals.
Worth a look before claiming flavor parity on QR reading.
**Not proven on any device: the sr-Latn language fix.** The test phone is
`en-US`, so it opens in English — correct behaviour, but it means the
`b+sr+Latn` fix is verified only by `aapt2 dump configurations`, never by
behaviour. Needs a phone set to Serbian (Latin).

**Storage round, test pass 2026-08-14 (SM-S948B, release APK, gms).** Not
released, version NOT bumped. Tesseract in `gms` is DEVICE-PROVEN under R8 (D1):
the native libs load, `Initialized Tesseract API with language=srp+srp_latn+eng`,
init 88–117 ms, and a Cyrillic InfoStan screenshot yields ~4400 characters whose
address matches and whose deadline parses. OCR costs ~2.6 s per pass at 1080 px
and ~3.4 s at double that — the work tracks the TEXT FOUND, not the pixel count.
**OCR runs TWICE per image on purpose** — `classifyDocument` then
`processImageBill`. Left in deliberately (owner's call: *accuracy over speed*).
Do not "optimise" it by skipping OCR when an IPS QR is present: `classifyDocType`
returns BILL on the QR alone, so a bank confirmation that prints the paid QR
would classify as a bill. The only correct saving is to MEMOISE the text from the
classify pass and thread it through `processPicked` (the source uri differs from
the imported file's uri) — that changes nothing observable; skipping does.
**`Pipeline.scaledForOcr` — images are enlarged before OCR, by a WHOLE-NUMBER
factor.** `PdfOcr` has always rendered pages at >= 2000 px; plain images went in
at native size, so a phone screenshot of an A4 bill reached Tesseract at ~130 DPI
and the smallest rows never appeared in the output — which is why the deadline
read as absent while everything else was right. It was never a parser fault:
`datum dospec[ae]` is in `DueDateParser.LABELS` and `Normalizer` maps `ć`->`c`.
The factor MUST be an integer: `createScaledBitmap` is bilinear, and at 1.85x
(1080 -> exactly 2000) glyph edges fall between source pixels and smear, which
read WORSE than not scaling at all — it lost the `adresa:` anchor and named a
bill onto the wrong address. At 2x every source pixel maps to a whole block, the
anchor is found, and the same bill reads both its address and its deadline. Round
the factor UP; never aim at the target width exactly.
**`AddressMatcher` zone order: the property anchor now outranks the QR `P`
field.** `P` is the PAYER's postal address; the provider anchor (`adresa:` for
infostan, `mernog mesta` for eps) is the PROPERTY the bill is for, and for
InfoStan those are routinely two different addresses kept as two labels. With
only one of them in the book a bill named itself correctly; adding the other
silently moved it onto the postal one. The rule is the owner's: **always the
property address**. Providers with no anchor (mts, yettel, sz) are untouched —
there `P` IS the subscriber's address and stays the first zone. The rest of the
app already knew this: `buildBillCard` withholds the QR from the matcher on the
scan path for exactly this reason. Pinned by
`infostan/qr_payer_field_does_not_beat_property_anchor`; the pre-existing
`qr_anchor_zone_resolves_two_addresses` did NOT cover it because its `ips` has no
`P` field. Measured aside: an InfoStan IPS QR carries no `P` at all, so that
issuer depends entirely on the anchor — which is why the two fixes above are one
story. **Corpus 21 -> 22, tests 74 -> 76.**
UI: the deadline filter is a bell pill in the address-chip row (amber, red when
something is overdue) instead of a text link quieter than the banner that opened
it; `DueFilterNotice` is gone, the banner stays, and the pill is bills-only.
Translations stay 178 = 178 = 178 (`due_show_all` -> `filter_due`).

**Storage round step 1 — QR, DEVICE-PROVEN 2026-08-14 (SM-S948B, Android 16,
release APK, both flavors).** Not released, version NOT bumped.
Measured: cold derive from the document **~130–400 ms**, cache hit **0–4 ms**, and
with 14 cards on screen exactly **ONE** derive — the „open only when unpaid" rule
is what keeps the cost proportional to unpaid bills instead of list length.
Processing a bill wrote **zero** files to the gallery (1.6.2 wrote two).
Delete-on-pairing removed only the QR booked for THAT bill.
Three defects the device found, all fixed here:
(1) the share sheet could not read the FileProvider uri to draw its preview — the
grant rides on `ClipData`, not on the extra, so `startShare` now sets it;
(2) nothing wrote `qrImageUri` to the BILL row any more, so `markPaired` had
nothing to delete and Odluka 5 was silently dead — `Pipeline.recordSavedQr` books
it when the user saves, and `savedQrUris` drops the previous copy first;
(3) **foss/ZXing missed the InfoStan layout** that ML Kit reads (rebuilt instead
of read, on the same PDF). `QrDecoder.decodeThorough` (default = `decode`) is
overridden in the ZXing flavor with `TRY_HARDER` after a plain pass fails, and is
used by the ONE-SHOT call sites only — `QrExtractor` and the image intakes. The
live scanner keeps the fast path: it sees ~30 frames a second and most carry no
code at all. After the fix foss reads that bill in 224 ms vs ML Kit's 245 ms.
The rule: **the QR is derived, never stored.**
Three levels, nothing above the first by default — shown on the card (memory
only), „Podeli QR" (`cacheDir/share` + FileProvider, the system cleans it),
„U galeriju" (`Pictures/Racunko`, the user asked). The automatic `Gallery.save`
in `buildBillCard` and `generateQr` is GONE; `applyEdits` redoes a gallery copy
only when one already exists. `Gallery.kt` stays whole — a bank that never
appears in the share sheet needs it.
New `domain/QrCache.kt`: PNGs in `cacheDir/qr`, keyed by reference number (it
survives a rename) else file name, `.gen` in the file name recording whether the
code was REBUILT rather than read from the document. `Pipeline.qrFor` replaces
`qrBytesFor` and is cache-first; `deriveQr` reads the DOCUMENT first and rebuilds
only as a fallback, because a rebuilt payload is not the issuer's (payee name /
payment code / model are not reconstructed) — a fallback therefore raises
`qrGenerated`, so the verify-before-paying notice appears. It skips the document
for an already-generated QR: a `processScannedQr` artifact DOES contain a
decodable QR, and reading it back would silently drop that notice.
`MainViewModel.ensureQr` is lazy (card-visible + unpaid only) and **deliberately
does not persist** — the record holds no bitmap, and a source file that was
briefly unreachable must not mark a bill generated for good.
`showQr` = `!item.paired`, `remember`ed on (id, default) so pairing folds it.
Timing goes to logcat as `RacunkoQr qr source=cache|document|rebuilt|none ms=N`
— **no key, no file name**: a reference number identifies an account.
Test on a RELEASE APK (the regeneration path is ML Kit decoding, exactly what R8
broke in 1.6.1); `adb shell pm trim-caches 999999999999` clears `cacheDir/qr`
without touching the database, which is how the same bill is measured cold twice.
**`ignoreAssetsPatterns += "cmap"` SILENTLY BROKE PDF TEXT FOR THREE RELEASES —
reverted 2026-08-14. Never strip PDFBox's cmap assets.** v1.6.0 dropped them for
1.21 MB on the claim that "every one of them is CJK". There are 94, and two are
**Identity-H / Identity-V** — the encoding of every subset CID font, which is
what Serbian issuers' PDFs use (`FontFile2` + hex strings, zero literals).
The failure was not an exception and not empty text: PDFBox kept returning
~4500 characters of glyph soup, „ЈАВНО КОМУНАЛНО" as `jabho komyhanho`. That
sailed past `OcrPolicy.needsOcr` (which measures LENGTH), the `adresa:` anchor
was not found, the address matcher returned nothing, and `PayeeMemory.prefill`
filled the blank from the RECIPIENT ACCOUNT — one institutional account shared
by every InfoStan customer and flat, so what it returned was whichever address
was seen last. A bill was renamed onto someone else's address; the deadline
vanished the same way (label-anchored, no readable label).
**How it was caught, and the method worth reusing:** the user said the OLD
version read these bills. The PDFs were identical in structure (16 fonts, 2
`/ToUnicode`, ~420 hex strings) whether they had worked or not — so the change
was in the APP, not the input. Re-processing bills that had been correct BEFORE
v1.6.0 reproduced the failure; that is what turned "these fonts are broken" into
"we broke the reader". Verify with `zipfile` on the built APK: `cmap` entries
must be 92 and include Identity-H/V. Restored size: gms 35.9 → **37.1 MB**.
Do not attempt the saving again without excluding the CJK families BY NAME and
re-processing a CID-keyed bill to prove it.
**Kept as defence in depth**, since the guess is what turned a parsing failure
into a wrong file name: `Pipeline.buildBillCard` skips payee-memory prefill when
`textIsReadable(text)` is false — the same shape as the existing
`sourceKind == QR_ONLY` refusal, at the CALL SITE, not in parser-core.
`textIsReadable` wants ≥2 of `BILL_WORDS` in the NORMALIZED text (`Normalizer`
transliterates Cyrillic, so one list covers both scripts; glyph soup matches
none). It gates only the GUESS, never the reading — a false negative costs one
manual tap on the address, never a wrong name. Device-confirmed in both states:
with cmaps missing the card fell back to `adresa?` + „dopuni ručno" instead of a
wrong label; with cmaps restored all four test bills read their address straight
from the document (`sidro=true`, matcher non-empty).
**Note for a future round:** payee memory can never learn an address for an
issuer whose recipient account is institutional. The key that would work is the
InfoStan IDENT (`SpaceId.detect` reads it from the QR reference), but §5's
`ro_without_model_prefix` shows the IDENT is itself lost without the `118`
prefix. Moot while text extraction works; relevant if it ever degrades again.

**Unrelated defect found in passing and fixed:** Android drops an unescaped `"`
from a string resource, so every Serbian „…" pair in `strings.xml` was rendering
without its closing quote (5 strings in `values`, 2 in `values-en`). Fixed by
using the real closing quotes („…” and “…”) rather than escaping — `values-ru`
was already correct because it uses «…».
Repo `github.com/pavlelausevic/racunko-android`, branch main. From here every
round is a point release (1.5.1, 1.5.2 …); 1.6.0 is the first MINOR bump —
nothing a user depends on broke, so it is not 2.0. Reserve 2.0 for the JSON
fixture corpus / a consumable `:parser-core` artifact / real system
notifications.

**v1.6.2 executed 2026-08-13** — second visual pass + the corpus migration.
THE RULE that drives the repaint: **colour marks state, never category.** The
old palette gave each filename segment its own hue (gold provider / teal address
/ lime month / emerald amount) on green-tinted surfaces — legible once explained,
busy otherwise. Now: neutral near-black surfaces, teal the ONLY accent, and just
two things marked, both the same way — a field we could not prove (amber,
underlined) and a value from payee memory (underlined). `Palette.Violet` is GONE;
its 5 usages moved to amber/teal. Card leads with provider + amount as the hero,
address · month muted under it, file name once and quiet; every field still taps
to edit. `Seg` is plain text, not a bordered chip. SummaryCard leads with ONE
number. Proportional font everywhere EXCEPT machine values (accounts, references,
QR payloads, filename tokens in Settings) — that split is deliberate.
**`themes.xml` hardcodes windowBackground/statusBarColor/navigationBarColor and
must be kept equal to `Palette.Bg`** — it paints before Compose starts, so a
stale value shows as a tinted strip that no repaint fixes (cost me one device
pass to find).
**Dialogs now share one language**: `DialogChoices` (one row, two EQUAL halves,
action filled / exit outlined), `DialogCheck` (the same teal disc the lists use,
centred with its label), `DialogText` (sets its own alignment from `onTextLayout`
— one line centred, more justified). Applied to intake, delete, and both purge
steps. On UNKNOWN the intake dialog leads with **Potvrda**, because bills come
from five known issuers while confirmations come from every bank.
**Two real bugs found only on the device:** (1) an unreadable card was a dead end
— not selectable (`selectable = card.status != ERROR`) and it `return@Column`-ed
before any action, so it could not be removed from the app at all; it now carries
„Ukloni ovu karticu" + `MainViewModel.selectOnly`. (2) the Potvrde „+" jumped
straight to the file picker, so the GALLERY was unreachable there; both tabs now
open the same menu, scan stays bills-only (live QR always yields a bill).
Section totals sum only amounts that were READ (`?: 0` claimed „this section
costs 0"); messages no longer say „PDF" when the file is an image.
versionCode 10 / versionName 1.6.2, `## [1.6.2]` in CHANGELOG.

**v1.6.0 executed 2026-08-12** — full visual redesign + deadlines. UI: Theme.kt
repainted (deep green + turquoise; the names in `Palette` deliberately KEPT —
`Amber`→gold, `Blue`→turquoise, `Violet`→lemon, `Green`→emerald; `Palette.Dot`
stays orange as the one mark not adopted from the reference design), `RIcons`
(20 Material Symbols drawn in code, NO material-icons-extended, zero emoji), the
shared sheet building blocks in App.kt (SheetTitle/FieldLabel/SheetRow/ToggleRow/FlowRowChips).
List: summary card, address filter chips (>1 address), folding per-address
sections that START FOLDED when >1 address and open under a filter, no-address
group FIRST, cards ordered `BY_MONTH_DESC` (newest first, unreadable month
last). Select mode: no checkboxes, long-press enters, „Izaberi sve" scoped to
what the filter shows (file-list checkboxes stay — that is a file picker).
parser-core: `DueDateParser` (label-anchored ONLY, never the issue date; 6
tests both directions), `Padding` + rewritten `Report` (columns spaced by
Roboto glyph WIDTH so the shared summary aligns in a PROPORTIONAL font —
measured spread 22px→6px; the in-app preview is therefore NOT monospace).
Room v4 (3→4): dueDateEpochDay, remindEnabled, remindDaysBefore, remindHour,
remindMinute. Reminder is per bill, shown as an on-open banner; the chosen TIME
is stored but INERT until system notifications land (the sheet says so).
`values-ru` complete (4 Russian plural forms); month tokens in FILE NAMES stay
Serbian. Settings: „sz" row labelled `provider_label_sz`, „Moji pružaoci"
section HIDDEN (state still loaded+saved, recoverable from git).
**Share-into fix (the one that took three attempts):** `taskAffinity=""` on
MainActivity nullified singleTask — but removing it was NOT enough, because the
SENDER's launch flags still decide placement. The SEND filters now live on
`ShareTargetActivity`, an invisible trampoline (`taskAffinity=""` is correct
THERE and only there, + excludeFromRecents + noHistory) which pulls our task
forward via `ActivityManager.AppTask.moveToFront()` (addresses the task by ID,
no affinity matching) and then starts MainActivity itself; uris are re-granted
through ClipData because extras carry no permission. CONFIRMED on device.
Size: 103.5 MB → 62.4 MB via ABI filter (arm64+v7a; x86 was 35 MB of
emulator-only weight), BouncyCastle pqc `.properties` excluded (4.15 MB,
resources so R8 never touches them), PDFBox CJK CMaps excluded (1.2 MB, 92
files, all CJK; PdfText failure already falls back to OCR), locale filter
sr/en/ru, `ui-tooling-preview` dropped (no @Preview anywhere).
**R8 is ON and device-proven** (Android 16, arm64): gms 62.4 -> 35.9 MB,
foss 49.2 -> 23.7 MB. The first attempt installed and would not launch; the
cause was NOT any of the usual suspects (Room `_Impl`, ViewModel ctors,
androidx.startup, manifest themes all survived by name). Retraced stack:
`RacunkoApp.onCreate -> EngineFactory.create -> MlKitQrDecoder.<init> ->
BarcodeScanning.getClient() -> NPE`. ML Kit discovers components by class NAME
from manifest meta-data, builds them via a no-arg ctor, and keys them in a
registry BY CLASS OBJECT — R8 both drops reflective-only ctors and MERGES
classes, and merging collapses two registry keys into one so the lookup returns
null. Fixed by keeping the vision surface whole (`com.google.mlkit.vision.**`,
`common.internal.**`, `com.google.android.gms.internal.mlkit_**`) plus
`-keep class * implements ComponentRegistrar { <init>(); }` — the registrars
ship with `-keepnames`, which stops renaming but NOT removal. Cost of those
keeps: +0.8 MB. Do not trim the ML Kit block without a device pass.
Verified on device under R8: card list from Room, address sections, QR render
(PdfRenderer + ML Kit decode + ZXing encode), Settings + DataStore. Zero
ClassNotFound/NoSuchMethod/VerifyError in logcat.
Docs: РУКОВОДСТВО.md added (full Russian manual), README gained a 🇷🇺 section,
UPUTSTVO/MANUAL gained the deadline + list-ordering + report sections.

**PUBLIC RELEASE 2026-08-02**: history intentionally SQUASHED to one clean root
commit for open-sourcing (all personal data in tests/docs replaced by fictional,
checksum-valid samples — never reintroduce real names/addresses/subscriber ids).
v1.5.2 device pass was CONFIRMED on the phone before this. Tag `v1.5.2` now
points at the clean root; older tags were deleted with the old history. Git
identity for this repo: `pavle@lausevic.com`. Signing secrets moved to
git-ignored `keystore.properties` (storeFile/storePassword/keyAlias/keyPassword).
New docs at root: UPUTSTVO.md (sr) + MANUAL.md (en) user manuals, llms.txt for
AI discoverability, bilingual README.

**v1.5.2 executed 2026-07-06** (type/tab guard + multi-space): parser-core:
`classifyDocType` on TemplateRegistry (DocumentTemplate gained `docType`,
confirmation adapters override; BILL = bill template or IPS QR, CONFIRMATION =
bank template, else UNKNOWN + keyword lean; QR absence NEVER implies
confirmation) + `IntakeGuard.decide` (PROCEED/WARN_SUGGEST_*/ASK_TYPE, one
shared path for manual add AND share-into); `SpaceId` (InfoStan IDENT: RO
`^118(\d{8})\d{3}-`, text label fallback, canonical = zeros stripped),
`SpaceBinding`+`SpaceNaming` (ADDRESS-SUB token, subFor by spaceId, `collides`
replaces silent `_2` for bills). Tests: ClassifyDocTypeTest 5 + SpaceNamingTest
5, all 76 green. App: VM `intake()` (classify → route/warn/ask; pickers +
photo picker + onSharedIn all use it; ShareTypeDialog → IntakeDialog),
buildBillCard takes bindings → sub-label auto-applies; collision sets
`needsSpaceTag` → SpaceTagPrompt on the card (chips G1/G2/STAN/LOKAL,
„Zapamti za ovaj prostor" binds spaceId in DataStore `space_bindings`).
versionCode 7 / versionName 1.5.2, `## [1.5.2]` in CHANGELOG.
Expected surprises on device (maintainer notes): an unknown bank's
confirmation may classify UNKNOWN → type question — that means it needs a
template (get a redacted sample, follow CONTRIBUTING.md); two spaces of an
IDENT-less provider fall back to the manual tag without auto-binding.

**v1.5.1 executed 2026-07-06**
(post-1.5.0 device pass): address NEVER guessed (blank-pattern guard in
AddressMatcher + NeverGuessAddressTest pins one-entry-book → empty; default
address seed now EMPTY so the CTA can show), already-processed files blocked in
the folder list („već obrađen" tag + disabled checkbox, reconciled against card
records; dismissed files stay selectable so re-processing revives them),
„Napravi šifarnik" CTA on an empty book, generated-QR banner auto-dismisses
(~4 s / tap), „Potraži u Download" REMOVED (scanDownloads/DiscoverSheet/
isBillCandidate deleted; registry.looksLikeBill stays in parser-core), and the
BLOCKER: Settings moved ModalBottomSheet → FULL SCREEN in the activity window
(sheet's dialog window swallowed IME insets twice) with imePadding + scroll +
BringIntoViewRequester on focus + Done/Back closing the IME. versionCode 6 /
versionName 1.5.1, `## [1.5.1]` in CHANGELOG. Device gate BEFORE pushing tags:
keyboard/Settings is the FIRST thing to confirm on the phone (touch indicator
on, as in the recording); if it fails again, report exact behavior — insets
then need debugging in the activity window itself, not the sheet. (Device pass
later CONFIRMED; the era's tags were removed with the pre-public history — see
the PUBLIC RELEASE note above.)

## Fixture corpus (v1.6.2 — the portability argument)
`parser-core/src/test/fixtures/{issuer}/{case}.{txt,expected.json}`, run by
`FixtureTest`. **22 cases** over eps/infostan/mts/sz/yettel/uplatnica plus three
issuer-less buckets: `address/` (matcher boundaries + never-guess), `confirmation/`
(bank receipts, classification only), `unknown/` (must NOT be offered as bills).
KEY FINDING that shaped it: the app has TWO extraction paths and they are
different code — `Pipeline.buildBillCard` uses the DIRECT functions
(ProviderDetector → AmountParser → MonthDetector → AddressMatcher → BillName) and
that is what makes the FILE NAME; `registry.extract` is used only for
classification and for the account on a QR-less bill. The old corpus tested only
the registry, i.e. not the user-visible result. FixtureTest runs BOTH per case
and pins `expectedName`. Schema: every key optional except `sourceKind`, asserted
only if present, and an UNKNOWN key fails (a typo cannot silently disable an
assertion). Fixtures are wired as a test **resources srcDir** in
`parser-core/build.gradle.kts` — that is the ONLY reason Gradle re-runs `test`
when a fixture changes; remove it and edits are silently not verified.
**Schema grew in this round:** `addressBook` (compact one-line book, so a case
about the BOOK — one entry, blank pattern — declares its own instead of using
`SampleAddresses`), `looksLikeBill`, `docType`/`docTypeConfidence`/`docTypeLean`.
`spaceId` was REPOINTED from `registry.extract` to `SpaceId.detect` — the
registry's field of that name is null on any bill carrying a QR, so asserting it
pinned nothing about the file name. No shipped fixture used the old meaning.
**Migrated this round:** ParserUnitTest (only its 2 address-boundary tests were
document-shaped), InfostanMonthTest (all 3), LooksLikeBillTest (all 3),
ClassifyDocTypeTest (classification → corpus; the guard is now an EXHAUSTIVE
decision table over type × intent, stronger than the 5 document-coupled cases it
replaced), NeverGuessAddressTest (3 of 4; the 4th folded into PayeeMemoryTest,
which gained the stronger "lookup is not even attempted" assertion).
**Still in Kotlin, by the documented split:** algorithm (rounding,
transliteration, filename regex, checksum, report layout, due-date labels,
BillName parse, QR round-trip), pairing (needs OTHER bills as context), intake
routing (a decision table, no document), scan-path wiring (`ScanAddressTest` —
which arguments the call site passes is about this app, not the format), and
space naming (`SpaceNamingTest` — sub-label/collision policy; only its
`SpaceId.detect` half is a document claim and that is now corpus).
The dividing question, in TESTING.md: **could a port run this case with no Kotlin
present?**

## Tests (parser-core) — keep green
AcceptanceTest (§10 1–8, uses SampleAddresses — its remaining 6 are all pairing),
AccountChecksumTest, IpsQrRoundTripTest, RegistryTest, FalsePositiveTest,
PayeeMemoryTest (+ „lookup not attempted on an unproven account"),
FixtureTest (fixtures/**/*.expected.json), BillNameParseTest,
ParserUnitTest (algorithm + pairing only, after the corpus migration),
ClassifyDocTypeTest (v1.5.2 A: now the EXHAUSTIVE intake decision table),
ScanAddressTest (the QR is withheld from the address matcher on the scan path),
SpaceNamingTest (v1.5.2 B: IDENT sub-labels, collision flagged not _2),
DueDateParserTest (v1.6: label-anchored only, issue date never a deadline) with
per-issuer LAYOUT cases for MTS/EPS/InfoStan/SBB-Yettel/Yettel — each pinning the
decoy that must NOT win (complaint deadline, contract expiry, discount cut-off,
the EPS slip's „Валута" currency column). Real bills confirmed the label wording
locally and never left the maintainer's machine; the committed text is synthetic.
**v1.7 — that per-issuer set proved the LABEL WORDING, not the LAYOUT, and the
difference cost InfoStan its deadline for three releases.** The old case wrote
`Датум доспећа: 31.08.2026.` on one line; the real bill is a four-column table
with the headings in one row and the values in the next, so the bill NUMBER sits
between the label and its date. Device-found 14.08.2026, fixed by a second pass
(see DueDateParser) and pinned by `infostan_tableLayout_dueDateIsInTheValueRow`
+ `tableFallbackNeverBeatsAnAdjacentLabel`. **When a synthetic case stands in for
a real one, say which property it pins.**
ReportTest (v1.6: amounts align by WIDTH not char count; spacer never overshoots
and never emits two ASCII spaces in a row).
**79 green + 23 fixture cases** (was 86 + 8; the drop is migration, not loss —
every removed Kotlin test is a corpus case, and the corpus grew by 15).
README carries the count in a badge and in the build snippet — update BOTH.
UI has no JVM proof → device pass. A PR adding a template needs a fixture; never
weaken a checksum/false-positive assertion.
