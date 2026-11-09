# Hrvatska: Knjiga prometa (KPR) i PO-SD

## Status implementacije

Fakturiva za hrvatske obveznike poreza na dohodak ima **lokalni** modul
*Izvještaji → Knjiga prometa / PO-SD (Hrvatska)* i istu opciju u
*Postavke → POSLOVNI ALATI*. Podatci iz korisnikova lokalnog profila
ne šalju se na poslužitelj tijekom obračuna i PDF izvoza.

**Predlošci:** dva PDF primjera koja je dostavio vlasnik projekta
koriste se samo kao **referencija za strukturu i tipografiju**, a nisu
ugrađeni ni kopirani u repozitorij. Podatci obveznika iz tih primjera
nisu u izvornom kodu niti testnim podacima. PDF generator izrađuje
novi tekstualni dokument crno-sivog tabličnog rasporeda.
Pikselna istovjetnost s referentnim PDF-ovima još nije potvrđena
testiranjem na identičnoj rezoluciji.

## Podržani subjekti

- **Paušalni obrt / samostalna djelatnost s paušalnim dohotkom:** KPR
  (gotovina, čekovi, bankovni primitci, kartice) i godišnji PO-SD.
- **Obrt s dohotkom prema poslovnim knjigama:** informativni KPR za
  gotovinske primitke/čekove, bez automatiziranog PO-SD.
- **d.o.o., j.d.o.o., obveznici poreza na dobit te nepoznat porezni model:**
  program ne generira PO-SD niti pogrešno prikazuje KPR kao obveznu
  zamjenu za propisane poslovne knjige. Za takve subjekte potrebno je
  posebno implementirati primjerene računovodstvene obrasce.
- Zajednička djelatnost i PO-SD-Z **nisu** automatski obračunati.

## Izvor podataka: datum plaćanja

- U knjigu ulaze **pojedinačne evidentirane naplate**, a ne izdani,
  poslani ili samo označeni računi.
- Razdoblje KPR-a: korisnik bira datum **od** i **do** uključivo.
- Jedna djelomična uplata ima jedan zapis: datum uplate, broj računa,
  iznos, poslovna jedinica i način plaćanja.
- **Gotovina i čekovi** idu u stupac gotovine; **kartice i bankovni
  prijenosi** u stupac bezgotovinskih primitaka.
- Nenaplaćeni računi i ponude nisu primitci.
- Datum izdavanja nije datum primitka. Račun iz prosinca naplaćen u
  siječnju ulazi u godinu siječanjske naplate.
- Mješovite naplate jednog računa mogu imati različite načine naplate.
- **Postojeće povijesne uplate** bez odvojenog načina plaćanja nakon
  migracije baze nose status `UNKNOWN`. Izvoz je blokiran dok korisnik
  ne provjeri te uplate i ručno ih klasificira.
- Uplate u drugim valutama nisu automatski pretvorene u EUR jer
  izvorni iznos nije dovoljan za propisano tečajno preračunavanje.
  Izvoz takvih razdoblja blokiran je do uvođenja potvrđenog tečaja
  i EUR protuvrijednosti.
- Povrati, storna i ručne korekcije primitaka izvan Fakturive
  nisu automatski dostupni te ih korisnik mora usporediti s blagajnom
  i bankovnim izvodima. Program ne tvrdi da ima nevidljive bankovne uplate.

## Razdoblje i godišnja prijava

PO-SD se priprema po **poreznoj godini**; odabir početka i završetka
obavljanja djelatnosti određuje broj mjeseci. Za 2025. i 2026.
koristi se sedam godišnjih razreda do 60.000 EUR ukupnih primitaka,
s 12 % godišnjeg paušalnog poreza te umanjenjem od 75 % samo uz
potvrđeno pravo na posebno područje. Korisnik ručno potvrđuje
stvarno uplaćeni paušalni porez prema svojoj PKK.

