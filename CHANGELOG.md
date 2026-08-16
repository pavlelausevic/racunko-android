# Changelog

## [1.7.0] — 2026-08-15

Instaliraš Računko i on radi — bez pitanja o dozvolama i bez ekrana pre prvog
ekrana. Arhiva živi u samoj aplikaciji, QR se pravi kad zatreba umesto da stoji
kao fajl, a izvoz i uvoz čuvaju sve što ime fajla ne može da ponese.

### Čita i ćirilicu


#### Promenjeno
- **Računko sada čita ćirilicu i u Play izdanju.** Računi javnih preduzeća
  štampaju se ćirilicom, a prepoznavač teksta koji je Play izdanje koristilo zna
  samo latinicu — zato sa screenshot-a takvog računa nije umeo da pročita ni
  adresu ni rok. Sada oba izdanja koriste isti prepoznavač, sa srpskim jezikom.
  Aplikacija je pri tom **manja za 3,6 MB**.
- **Aplikacija se više ne pravi rezervna kopija na Google Drive.** Otkad se
  arhiva čuva u samoj aplikaciji, sistemsko automatsko čuvanje bi je slalo na
  tvoj Drive nalog. Isključeno. Ako želiš kopiju van telefona, to radi izvoz —
  kad ti odlučiš i u fasciklu koju izabereš.

#### Popravljeno
- **Aplikacija se otvarala na engleskom** na telefonu podešenom na srpski
  (latinicu), iako je u Podešavanjima pisalo da je izabran srpski. Android nije
  prepoznavao da je podrazumevani tekst aplikacije pisan latiničnim srpskim, pa
  je birao engleski kao sledeći jezik sa liste telefona.
- **Potvrde više ne pišu „neplaćeno".** Zbir po adresi je brojao potvrde kao da
  su računi, pa su i one uz plaćen račun izgledale kao dug.
- **„Datum valute" se sada čita kao rok plaćanja.** Falio je jedan padež.
- **Rok plaćanja se sada čita i sa slike računa.** Screenshot računa je u
  aplikaciju ulazio u veličini u kojoj je i snimljen, a sitno štampani redovi na
  toj veličini nisu bili čitljivi — račun bi dobio sve ostalo, ali bi ostao bez
  roka. Slika se sada uveća pre čitanja, kao što se to oduvek radilo sa PDF-om.
- **Račun se imenuje po adresi prostora, a ne po adresi za poštu.** Na računima
  koji štampaju obe, aplikacija je umela da uzme onu za poštu — i to tek pošto
  se i ona doda u šifarnik, pa je izgledalo kao da se pokvarilo nešto što je
  radilo. Sada uvek ima prednost adresa prostora na koji se račun odnosi.
- **Iz pregleda računa pred rokom sada se lako vraćaš.** Filter je postao
  dugme sa zvoncetom u istom redu sa adresama, pa ga „Sve" gasi kao i svaki
  drugi filter.
- **InfoStan računi sada dobijaju rok plaćanja.** Rok je na njima odštampan, ali
  u tabeli — u jednom redu stoje nazivi kolona, u sledećem njihove vrednosti — pa
  je između natpisa „Датум доспећа" i samog датума stajao broj računa i
  aplikacija bi odustala. Kartica je zato pisala „rok?" i podsetnik za te račune
  nikad nije mogao da se uključi. Sada se čita.
