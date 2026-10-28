# Fakturiva v0.4.0 — Android pretpregled

**Računi i ponude, jednostavno.**

Fakturiva donosi nativno, pregledno Android sučelje za pripremu ponuda i računa, kupce i poslovne partnere, evidenciju uplata, katalog usluga i lokalni PDF izvoz.

### Što je novo
- **Detaljan profil tvrtke ili obrta:** naziv, OIB, PDV identifikacijski broj, adresa, grad, država, poštanski broj, e-mail, telefon, web, IBAN, banka, SWIFT, registar, poslovna jedinica, operater, porezna napomena i podaci o plaćanju.
- **Klijenti:** uređivanje, OIB/PDV ID, adresa, telefon i e-mail, uz povijest računa.
- **Profesionalniji PDF:** izdavatelj i klijent, datumi izdavanja i isporuke, dospijeće, stavke, porez po stopama, plaćanja i napomene.
- **Veća pouzdanost podataka:** nedestruktivna SQLite v2→v3 migracija, WAL zapisivanje, stroži datum/OIB/IBAN, konzistentna sigurnosna kopija i oporavak nacrta.
- **Dosljedan identitet Fakturiva:** prepoznatljiva ikona, svijetla/tamna tema i izvorni Android elementi.

### Paketi izdanja
- `Fakturiva-v0.4.0-preview-debug.apk` — Android debug-potpisan APK za isprobavanje.
- `Fakturiva-v0.4.0-preview-DEBUG-SIGNED.aab` — AAB s privremenim debug potpisom, **nije produkcijski Google Play upload**.
- `Fakturiva-v0.4.0-preview-UNSIGNED.aab` — nepotpisani AAB, potrebno produkcijsko potpisivanje vlastitim ključem.
- `SHA256SUMS.txt` — provjera integriteta datoteka.

### Očuvanje podataka
Fakturiva radi bez mrežnog računa i čuva zapise lokalno uz automatske šifrirane kopije i lozinkom zaštićen izvoz. **Ni jedna aplikacija ne može jamčiti nulti rizik od gubitka dokumenata ako su sve kopije na istom uređaju.** Zaštitite svoje podatke redovitim izvozom šifrirane kopije na neovisan medij ili pohranu te povremenom provjerom obnove.

### Važne informacije
Ovo je **razvojni pretpregled**, a ne verificirani računovodstveni proizvod. PDF nije strukturirani eRačun i ne predstavlja dokaz fiskalizacije. Elektronička fiskalizacija i zakonski obvezna eRačun razmjena nisu implementirane. Pixel-perfect 1:1 provjera svih 20 referentnih ekrana ostaje otvorena dok se ne dovrše testovi stvarnih emulatorskih snimki i vizualna usporedba.

**Fakturiva by Brendigo** · Android 7.0+.
