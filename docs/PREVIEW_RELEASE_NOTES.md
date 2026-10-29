# Fakturiva v0.7.0-preview.1 — native Android UI refresh

**Računi i ponude, jednostavno.** Offline-first Android aplikacija za klijente, račune, ponude, katalog, naplatu, izvještaje i PDF dokumente.

### Novo
- Potpuno preuređen ekran klijenta: avatar, broj računa i stvarno naplaćen iznos, poslovni podaci, povijest dokumenata.
- Preuređen ekran izvještaja: točno fakturirani i naplaćeni iznosi te šestomjesečni graf na temelju stvarnih SQLite evidencija.
- Katalog artikala i usluga: funkcionalna pretraga te uređivanje naziva, cijene i PDV stope bez promjene povijesnih računa i ponuda.
- Grupirane postavke s funkcionalnim redovima i prebacivanjem teme.
- Native pozdravni ekran s originalnim Fakturiva brendom i ilustracijom dokumenta izrađenom Android Canvasom.
- Ujednačen prikaz koraka izrade računa i ponuda.
- Ispravljena navigacija na prethodni ekran i sigurnost prilikom otvaranja novog računa s nedovršenim nacrtom.
- eRačuni i fiskalizacija: točni pokazatelji profila i jasne upute; ne prikazuje se nepostojeće slanje.
- Dodani instrumentacijski testovi koji potvrđuju da uređivanje kataloga ne mijenja izdane račune.

### Android paketi
- `Fakturiva-v0.7.0-preview.1-debug.apk` — debug-potpisan APK za testiranje.
- `Fakturiva-v0.7.0-preview.1-DEBUG-SIGNED.aab` — AAB s debug ključem, NIJE produkcijski Play Store upload.
- `Fakturiva-v0.7.0-preview.1-UNSIGNED.aab` — nepotpisani release bundle.
- `SHA256SUMS.txt`.

### Ograničenja
Ovo je razvojna verzija. Nije dokazano pixel-perfect 1:1 svih 20 originalnih PNG ekrana niti odsutnost svih grešaka. Za produkciju nedostaju provjere na fizičkim Android uređajima, pravilno produkcijsko potpisivanje i, ovisno o poslovanju, fiskalizacija te strukturirani eRačun. PDF nije fiskalizirani račun niti eRačun.

Redovito izvozite verificiranu šifriranu kopiju izvan telefona. Nulti rizik od gubitka podataka na jednom uređaju nije moguć.

**Fakturiva by Brendigo.**

### Provjera izdanja

APK i AAB objavljuju se kao *razvojni pretpregled* uz SHA-256 sažetke. Za korištenje u proizvodnji potrebni su dodatni testovi na fizičkim Android uređajima i produkcijski upload ključ. Glavni README sadrži stvarne zaslone aplikacije.
