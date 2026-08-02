# Računko

**[Srpski](#-srpski)** · **[English](#-english)**

![Platform](https://img.shields.io/badge/platform-Android%2010%2B-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Privacy](https://img.shields.io/badge/internet-NEMA%20DOZVOLU-success)
![Tests](https://img.shields.io/badge/parser--core-76%20testova-brightgreen)

---

## 🇷🇸 Srpski

**Računko sređuje tvoje komunalne račune.** Android aplikacija koja čita PDF
račune i papirne uplatnice (InfoStan, EPS, MTS, Yettel, stambena zajednica…),
sama ih **preimenuje** po pružaocu/adresi/mesecu/iznosu, **izvlači ili pravi
NBS IPS QR kod** za plaćanje jednim skenom iz aplikacije banke, i **uparuje
potvrde o uplati** sa računima — da uvek znaš šta je plaćeno, a šta nije.

**Privatnost pre svega: aplikacija NEMA dozvolu za internet.** Sve — čitanje
PDF-a, skeniranje QR koda, OCR — radi isključivo na telefonu. Ništa nikada ne
napušta uređaj. Detalji: [PRIVACY.md](PRIVACY.md).

### 📲 Za obične korisnike — kako da dođeš do aplikacije

Ne moraš da znaš ništa o GitHub-u niti o programiranju:

1. Otvori stranicu **[Releases](../../releases/latest)** (dugme „Releases" desno
   na ovoj stranici).
2. Preuzmi fajl **`Racunko-X.Y.Z.apk`** na telefon (~99 MB — u sebi nosi sve
   modele za čitanje, zato i radi bez interneta).
3. Otvori preuzeti fajl i instaliraj (Android će pitati da dozvoliš instalaciju
   iz ovog izvora — potvrdi).
4. Pri prvom pokretanju potvrdi fasciklu `Download/Racunko` jednim dodirom — to
   je sve od podešavanja.

📖 **Kompletno uputstvo za korišćenje: [UPUTSTVO.md](UPUTSTVO.md)**

> **Napomena:** Računko je **Android** aplikacija — ne postoji verzija za
> Windows (.exe) niti za iPhone. Sve što ti treba je Android telefon (Android
> 10 ili noviji).

### Šta radi

- **Prepoznavanje računa** — PDF, fotografija ili papir pod kamerom; podaci se
  čitaju iz IPS QR koda i teksta, nikad se ne pogađaju: kad nešto nije sigurno,
  aplikacija pita tebe.
- **Pametna imena fajlova** — `infostan_KD7_maj26_11152.pdf` umesto
  `Racun-4482913.pdf`: pružalac, tvoja skraćenica adrese, mesec, iznos.
- **QR za plaćanje** — postojeći IPS QR se iseca iz računa; računu bez QR koda
  (papirna uplatnica) aplikacija **sama pravi ispravan kod**, ali samo kada je
  broj računa primaoca matematički proveren (kontrolna cifra, model 97).
- **Potvrde o uplati** — podeli potvrdu iz aplikacije banke u Računko; uparuje
  se sa računom automatski (poziv na broj → račun+iznos → ručno).
- **Više prostora na istoj adresi** — stan + garaža ne mešaju se u imenima
  (`SG26-G1`), pamti se po šifri korisnika.
- **Izveštaj** — zbirni pregled po mesecu i adresi, za kopiranje/deljenje.

### Podržane banke (šabloni potvrda)

Banca Intesa, Erste, AIK + generički šablon. Tvoja banka nije podržana?
[Otvori issue](../../issues) sa **anonimizovanim** uzorkom potvrde — dodavanje
šablona je mali posao ([ADDING_A_TEMPLATE.md](ADDING_A_TEMPLATE.md)).

### Za programere

Dve varijante, obe 100% offline: `gms` (ML Kit + ZXing) i `foss`
(ZXing + Tesseract — bez Google servisa, pogodno za F-Droid).

```
set JAVA_HOME=<JDK 17+ ili Android Studio jbr>
gradlew.bat :parser-core:test                              # svih 76 testova mora biti zeleno
gradlew.bat :app:assembleGmsDebug :app:assembleFossDebug   # oba flavora moraju da se builduju
gradlew.bat :app:assembleGmsRelease                        # potpisan APK (vidi ispod)
```

- Logika parsiranja je čist JVM modul **`:parser-core`** (nula Android
  zavisnosti) sa plugin registrom šablona — nova banka = jedna klasa + fixture
  test, bez emulatora.
- **Potpisivanje:** napravi svoj keystore i `keystore.properties` u korenu
  (git-ignorisan): `storeFile=`, `storePassword=`, `keyAlias=`, `keyPassword=`.
  Bez njega release pada na debug potpis (i dalje instalabilan za testiranje).
- Mapa koda: [ARCHITECTURE.md](ARCHITECTURE.md) ·
  Doprinosi: [CONTRIBUTING.md](CONTRIBUTING.md) ·
  Testovi: [TESTING.md](TESTING.md) ·
  Istorija: [CHANGELOG.md](CHANGELOG.md)

---

## 🇬🇧 English

**Računko (“Bill-o”) organizes Serbian utility bills.** An Android app that
reads PDF bills and paper payment slips (InfoStan, EPS, MTS, Yettel, building
associations…), **renames** them by provider/address/month/amount, **extracts
or generates the NBS IPS QR code** so you pay with one scan from your banking
app, and **pairs bank payment confirmations** back to bills — so you always
know what is paid and what is not.

**Privacy first: the app has NO internet permission.** Everything — PDF
parsing, QR scanning, OCR — runs entirely on the phone. Nothing ever leaves
the device. Details: [PRIVACY.md](PRIVACY.md).

### 📲 For regular users — getting the app

No GitHub or programming knowledge needed:

1. Open the **[Releases](../../releases/latest)** page.
2. Download **`Racunko-X.Y.Z.apk`** to your phone (~99 MB — it bundles all
   recognition models, which is why it works offline).
3. Open the downloaded file and install (Android will ask you to allow
   installs from this source — confirm).
4. On first launch, confirm the `Download/Racunko` folder with one tap — that
   is the entire setup.

📖 **Full user manual: [MANUAL.md](MANUAL.md)**

> **Note:** Računko is an **Android** app — there is no Windows (.exe) or
> iPhone version. All you need is an Android phone (Android 10+).

### Features

- **Deterministic parsing** — values come from the decoded IPS QR,
  label-anchored templates and **checksum-verified** account numbers; when a
  value cannot be *proven*, the app asks the user instead of guessing. No AI,
  no cloud.
- **Smart file names** — `infostan_KD7_maj26_11152.pdf` instead of
  `Racun-4482913.pdf` (provider, your address label, month, amount).
- **Payment QR** — extracted from the bill, or **generated** for QR-less paper
  slips (only when the recipient account passes the MOD 97-10 checksum).
- **Confirmation pairing** — share a payment confirmation from your banking
  app into Računko; it pairs to the bill automatically (payment reference →
  account+amount → manual).
- **Multiple units at one address** (flat + garage) and a monthly **report**.
- Two flavors, both fully offline: `gms` (ML Kit + ZXing) and `foss`
  (ZXing + Tesseract, Google-free, F-Droid-friendly).

### Building & contributing

The parsing brain is a pure-JVM module **`:parser-core`** (zero Android
dependencies) with a plugin template registry — a new bank = one class + a
fixture test, no emulator. See [ARCHITECTURE.md](ARCHITECTURE.md),
[CONTRIBUTING.md](CONTRIBUTING.md), [TESTING.md](TESTING.md),
[ADDING_A_TEMPLATE.md](ADDING_A_TEMPLATE.md). Signing uses a git-ignored
`keystore.properties` (see the Serbian section for keys). PRs welcome —
especially new bank templates with **redacted** samples.

### Keywords

Serbian utility bills · komunalni računi · NBS IPS QR · uplatnica · payment
slip · InfoStan · EPS · Infostan bill renamer · QR plaćanje · poziv na broj
model 97 · bank confirmation pairing · potvrda o uplati · offline OCR ·
privacy-first Android · no internet permission · Jetpack Compose · Kotlin ·
ML Kit · ZXing · Tesseract · F-Droid

---

**Licenca / License:** [Apache-2.0](LICENSE) ·
**Kontakt / Contact:** pavle@lausevic.com ·
**Prijava problema / Issues:** [GitHub Issues](../../issues)