Za kraći radni period iznos primitka dijeli se brojem mjeseci i
preračunava na godišnju razinu, a obveza razmjerno mjesecima.
Broj mjeseci obuhvaća pune kalendarske mjesece i posljednji mjesec
djelatnosti. Automatizacija trenutno podržava samo **jedno
kontinuirano razdoblje obavljanja pojedinačne djelatnosti u jednoj
godini**. Za prekide, sezonsko poslovanje s više intervala,
zajedničku djelatnost, razlike poreznog rješenja ili posebne iznimke
potrebna je provjera stručne osobe; ne smatrajte automatski PO-SD
konačnom poreznom prijavom.

Za 2025. i 2026. porezne se razrede primjenjuje prema
[NN 16/2025](https://narodne-novine.nn.hr/clanci/sluzbeni/2025_01_16_149.html).
Izvješće za 2026. podnosi se u 2027., u pravilu do 15. siječnja.
Ako je datum izvoza prije kraja godine, izvješće je nacrt.
Za porezne godine izvan 2025./2026. aplikacija zahtijeva ponovno
normativno potvrđivanje razreda.

## Izvozi

| Datoteka | Status |
| --- | --- |
| `Fakturiva-KPR-od-do.pdf` | PDF s odabranim razdobljem i primitcima; ponavljaju se zaglavlja i zbrajaju retci |
| `Fakturiva-PO-SD-godina.pdf` | PDF s rubrikama I.–VII., poreznim razredima, uplaćenim porezom i razlikom |
| `Fakturiva-PO-SD-RADNI-godina.xml` | **Radni strojno čitljiv XML, NIJE za uvoz u ePoreznu** |

### PO-SD XML za ePoreznu

Dostupni su
[službeni paketi XML shema i primjera ePorezne](https://e-porezna.porezna-uprava.hr/Pages/Ousluzi.aspx).
PO-SD XML shema mijenjana je za prijavu od 2025. godine.

**Za sada datoteka izvoza nije službeno validirani PO-SD XML.**
U postojećem razvoju nije bilo moguće dobaviti i potvrditi
odgovarajući službeni XSD s primjerom, pa bi tvrdnja da proizvoljno
izrađeni XML prolazi u ePoreznoj bila netočna. UI zato jasno
upozorava korisnika prije preuzimanja.

Za produkcijski izvoz u ePoreznu potrebno je: verificirati
trenutačni XSD, napraviti službeni serializator, pokrenuti
XML Schema validaciju nad izlazom i uspješan probni uvoz.
**Bez tih provjera ne označavati XML kao predajni obrazac.**

## Provjere

Instrumentacijski testovi provjeravaju da se:
- primici ne prebacuju u datum izdavanja računa;
- više djelomičnih uplata jednog računa evidentira odvojeno;
- gotovina i bankovni primitci razdvajaju bez zaokruživanja;
- ista uplata ne pribraja dvaput;
- datumi od/do primjenjuju uključivo;
- uplate nakon nove godine ne ulaze u prethodnu;
- nepoznat način plaćanja blokira izvoz;
- paušalni razred, porezni mjeseci i posebno umanjenje računaju točno;
- PDF počinje ispravnim PDF zaglavljem, a XML je izričito označen
  kao nevalidiran nacrt;
- drugi korisnički profil ne može preuzeti podatke prethodnog profila.

**Upozorenje:** ovo je pomoćna evidencija, a ne zamjena za poreznu
savjetodavnu provjeru ili službenu potvrdu predaje obrasca. Izvozi
se stvaraju lokalno i ne izvršavaju poreznu prijavu.

## Službeni izvori i dodatne upute

- [Hrvatska obrtnička komora — Knjiga prometa](https://www.hok.hr/gospodarstvo-i-savjetovanje/obrasci-za-poslovanje-obrta/knjiga-prometa-obrazac-kpr)
- [Porezna uprava — ePorezna, XML sheme i primjeri](https://e-porezna.porezna-uprava.hr/Pages/Ousluzi.aspx)
- [Narodne novine 16/2025 — izmjene paušalnih razreda](https://narodne-novine.nn.hr/clanci/sluzbeni/2025_01_16_149.html)
- [Narodne novine — Pravilnik o paušalnom oporezivanju](https://narodne-novine.nn.hr/clanci/sluzbeni/full/2020_01_1_1.html)
