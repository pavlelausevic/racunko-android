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
.\gradlew.bat :app:assembleGmsRelease            # signed APK (keystore in repo root)
.\gradlew.bat :parser-core:run --args="mkaccount 190 99870"    # → valid 18-digit account
```
Signed release APKs are ~62 MB gms / ~48 MB foss (bundled ML Kit / Tesseract
models; see the size notes in app/build.gradle.kts). Keep APKs,
`prezentacija/`, `.claude/`, tessdata out of git (already in `.gitignore`).

## Modules (dependency rule: :app → :parser-core + :platform-api)
- **:parser-core** — pure Kotlin/JVM, ZERO Android imports. The brain + ALL unit
  tests. Verify purity: `grep -r "import android" parser-core/src/main` = empty.
- **:platform-api** — Android-library; device interfaces only (QrDecoder,
  QrEncoder, TextRecognizer, LiveQrScanner, PlatformEngines, Engines holder,
  DefaultLiveQrScanner 3-frame IPS debounce). No concrete engine here.
- **:app** — Compose UI + domain + data + flavor engine impls.

## Flavors (engine swap, compile-time)
- `gms` (Play): ML Kit barcode+text (bundled, offline) + ZXing encode.
- `foss` (F-Droid): ZXing decode+encode + Tesseract (tessdata_fast bundled at
  build time by `fetchTessdata` task → git-ignored; zero runtime network).
Engine impls live ONLY in `app/src/{gms,foss}/java/com/racunko/app/engine/`.
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
- `domain/{QrExtractor,PdfText,PdfOcr,ScanArtifact}.kt`.
- `data/{Db(Room),Storage(SAF-only: SafStore+StorageManager+NoopStore),Saf
  (ensureFolders→Racunko/Racuni/Potvrde),Gallery(QR→SAF tree+Pictures publish),
  Settings(DataStore)}.kt`.

## Conventions
- Filename: `{provider}_{ADDR}_{monthYY}_{amount}.pdf`; confirmation `uplata_`+name;
  QR `{name}_QR.png`. Month token always Serbian lowercase.
- Storage (v1.5.0-rc3): SINGLE model = SAF. One persisted OPEN_DOCUMENT_TREE grant
  on **`Download/Racunko`** (Android 11+ GREYS OUT the grant on the Downloads ROOT,
  so onboarding EXTRA_INITIAL_URI = `primary:Download/Racunko`; ensureFolders treats
  a granted folder named Racunko as the container, no double-nest → makes
  Racuni/Potvrde). rc3: `StorageManager.ensurePublicFolder()` MediaStore-creates
  Download/Racunko BEFORE onboarding (minSdk 29, no perm, idempotent readme) so the
  grant dialog lands ENABLED = ONE TAP; VM init calls it when no tree bound. QR PNG in the SAF `Racunko` container + a gallery copy published
  to `Pictures/Racunko` via MediaStore.Images (the ONLY remaining MediaStore use).
  Adding files from the Downloads ROOT = „Dodaj iz fajla" = ACTION_OPEN_DOCUMENT
  system picker (NO permission, Play-safe); NEVER a tree grant on the root.
  First run → OnboardingScreen (+ ⓘ info popup) → grant. Manual drop into
  Racunko/Racuni auto-detected on start/resume/refresh. „Potraži" scans the granted
  Racunko tree. Edge-to-edge (setDecorFitsSystemWindows(false)) so IME insets work.
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
`FixtureTest`. **21 cases** over eps/infostan/mts/sz/yettel/uplatnica plus three
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
ReportTest (v1.6: amounts align by WIDTH not char count; spacer never overshoots
and never emits two ASCII spaces in a row).
**74 green + 21 fixture cases** (was 86 + 8; the drop is migration, not loss —
every removed Kotlin test is a corpus case, and the corpus grew by 13).
README carries the count in a badge and in the build snippet — update BOTH.
UI has no JVM proof → device pass. A PR adding a template needs a fixture; never
weaken a checksum/false-positive assertion.
