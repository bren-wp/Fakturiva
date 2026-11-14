# Fakturiva — vizualna i funkcionalna kontrolna matrica

## 20 referentnih zaslona

Reference iz privatnog ZIP paketa `Fakturiva_Android_Brending_i_20_Ekrana(8).zip` imaju 1179 × 2556 px. Svih 20 stvarnih Android snimki iz `main` commita `bef1bcc67e` već je izmjereno: prosječni RGB MAE **8,229576/255**, **0/20** točnih podudaranja. Potpuni podaci i dokaz porijekla: [PIXEL_AUDIT_MAIN_BEF1BCC_2026-11-14.md](PIXEL_AUDIT_MAIN_BEF1BCC_2026-11-14.md). CI bez pada ne znači pixel-perfect 1:1.

| # | Referenca | Funkcionalni ekvivalent | Status verifikacije |
|---|---|---|---|
| 01 | Početni zaslon | originalni native launcher/splash | emulatorska usporedba potrebna |
| 02 | Dobrodošli | prvi ulazak | usporedba potrebna |
| 03 | Prijava | zaslon prijave e-adresom i lozinkom na zajednički Android/web račun; lokalni offline rad je odvojen | screenshot snimljen, 1:1 nije potvrđen |
| 04 | Početna | dashboard | usporedba potrebna |
| 05 | Računi | popis, pretraga, filtri | test s podacima |
| 06 | Novi račun | unos klijenta i datuma | test prekida rada |
| 07 | Stavke računa | dodavanje/brisanje/PDV | test aritmetike |
| 08 | Pregled računa | PDF i potvrda | provjera prijeloma dokumenta |
| 09 | Ponude | popis i filtri | test s podacima |
| 10 | Nova ponuda | unos i rok valjanosti | test prekida rada |
| 11 | Pregled ponude | PDF, konverzija u nacrt računa | test kloniranja stavki |
| 12 | Klijenti | popis i unos | test unosa |
| 13 | Detalji klijenta | povijest dokumenata | test povezivanja |
| 14 | Artikli/usluge | katalog | test unosa |
| 15 | Izvještaji | sažetak iz SQLite | provjera agregata |
| 16 | Naplata | evidencija uplata | provjera parcijalne naplate |
| 17 | eRačun integracija | priprema profila, posrednika i ovlaštenja; nema dokaza o certificiranom slanju ili fiskalizaciji | screenshot izmjeren, 1:1 nije potvrđen |
| 18 | Postavke | navigacija i sigurnosne kopije | test obnove |
| 19 | Profil tvrtke | izdavatelj i podaci | test trajnog spremanja |
| 20 | Tamna tema | Android native tema | test kontrasta i rubova |

## Kritični scenariji čuvanja dokumenata

1. Upiši klijenta, datume i napomenu → ubij proces → ponovo otvori → nastavi nacrt.
2. Dodaj više stavki i PDV stope → prekini proces → provjeri količine, cent iznose i stavke.
3. Spremi nacrt → zatvori aplikaciju → izmijeni nacrt → potvrdi → provjeri jedinstvenu numeraciju.
4. Potvrdi račun → izmijeni profil klijenta/tvrtke → PDF mora zadržati izvorne podatke.
5. Promijeni klijenta ili datum više puta → zatvori proces → mora ostati posljednji unos.
6. Kreiraj novi dokument dok radni nacrt postoji → odustani i vrati se, ništa ne smije nestati.
7. Dodaj uplatu → provjeri iznos, nepodmireni ostatak i izvještaje.
8. Izvezi backup, na zasebnoj testnoj instalaciji uvezi ispravnu kopiju; pogrešnu lozinku i oštećenu datoteku odbaci bez izmjene baze.
9. Simuliraj prekid tijekom rotacije lokalne kopije → barem prethodna ispravna kopija mora biti dostupna.
10. Testiraj nestanak prostora, prisilno zatvaranje, nadogradnju APK-a i oporavak nakon restarta.

Sve navedeno zahtijeva ručnu/instrumentacijsku verifikaciju na uređajima. Sama Android CI kompilacija nije dokaz svih scenarija. Ne obećavati nemoguć apsolutni izostanak gubitka podataka bez sigurnosne kopije izvan uređaja.
