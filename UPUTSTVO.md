# Računko — uputstvo za korišćenje

*(English version: [MANUAL.md](MANUAL.md) · На русском: [РУКОВОДСТВО.md](%D0%A0%D0%A3%D0%9A%D0%9E%D0%92%D0%9E%D0%94%D0%A1%D0%A2%D0%92%D0%9E.md))*

Računko je besplatna Android aplikacija koja sređuje tvoje komunalne račune:
prepoznaje ih, lepo ih imenuje, daje ti QR kod za plaćanje i pamti koje su
uplate potvrđene. Radi **potpuno bez interneta** — tvoji računi nikad ne
napuštaju telefon.

---

## 1. Instalacija (ne treba ti nikakvo tehničko znanje)

Računko se (za sada) ne nalazi u Google Play prodavnici. Instalira se iz
tzv. APK fajla — to je normalan način instalacije Android aplikacija van
prodavnice:

1. Na telefonu otvori stranicu projekta i klikni **Releases** (izdanja), pa
   preuzmi najnoviji fajl koji se završava na **`.apk`** (~36 MB).
2. Kad se preuzimanje završi, otvori fajl iz trake obaveštenja ili iz fascikle
   *Download*.
3. Android će reći da instalacija iz ovog izvora nije dozvoljena — klikni
   **Podešavanja** i uključi **„Dozvoli iz ovog izvora"**, pa se vrati nazad.
4. Klikni **Instaliraj**. Gotovo.

> ❓ **Zašto telefon upozorava?** Tako Android reaguje na svaku aplikaciju van
> Play prodavnice. Računko je otvorenog koda — svako može da proveri šta tačno
> radi — i nema dozvolu za internet, pa tvoje podatke ne može nigde da pošalje.

> ❓ **Postoji li verzija za računar (.exe) ili iPhone?** Ne — Računko je
> isključivo Android aplikacija (Android 10 i noviji).

## 2. Prvo pokretanje — nema šta da se podešava

Otvoriš aplikaciju i ona radi. Nema pitanja o dozvolama, nema ekrana pre prvog
ekrana, nema fascikle koju moraš da odobriš.

Računi i potvrde se čuvaju **u samoj aplikaciji**. Od telefona se ništa ne
traži i ništa nije vidljivo drugim aplikacijama.

> Ako želiš da ih vidiš i van Računka, imaš dve mogućnosti u Podešavanjima
> (tačka 11): **„Izvezi u fasciklu"** kad god ti zatreba kopija, ili
> **„Čuvaj i kopiju u mojoj fascikli"** da se svaki račun upisuje i tamo.
>
> Pošto arhiva živi u aplikaciji, **deinstalacija je briše**. Ako ti je stalo
> do te arhive, s vremena na vreme uradi izvoz.

## 3. Dodavanje računa

Tri načina, sva tri iz **➕** dugmeta na kartici „Računi":

| Način | Kada |
|---|---|
| **📄 Dodaj iz fajla** | PDF račun stigao mejlom ili preuzet sa portala (EPS, InfoStan…) — birač se otvara na *Download* fascikli |
| **📷 Dodaj fotografiju** | Već imaš sliku/screenshot računa u galeriji |
| **📸 Skeniraj račun** | Papirni račun / uplatnica ti je u ruci — uperi kameru u IPS QR kod |