- **Račun se više ne imenuje po mesecu iz stavke.** Julski InfoStan račun koji
  nosi zaostatak za maj („заједничка електрична енергија – мај") umeo je da se
  nazove `maj26`, jer je od svih meseci koje dokument pomene biran onaj koji je
  prvi po kalendaru, a ne onaj koji stoji prvi na samom računu. Sada je merilo
  gde se mesec nalazi na papiru, a ne gde je u godini.

### Arhiva je tvoja, ne fasciklina

Instaliraš Računko i on radi. Bez pitanja o dozvolama, bez ekrana pre prvog
ekrana, bez fascikle koju moraš da odobriš da bi aplikacija uopšte počela.

#### Promenjeno
- **Računi i potvrde se sada čuvaju u samoj aplikaciji.** Ništa se ne traži od
  telefona i ništa nije vidljivo drugim aplikacijama. Prvi ekran koji si ranije
  morao da prođeš — biranje fascikle — više ne postoji.
- **Fascikla se vratila kao izbor.** U Podešavanjima uključiš „Čuvaj i kopiju u
  mojoj fascikli" i svaki račun se upisuje i tamo, pa ga vidiš i van Računka. Kad
  je uključiš, postojeća arhiva se odmah prepiše. Kad je isključiš, **ništa se ne
  briše** — ni arhiva ni ono što je već u fascikli.

#### Dodato
- **Izvoz i uvoz, ravnopravno.** Izvoz upisuje račune, potvrde i `racunko.json` u
  fasciklu koju izabereš. Fajlovi ostaju obični fajlovi — otvoriš ih bilo čime, a
  imena im i dalje govore ko, gde, koji mesec i koliko. Manifest uz njih nosi ono
  što ime ne može: rokove, podsetnike, koja potvrda pripada kom računu, šifarnik
  adresa, memoriju primalaca, tvoje nazive pružalaca.
- Uvoz vraća sve to na bilo kom telefonu. Radi i sa fasciklom bez manifesta —
  tada dobiješ fajlove i ono što njihova imena nose.

### QR bez traga

QR kôd više ne postoji kao fajl. Postoji kao nešto što se napravi kad zatreba.

#### Promenjeno
- **Neplaćen račun odmah pokazuje svoj QR.** Ranije je svaka kartica tražila da
  se klikne „prikaži QR" — i to baš na računu koji tek treba platiti, dakle na
  jedinom mestu gde kôd zaista treba. Sada je otvoren kad račun nije plaćen, a
  sklopljen kad jeste. Time i visina kartice govori u kom je stanju: plaćene su
  kraće nego ranije.
- **Ništa se više ne upisuje u galeriju samo od sebe.** Do sada je svaki obrađen
  račun ostavljao QR sliku na dva mesta bez pitanja. Sada nula — kôd izlazi iz
  aplikacije isključivo kad ti to zatražiš.

#### Dodato
- **„Podeli QR"** — šalje sliku direktno u aplikaciju banke ili gde god hoćeš,
  preko privremene kopije koju sistem sam počisti. Uz njega ostaje i **„U
  galeriju"** za trajno čuvanje. Dva puta zato što banke nisu iste: neke primaju
  podeljenu sliku i odmah otvaraju plaćanje, druge se u listi deljenja uopšte ne
  pojavljuju pa im kôd mora doći iz galerije. Aplikacija ne može da pogodi koja
  je tvoja — probaš jednom i znaš.
- Kad se QR ne može ni pročitati iz dokumenta ni sklopiti iz podataka, kartica to
  **kaže**, umesto da ostavi prazno mesto.

#### Popravljeno
- **QR je preživeo gašenje aplikacije.** Slika se nije čuvala u bazi, pa je posle
  restarta „QR slika" umela da ne uradi ništa. Sada se kôd obnavlja — pročita se
  ponovo iz samog računa, a ako to ne uspe, sklopi se iz zapamćenih podataka
  (tada nosi napomenu da ga proveriš pre plaćanja, jer to više nije kôd koji je
  odštampao izdavalac).
- **Lista deljenja nije prikazivala QR** koji deliš — stajao je sivi pravougaonik
  baš tamo gde treba da prepoznaš šta šalješ.
- **F-Droid izdanje nije umelo da pročita QR sa InfoStan računa** i tiho ga je
  sklapalo iz podataka umesto da pročita izdavačev. Kôd je mali deo cele
  stranice, pa se sada strana pretražuje temeljnije. Skeniranje kamerom je
  namerno ostavljeno brzo — tamo se gleda trideset slika u sekundi.
- **InfoStan računi su od verzije 1.6.0 čitani naopako, i to tiho.** Da bi
  aplikacija bila manja, tada je izbačen deo biblioteke za čitanje PDF-a uz
  obrazloženje da služi samo kineskom, japanskom i korejskom pismu. Nije bilo
  tako: među tim fajlovima su i dva koja opisuju kodiranje koje koristi gotovo
  svaki savremeni PDF sa ugrađenim fontom — a takvi su InfoStan računi.
  Bez njih se iz računa i dalje izvlačio tekst, samo što nije bio tekst:
  „ЈАВНО КОМУНАЛНО" je stizalo kao `jabho komyhanho`. Adresa se nije mogla
  pročitati, rok takođe, i račun je umeo da završi na **tuđoj adresi**.
  Vraćeno. Aplikacija je zbog toga veća za 1,2 MB — i čita račune kako treba.
- **Kad dokument ipak ne može da se pročita, Računko više ne pogađa adresu.**
  Ranije bi je popunio iz pamćenja, po računu primaoca — a kod izdavalaca poput
  InfoStana taj račun je isti za sve korisnike i sve stanove, pa nije mogao da
  razlikuje adrese. Sada adresa ostaje `adresa?` sa oznakom „dopuni ručno" i fajl
  se ne preimenuje. Isto pravilo po kom se adresa nikad ne izvodi iz QR koda —
  kad se ne može dokazati, pita se.
- **Srpski navodnici su ostajali nezatvoreni** u pet poruka („+", „potvrde",
  „adresa?"…). Android izbacuje običan navodnik iz teksta ako nije označen; sada
  se koristi pravi zatvoreni navodnik.
- Napomena ispod napravljenog QR-a poravnata je kao i ostali tekst u aplikaciji —
  jedan red centriran, više redova ravno na obe margine.

### Privatnost
- QR sačuvan u galeriju i dalje se **briše sam kad račun postane plaćen**. Briše
  se isključivo slika koju je Računko sam upisao i zapamtio — galerija se nikad
  ne pretražuje ni za čim.

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
