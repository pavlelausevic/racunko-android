# Changelog

## [1.6.2] — 2026-08-13

Mirnije lice i jedan izlaz koji je nedostajao. Aplikacija radi isto — samo se
lakše čita, i više ne ume da te zaključa pred nečitljivim dokumentom.

### Promenjeno
- **Boja sada označava stanje, ne vrstu polja.** Svaki deo imena fajla je ranije
  imao svoju boju — zlatna pružalac, tirkiz adresa, limun mesec, smaragd iznos —
  uz zeleno tonirane površine. To je bilo čitljivo onome kome je značenje boja
  objašnjeno, a bučno svima ostalima. Sada je površina neutralno crna, tirkiz je
  jedini akcenat, a obeležava se **samo ono što traži tebe**: nedokazano polje i
  vrednost preuzeta iz memorije primalaca. Oboje istom oznakom, jer je „pogledaj
  ovo" jedna ideja.
- **Kartica vodi sa onim zbog čega joj prilaziš** — ko traži i koliko. Pružalac
  levo, iznos desno, adresa i mesec ispod prigušeno; ime fajla se prikazuje
  jednom, tiho. Svako polje se i dalje tapka radi izmene.
- **Zaglavlje liste** vodi jednim brojem umesto dva skoro jednaka reda.
- **Svi dijalozi govore jednim jezikom**: dva dugmeta jednake širine umesto dva
  teksta različite dužine gurnuta uz ivicu, isti okrugli marker izbora kao u
  listama umesto kvadratnog polja, i tekst koji sam bira poravnanje — jedan red
  centriran, više redova ravno na obe margine.
- **Upit „račun ili potvrda?"** je skraćen na jednu frazu, a kad se dokument ne
  prepozna vodi **Potvrda** — računi dolaze od pet poznatih izdavalaca, a potvrde
  od svih banaka redom.
- **Traka „Fajlovi u fascikli"** više ne prelama broj stavki: dugmad su postala
  ikonice, pa naslov opet staje.
- Na kartici Potvrde **„+" sada nudi i galeriju**. Ranije je skakao pravo u birač
  fajlova, pa se fotografisana potvrda mogla dodati samo ako je prethodno
  sačuvana kao fajl. Skeniranje ostaje samo uz račune, jer čita QR uživo.

### Popravljeno
- **Nečitljiv dokument se sada može ukloniti iz aplikacije.** Takva kartica nije
  bila selektabilna i nije imala nijedno dugme, pa se jedini izlaz bio obrisati
  fajl van aplikacije. Sada nosi „Ukloni ovu karticu" i bira se kao svaka druga.
- Kartica sa greškom kaže **koji je fajl** u pitanju; ranije je vodila sa
  „pružalac?", a ime fajla se nije videlo.
- Poruke više ne tvrde da je dokument PDF kada je slika.
- Zbir sekcije se **ne izmišlja**: ako nijedan iznos nije pročitan, kolona ostaje
  prazna umesto da piše `0`.
- Statusna i navigaciona traka prate boju aplikacije; ostajale su u staroj.

### Za one koji rade na kodu
- **Fixture korpus: 8 → 21 slučaj**, uz tri nove grupe bez izdavaoca —
  `address/` (granice poklapanja i pravilo da se adresa nikad ne pogađa),
  `confirmation/` i `unknown/`. Schema je dobila `addressBook`, `looksLikeBill`,
  `docType`/`docTypeConfidence`/`docTypeLean`; `spaceId` je prevezan na put koji
  zaista pravi ime fajla.
- Testovi 86 → 74. **To je selidba, ne gubitak** — svaki uklonjen Kotlin test
  postoji kao slučaj u korpusu, a ono što je ostalo u Kotlinu ostalo je iz
  zapisanog razloga: algoritam, uparivanje i wiring fixtura ne može da izrazi.
  Merilo je zapisano u TESTING.md: *može li port da pusti ovaj slučaj bez ijedne
  linije Kotlina?*

## [1.6.1] — 2026-08-13

Bez izmena u ponašanju aplikacije — samo je APK skoro upola manji.