Posle dodavanja klikni **Obradi**. Računko pročita račun i pokaže karticu sa
četiri polja: **pružalac, adresa, mesec, iznos**. Sve što je sigurno pročitano
biće popunjeno; ono što nije, piše sa znakom pitanja (npr. „adresa?") — klikni
na polje i dopuni. *Računko nikad ne pogađa — radije pita.*

Fajl se premesti u arhivu aplikacije i preimenuje, npr.:

```
infostan_KD7_maj26_11152.pdf
   │       │    │      └─ iznos (zaokružen)
   │       │    └─ mesec i godina
   │       └─ tvoja skraćenica adrese (iz šifarnika)
   └─ pružalac usluge
```

> 💡 Ako imaš celu fasciklu računa, ne moraš da ih dodaješ jedan po jedan —
> **Podešavanja → Uvezi iz fascikle** ih prekopira odjednom. Posle uvoza stoje
> u sekciji „Fajlovi u fascikli" i čekaju da klikneš **„Obradi"**.

### Pogledaj sam dokument

Na svakoj kartici stoji **„Pogledaj"** — račun ili potvrda se otvara preko celog
ekrana, u samom Računku. To najviše treba kad kartica kaže „adresa?" ili
„iznos?": da bi se podatak ispravio, papir mora da se vidi.

**Dvostruki dodir** uvećava, **prevlačenje** pomera, **„nazad"** prvo vraća uvećanje
pa tek onda listu — onakvu kakva je ostavljena.

## 4. Šifarnik adresa (preporučeno, 2 minuta)

Adresa je jedino što Računko ne može sam da izvede — tvoja je odluka da li se
stan zove „KD7", „STAN" ili „KUĆA". U **Podešavanja → Moje adrese** upiši:

- **levo**: kratku oznaku za ime fajla (npr. `KD7`)
- **desno**: tekst adrese kako piše na računu (npr. `koste dragojevića 7`) —
  ćirilica ili latinica, svejedno

Više redova sme da deli istu oznaku (različiti načini na koje pružaoci pišu
istu adresu). Bez šifarnika sve radi, samo adresu upisuješ ručno po računu.

## 5. QR kod — plaćanje jednim skenom

Na kartici računa:

- **prikaži QR** — kod na ekranu; skeniraj ga direktno iz aplikacije banke.
- **QR slika** — snima kod u galeriju (sa imenom računa ispod koda), za banke
  koje umeju da skeniraju iz galerije.
- **Napravi QR** — za račune **bez** QR koda (stare papirne uplatnice):
  Računko sam sastavi ispravan IPS kod. Iz bezbednosti, dugme radi tek kada je
  broj računa primaoca **matematički proveren** (kontrolna cifra) i iznos
  poznat. Kod napravljenog koda uvek proveri primaoca, iznos i poziv na broj
  u banci pre potvrde plaćanja.

> Računko **nikada ne plaća umesto tebe** — samo ti priprema kod. Plaćanje
> uvek potvrđuješ u svojoj banci.

## 6. Potvrde o uplati

Kad platiš, sačuvaj dokaz uz račun. Dva načina:

1. **Iz aplikacije banke:** Podeli/Share potvrdu (PDF ili sliku) → izaberi
   **Računko**. Potvrda se automatski upari sa računom.
2. **➕ na kartici računa** („dodaj potvrdu") — direktno vezivanje za taj račun.

Uparivanje ide u tri sloja: po **pozivu na broj**, pa po **računu primaoca +
iznosu**, a ako ništa nije sigurno — Računko ti ponudi da sam izabereš račun.
Uparena potvrda se preimenuje u `uplata_` + ime računa, a račun dobija oznaku
**plaćeno ✓**.

## 7. Više prostora na istoj adresi (stan + garaža)

Dva InfoStan računa za istu adresu (stan i garaža) razlikuju se po „šifri
korisnika". Prvi put kad se imena sudare, Računko te pita za kratku oznaku
prostora (`G1`, `STAN`, `LOKAL`…) uz opciju **„Zapamti za ovaj prostor"** —
od sledećeg meseca fajl sam dobija ime tipa `infostan_SG26-G1_jun26_1200.pdf`.

## 8. Rok plaćanja i podsetnik

Na svakoj kartici stoji **rok plaćanja**. Računko ga čita sa računa, ali samo
kad na računu piše baš to — „rok plaćanja", „datum dospeća", „valuta plaćanja",
„platiti do". **Datum izdavanja se nikad ne koristi kao rok**, jer to nije isti
datum; ako roka nema, polje ostaje prazno i možeš ga uneti sam.

Dodirni rok na kartici da otvoriš podešavanje:

- **Podseti me da se približava plaćanje** — uključuje se za **svaki račun
  posebno**.
- **Koliko dana ranije** — npr. 3 dana pre roka.

Kad otvoriš aplikaciju, na vrhu se pojavi traka sa računima kojima se rok
približio (i koliko ih je već isteklo). Dodir na **„Prikaži"** suzi listu baš
na njih. Račun kom je rok istekao nosi crvenu oznaku, ali **ne menja mesto** u
listi — redosled ostaje po mesecu.

> ⏰ Izabrano **vreme** se pamti, ali za sada još ne radi ništa: podsetnik se
> vidi kad otvoriš aplikaciju, a ne kao sistemsko obaveštenje. Tako i piše na
> samom ekranu — vreme čeka da obaveštenja budu dodata.

## 9. Sređivanje liste — adrese, meseci, izbor

- **Zbir na vrhu** — koliko je ostalo za plaćanje i koliko je plaćeno.
- **Čipovi za adresu** — pojavljuju se kad imaš više od jedne adrese; dodir
  suzi listu na tu adresu.
- **Sekcije po adresama** se sklapaju. Kad imaš više adresa, ekran se otvara
  sklopljen — kao pregled koji razviješ. Kod jedne adrese nema šta da se bira,
  pa stoji otvoreno. Računi **bez adrese idu prvi**, jer oni traže tvoju
  reakciju.
- **Redosled** — po mesecu na koji se račun odnosi, najnoviji na vrhu.
- **Biranje računa** — **dug pritisak** na karticu ulazi u režim izbora, dalje
  bira običan dodir. „Izaberi sve" bira **samo ono što je trenutno prikazano**
  — ako je uključen filter po adresi, bira samo tu adresu.

## 10. Izveštaj

Izaberi račune (dug pritisak, pa dodiri) → **Napravi izveštaj**: zbirni pregled
po mesecu i adresi (pružalac, iznos, ukupno), spreman za kopiranje ili deljenje
— zgodno za cimere, porodicu ili knjigovođu.

```
JUL  KD7
EPS        4.200 RSD
InfoStan   4.650 RSD
∑          8.850 RSD
```

Razmaci su podešeni tako da iznosi stoje jedan ispod drugog i **pošto ga
nalepiš** u Viber, WhatsApp ili belešku — a ne samo dok stoji u aplikaciji.

## 11. Podešavanja

- **Jezik** — srpski / engleski / ruski / prati sistem.
- **Moje adrese** — šifarnik (tačka 4).
- **Nazivi pružalaca** — kako se koji pružalac piše u imenu fajla.
- **Mesto čuvanja** — piše ti da je arhiva u samoj aplikaciji. Nema šta da se
  bira.
- **Izvezi u fasciklu** — upiše račune, potvrde i `racunko.json` u fasciklu koju
  izabereš. Fajlovi ostaju obični fajlovi, otvoriš ih bilo čime; manifest uz njih
  nosi ono što ime fajla ne može — rokove, podsetnike, koja potvrda pripada kom
  računu, šifarnik i zapamćene primaoce.
- **Uvezi iz fascikle** — vraća sve to, na ovom ili na drugom telefonu. Radi i sa
  običnom fasciklom PDF-ova, samo tada dobiješ ono što imena fajlova nose.
- **Čuvaj i kopiju u mojoj fascikli** — kad uključiš, svaki račun se upisuje i u
  fasciklu koju izabereš, pa ga vidiš i van Računka. Uključivanje odmah prepiše
  postojeću arhivu u nju. Isključivanje **ništa ne briše**.
- **Brisanja** — istorija uparivanja, zapamćeni primaoci, ili kompletno
  pražnjenje arhive (dva potvrđivanja — nepovratno!).

## 12. Česta pitanja

**Gde su moji podaci?** Isključivo na tvom telefonu, u privatnom prostoru same
aplikacije. Drugim aplikacijama nisu vidljivi, a Računko nema dozvolu za internet
— proveri i sam u Android podešavanjima aplikacije. Aplikacija je isključena i iz
sistemskog automatskog bekapa, da tvoji računi ne bi završili na Google Drive-u.

**Šta ako obrišem aplikaciju?** **Arhiva se briše sa njom.** To je cena toga što
se ništa ne traži od telefona. Zato postoji **Izvezi u fasciklu** — izvezena
fascikla su obični fajlovi koji preživljavaju deinstalaciju, a uvoz ih vraća
zajedno sa rokovima, uparivanjima i šifarnikom. Ako ti je arhiva važna, izvezi je
pre nego što obrišeš aplikaciju ili promeniš telefon.

**Moja banka nije prepoznata?** Potvrda će tražiti ručno uparivanje — radi,
samo nije automatski. Ako želiš da banka bude podržana, otvori „issue" na
GitHub stranici projekta i priloži **anonimizovan** primer potvrde (prekrij
ime, adresu i brojeve računa!).

**Kako da sam napravim APK iz koda?** Instaliraj besplatni
[Android Studio](https://developer.android.com/studio), otvori preuzeti
projekat (`File → Open`), sačekaj da se sve preuzme, pa
`Build → Build App Bundle(s) / APK(s) → Build APK(s)`. Gotov APK je u
`app/build/outputs/apk/gms/debug/`. Napredni korisnici: komande su u
[README.md](README.md).

**Kako da prijavim grešku ako ne znam GitHub?** Napravi besplatan nalog na
github.com, otvori stranicu projekta → kartica **Issues** → **New issue** →
opiši šta se desilo (može i na srpskom). Slika ekrana pomaže — ali **nikad ne
kači ceo račun** sa svojim podacima.

---

*Računko je projekat otvorenog koda pod [Apache-2.0](LICENSE) licencom.
Kontakt: pavle@lausevic.com*
