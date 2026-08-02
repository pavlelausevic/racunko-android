# Changelog

## [1.5.2] — 2026-07-06

### Novo
- **Zaštita od pogrešnog taba.** Kad dodaš fajl, Računko ga prvo prepozna:
  očigledna potvrda ubačena u Račune dobija pitanje sa predlogom „Dodaj kao
  potvrdu" (i obrnuto — račun ubačen u Potvrde); pravi računi, uključujući
  papirne uplatnice **bez QR koda**, prolaze bez ikakvog pitanja; samo
  neprepoznat dokument dobija pitanje „račun ili potvrda?". Isto važi za
  deljenje iz banke — jasna potvrda se više ništa ne pita.
- **Više prostora na istoj adresi** (stan + garaža…). Dva računa istog
  pružaoca i adrese više se ne sudaraju u imenu: šifra korisnika (IDENT),
  jedinstvena po prostoru, nosi kratku pod-oznaku — jednom vežeš `G1` za
  garažu i od sledećeg meseca fajl sam dobija ime `infostan_SG26-G1_jun26_…`.
  Ako bi ime bilo isto kao postojeće, aplikacija više ne dodaje tiho `_2`
  nego traži oznaku prostora, uz „Zapamti za ovaj prostor" da se više
  nikad ne pita.

## [1.5.1] — 2026-07-06

Dorada posle prve probe 1.5.0 na telefonu.

### Promenjeno
- **Podešavanja su sada ceo ekran** (ne poluotvoreni panel): sadržaj se pomera
  iznad tastature, polje koje uređuješ se samo doskroluje u vidno polje, a
  taster „Kraj"/Done i dugme Nazad zaista zatvaraju tastaturu (prvi Nazad
  tastaturu, drugi ekran).
- **Adresa se više nikad ne pogađa** — ako adresa sa računa nije u šifarniku,
  polje ostaje prazno („adresa?") umesto da se upiše jedini postojeći unos.
  Predlog iz memorije primalaca moguć je samo za tačno isti račun primaoca.
- **Već obrađen fajl ne može ponovo da se štiklira** u listi fascikle — nosi
  oznaku „već obrađen" i preskače se; važi i posle ponovnog pokretanja.
- **Uklonjeno „Potraži u Download"** — fascikle Racunko se prate same, a fajl
  iz Downloads dodaješ preko ➕ „Dodaj račun" (bez ikakve dozvole).

### Novo
- **„Napravi šifarnik"** — kada je šifarnik adresa prazan, na vrhu liste stoji
  jasan poziv da se prvo unesu adrese (jedino što aplikacija ne može sama);
  rad bez šifarnika je i dalje moguć.

### Ispravke
- Upozorenje „Proveri pre plaćanja" na napravljenom QR kodu sklanja se samo
  posle ~4 sekunde (ili na dodir); kratka trajna napomena ispod koda ostaje.

## [1.5.0] — 2026-07-06

Prvo objavljeno izdanje. Sve radi na uređaju — aplikacija nema INTERNET dozvolu.

### Novo
- **Skeniranje kamerom** — papirne uplatnice i računi skeniraju se uživo
  (IPS QR), a fotografija/screenshot računa čita se OCR-om.
- **Generisanje QR koda** — za račune koji nemaju IPS QR (npr. papirne
  uplatnice) aplikacija sama pravi ispravan kod za plaćanje, ali samo kada je
  broj računa primaoca **matematički proveren** (kontrolna cifra po modelu 97).
- **QR slika sa natpisom** — sačuvani QR nosi ime fajla ispod koda, pa se u
  galeriji odmah vidi koji je račun.
- **Izveštaj** — zbirni pregled obrađenih računa po pružaocu i mesecu.
- **Pamćenje primalaca** — poznati primaoci se prepoznaju i popunjavaju sami.
- **Dve varijante aplikacije**: `gms` (Google ML Kit) i `foss` (ZXing +
  Tesseract, za F-Droid) — obe potpuno offline.

### Promenjeno
- **Novo, jednostavnije skladište**: sve živi u `Download/Racunko`
  (`Racuni` + `Potvrde`). Aplikacija sama napravi fasciklu, a pristup se
  odobrava **jednim dodirom** pri prvom pokretanju. Stara `0RACUNI` struktura
  se više ne koristi (postojeći fajlovi se ne diraju).
- **➕ Dodaj iz fajla** otvara sistemski birač na Downloads — bez ikakvih
  dodatnih dozvola; izabrani fajl se premešta u odgovarajuću fasciklu.
- Fajlovi ručno ubačeni u `Racunko/Racuni` prepoznaju se sami pri pokretanju.
- Izbor više stavki dobio je jedinstvenu traku akcija (podeli sve, obriši…).
- Pri uparivanju potvrde brišu se **sve** kopije QR slike (i iz galerije).
- Tastatura više ne prekriva polje koje se uređuje (podešavanja, izmene).

### Ispravke
- Ako QR ne može da se pročita iz PDF-a, podaci se izvlače iz teksta, a kod se
  ponovo generiše iz pročitanog sadržaja — oštećen ili mutan original više nije
  prepreka.
- Deljenje potvrde iz banke stiže u već otvorenu aplikaciju (bez duple
  instance).
- JPG potvrde izabrane iz liste obrađuju se ispravno.