### Promenjeno
- **APK je manji: 62,4 MB → 35,9 MB** (i 49,2 MB → 23,7 MB za foss varijantu).
  Uključeno je skraćivanje koda: sve iz biblioteka što aplikacija nikad ne
  pozove sada izlazi iz APK-a. U odnosu na 1.5.2 to je ukupno **−65 %**.

  U 1.6.0 je ovo bilo pripremljeno ali isključeno, jer se aplikacija tada
  instalirala a nije pokretala. Uzrok je bio ML Kit: on svoje delove pronalazi
  po imenu klase i pamti ih po samoj klasi, pa ih skraćivač nije smeo dirati.
  Sada je to zapisano kao pravilo i provereno na telefonu.

## [1.6.0] — 2026-08-12

Najveća promena od prvog javnog izdanja: aplikacija je dobila novo lice, računi
su sređeni po adresama, i uveden je rok plaćanja sa podsetnikom.

### Novo
- **Rok plaćanja i podsetnik.** Računko čita rok sa računa — ali **samo sa
  njegove oznake** („rok plaćanja", „datum dospeća", „valuta plaćanja"…), nikad
  iz datuma izdavanja, da ne bi izmislio datum koji na računu ne piše. Ako roka
  nema, polje ostaje prazno i možeš ga uneti sam. Za svaki račun posebno se
  uključuje podsetnik i bira koliko dana ranije; pri otvaranju aplikacije
  pojavi se traka sa računima kojima se rok približio, a dodir na nju suzi
  listu baš na njih. Istekao rok nosi crvenu oznaku na kartici.
- **Računi grupisani po adresama.** Na vrhu je kartica sa zbirom („za plaćanje"
  / „plaćeno"), ispod nje čipovi za filtriranje po adresi, pa sekcije koje se
  sklapaju. Računi bez adrese idu **prvi** — oni traže tvoju reakciju. Kad
  imaš više adresa, sekcije se otvaraju sklopljene, kao pregled koji razviješ;
  kod jedne adrese nema šta da se bira pa stoji otvorena.
- **Računi poređani po mesecu na koji se odnose**, najnoviji na vrhu — isto u
  punoj listi i unutar filtera po adresi.
- **Ruski jezik.** Ceo interfejs, uz ruske oblike množine. Nazivi meseci u
  imenima fajlova ostaju srpski — to je pravilo imenovanja, ne prevod.
- **„Fajlovi u fascikli" se sklapa** i stoji sklopljeno; „Dodaj račun" je i
  dalje u zaglavlju, pa dodavanje nikad ne traži da sekcija bude otvorena.

### Promenjeno
- **Novo vizuelno lice** — duboko zelene površine, tirkizni akcenat, zlatna za
  pružaoca, smaragdna za iznos. Sve ikonice su crtane u kodu (Material
  Symbols), emodžija u interfejsu više nema. Narandžasta tačka u „računko."
  ostaje kao jedini znak koji je samo naš.
- **Biranje računa bez čekboksa.** Dug pritisak na karticu ulazi u režim
  izbora, dalje bira običan dodir. „Izaberi sve" bira **samo ono što filter
  trenutno prikazuje**, ne sve u tabu. (Čekboksi u listi fajlova ostaju — to je
  birač fajlova, ne kartica.)
- **Izveštaj se poravnava za normalan font.** Ranije je razmak računat po broju
  slova, što se slaže samo u fontu sa jednakom širinom znakova — a izveštaj se
  lepi u Viber ili WhatsApp, gde je „InfoStan" mnogo šire od „EPS". Sada se
  računa stvarna širina, pa iznosi stoje jedan ispod drugog i posle lepljenja.
- **Aplikacija se ne otvara dvaput.** Deljenje potvrde iz aplikacije banke sada
  uvek vraća **postojeći** Računko sa svim tvojim karticama, umesto da otvori
  novi u Recents-u ispod banke.
- **Prikaz preživljava izlazak** — izbor kartica, filter i pozicija skrola su
  tu kad se vratiš.
- **APK je manji za 40 %** — sa 103 MB na 62 MB. Izbačene su biblioteke za
  procesore kojih na telefonima nema (x86), tabele za kriptografiju koju
  aplikacija ne koristi, i azijske tablice znakova iz PDF čitača.
- U podešavanjima „sz" je ispisano kao „stambena zajednica"; sekcija „Moji
  pružaoci" je privremeno sklonjena dok se ne razradi.

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
