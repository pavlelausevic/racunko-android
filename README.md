# Računko

**[Srpski](#-srpski)** · **[English](#-english)** · **[Русский](#-русский)**

![Platform](https://img.shields.io/badge/platform-Android%2010%2B-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)
![Privacy](https://img.shields.io/badge/internet-NEMA%20DOZVOLU-success)
![Tests](https://img.shields.io/badge/parser--core-74%20testa%20%2B%2021%20fikstura-brightgreen)
![Languages](https://img.shields.io/badge/jezici-sr%20%C2%B7%20en%20%C2%B7%20ru-blueviolet)

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
2. Preuzmi fajl **`Racunko-X.Y.Z.apk`** na telefon (~36 MB — u sebi nosi sve
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
- **Rok plaćanja i podsetnik** — rok se čita **samo sa svoje oznake** na računu
  (nikad iz datuma izdavanja); po računu biraš koliko dana ranije da te
  podseti, a pri otvaranju aplikacije stoji traka sa onim što stiže.
- **Sređeno po adresama** — zbir za plaćanje na vrhu, čipovi za filtriranje,
  sekcije koje se sklapaju, računi poređani po mesecu na koji se odnose.
- **Izveštaj** — zbirni pregled po mesecu i adresi, poravnat tako da iznosi
  stoje jedan ispod drugog i kad ga nalepiš u Viber ili WhatsApp.
- **Tri jezika** — srpski, engleski, ruski.

### Podržane banke (šabloni potvrda)

Banca Intesa, Erste, AIK + generički šablon. Tvoja banka nije podržana?
[Otvori issue](../../issues) sa **anonimizovanim** uzorkom potvrde — dodavanje
šablona je mali posao ([ADDING_A_TEMPLATE.md](ADDING_A_TEMPLATE.md)).

### Za programere

Dve varijante, obe 100% offline: `gms` (ML Kit + ZXing) i `foss`
(ZXing + Tesseract — bez Google servisa, pogodno za F-Droid).

```
set JAVA_HOME=<JDK 17+ ili Android Studio jbr>
gradlew.bat :parser-core:test                              # 74 testa + 21 fikstura, sve zeleno
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
2. Download **`Racunko-X.Y.Z.apk`** to your phone (~36 MB — it bundles all
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
- **Multiple units at one address** (flat + garage), grouped per address with
  a to-pay total, filter chips and folding sections, ordered by the month each
  bill is *for*.
- **Deadline and reminder** — the due date is read **only from its own label**
  on the bill, never from the issue date; per bill you choose how many days
  ahead to be reminded, and a banner greets you with what is coming up.
- **Report** — a monthly per-address summary, spaced so the amounts still line
  up after you paste it into Viber or WhatsApp.
- **Three languages** — Serbian, English, Russian.
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

---

## 🇷🇺 Русский

**Računko приводит в порядок коммунальные счета.** Приложение для Android
читает PDF-счета и бумажные платёжные квитанции (InfoStan, EPS, MTS, Yettel,
жилищные товарищества…), само **переименовывает** их по поставщику, адресу,
месяцу и сумме, **извлекает или создаёт QR-код NBS IPS** для оплаты одним
сканированием в банковском приложении и **сопоставляет подтверждения об
оплате** со счетами — чтобы всегда было видно, что оплачено, а что нет.

**Приватность прежде всего: у приложения НЕТ разрешения на интернет.** Всё —
чтение PDF, сканирование QR-кода, распознавание текста — происходит
исключительно на телефоне. Данные никогда не покидают устройство. Подробнее:
[PRIVACY.md](PRIVACY.md).

> **Для кого это.** Приложение рассчитано на счета **сербских** поставщиков и
> на сербский стандарт платёжного QR-кода (NBS IPS). Интерфейс полностью
> переведён на русский, но сами счета должны быть сербскими.

### 📲 Как получить приложение

Знания GitHub или программирования не нужны:

1. Откройте страницу **[Releases](../../releases/latest)**.
2. Скачайте на телефон файл **`Racunko-X.Y.Z.apk`** (~36 МБ — внутри все
   модели распознавания, поэтому приложение работает без интернета).
3. Откройте скачанный файл и установите (Android попросит разрешить установку
   из этого источника — подтвердите).
4. При первом запуске одним касанием подтвердите папку `Download/Racunko` —
   на этом настройка закончена.
5. Язык переключается в **Настройках → Язык → Русский**.

📖 **Полное руководство: [РУКОВОДСТВО.md](%D0%A0%D0%A3%D0%9A%D0%9E%D0%92%D0%9E%D0%94%D0%A1%D0%A2%D0%92%D0%9E.md)**

> **Примечание:** Računko — приложение **только для Android** (10 и новее).
> Версий для Windows (.exe) и iPhone не существует.

### Возможности

- **Никаких догадок** — значения берутся из расшифрованного QR-кода IPS, из
  шаблонов, привязанных к подписям на счёте, и из номеров счетов, **проверенных
  контрольной суммой** (МОД 97-10). Если значение нельзя доказать, приложение
  спрашивает вас. Ни ИИ, ни облака.
- **Понятные имена файлов** — `infostan_KD7_maj26_11152.pdf` вместо
  `Racun-4482913.pdf`: поставщик, ваше сокращение адреса, месяц, сумма.
  Названия месяцев в именах файлов остаются сербскими — это правило
  именования, а не перевод.
- **QR для оплаты** — извлекается из счёта, а для бумажной квитанции без
  QR-кода **создаётся заново**, но только если номер счёта получателя прошёл
  проверку контрольной суммой.
- **Сопоставление подтверждений** — поделитесь подтверждением из банковского
  приложения в Računko, и оно само привяжется к счёту.
- **Срок оплаты и напоминание** — срок читается **только по его собственной
  подписи** на счёте, никогда из даты выставления.
- **Группировка по адресам**, сумма к оплате сверху, сортировка по месяцу, за
  который выставлен счёт.
- **Отчёт** — сводка по месяцу и адресу; колонки выровнены так, что суммы
  остаются друг под другом даже после вставки в Viber или WhatsApp.

### Поддерживаемые банки (шаблоны подтверждений)

Banca Intesa, Erste, AIK и общий шаблон. Вашего банка нет?
[Откройте issue](../../issues) с **обезличенным** образцом подтверждения —
добавить шаблон несложно ([ADDING_A_TEMPLATE.md](ADDING_A_TEMPLATE.md)).

### Разработчикам

Ядро разбора — чистый JVM-модуль **`:parser-core`** без единой зависимости от
Android, с реестром шаблонов: новый банк = один класс плюс тест с фикстурой,
без эмулятора. См. [ARCHITECTURE.md](ARCHITECTURE.md),
[CONTRIBUTING.md](CONTRIBUTING.md), [TESTING.md](TESTING.md).

---

### Keywords

Serbian utility bills · komunalni računi · NBS IPS QR · uplatnica · payment
slip · InfoStan · EPS · Infostan bill renamer · QR plaćanje · poziv na broj
model 97 · bank confirmation pairing · potvrda o uplati · offline OCR ·
privacy-first Android · no internet permission · Jetpack Compose · Kotlin ·
ML Kit · ZXing · Tesseract · F-Droid · коммунальные счета Сербии ·
QR-код NBS IPS · офлайн распознавание · приложение без интернета

---

**Licenca / License:** [Apache-2.0](LICENSE) ·
**Kontakt / Contact:** pavle@lausevic.com ·
**Prijava problema / Issues:** [GitHub Issues](../../issues)
