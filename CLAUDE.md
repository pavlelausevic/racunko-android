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

**v1.6.0 executed 2026-08-12** — mani-form redesign + deadlines. UI: Theme.kt
repainted (deep green + turquoise; the names in `Palette` deliberately KEPT —
`Amber`→gold, `Blue`→turquoise, `Violet`→lemon, `Green`→emerald; `Palette.Dot`
stays orange as the one mark not borrowed from mani), `RIcons` (20 Material
Symbols drawn in code, NO material-icons-extended, zero emoji), mani building
blocks in App.kt (SheetTitle/FieldLabel/SheetRow/ToggleRow/FlowRowChips).
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
**R8 is OFF.** Turning it on gave 35.1 MB but the APK installed and would not
launch. Keep rules are written in `app/proguard-rules.pro` and the obvious
suspects were CLEARED by inspecting mapping.txt (Room `_Impl`, ViewModel ctors,
ML Kit ComponentRegistrars, androidx.startup Initializers, manifest themes all
survive by name). Re-enabling needs a logcat from the failing build — or try
`-dontobfuscate` first, since a name-based lookup is the likeliest cause.
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

## Tests (parser-core) — keep green
AcceptanceTest (§10 1–8, uses SampleAddresses), AccountChecksumTest,
IpsQrRoundTripTest, RegistryTest, FalsePositiveTest, PayeeMemoryTest,
FixtureTest (fixtures/**/*.expected.json), InfostanMonthTest, ReportTest,
NeverGuessAddressTest (v1.5.1: one-entry book + non-matching doc → empty label),
ClassifyDocTypeTest (v1.5.2 A: mismatch guard incl. QR-less SZ no-nag),
SpaceNamingTest (v1.5.2 B: IDENT sub-labels, collision flagged not _2),
DueDateParserTest (v1.6: label-anchored only, issue date never a deadline),
ReportTest (v1.6: amounts align by WIDTH not char count; spacer never
overshoots and never emits two ASCII spaces in a row). 86 green.
UI has no JVM proof → device pass. A PR adding a template needs a fixture; never
weaken a checksum/false-positive assertion.
