# Fakturiva v0.7.0-preview.2 — native Android UI refresh

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

### Stabilnost i vizualna dorada u preview.2
- Ispravljeno čuvanje unesenih podataka u obrascima klijenata, računa i ponuda nakon pogreške validacije.
- Zaštita šifriranih radnih nacrta: promjene stavki se poništavaju ako trajni zapis ne uspije.
- Novi korisnički tok za dodavanje klijenta tijekom uređivanja dokumenta.
- Ispravan CSV izvoz prema filtriranom mjesecu i zaštita od formula čak i uz početne razmake.
- Nativni plutajući gumbi na popisima računa, ponuda, klijenata i kataloga.
- Pretraživanje klijenata i uklanjanje praznih razmaka iza filtriranih kartica.
- Novi Android regresijski testovi šifriranja nacrta i mjesečnog CSV izvoza.
- Poboljšana izmjerena vizualna podudarnost svih 20 zaslona (RGB MAE prosjek 16,30 za pretposljednju emulatorsku snimku; završna korekcija filtriranih razmaka unesena je nakon nje).

### Android paketi
- `Fakturiva-v0.7.0-preview.2-debug.apk` — debug-potpisan APK za testiranje.
- `Fakturiva-v0.7.0-preview.2-DEBUG-SIGNED.aab` — AAB s debug ključem, NIJE produkcijski Play Store upload.
- `Fakturiva-v0.7.0-preview.2-UNSIGNED.aab` — nepotpisani release bundle.
- `SHA256SUMS.txt`.

### Nadogradnja bez gubitka podataka
Razvojni APK i AAB koriste Android debug potpis. **Debug ključ između izdanja nije zajamčeno isti**: novu razvojnu verziju zato možda nećete moći instalirati preko stare bez pogreške potpisa. **Ne brišite postojeću aplikaciju s jedinim primjerkom računa ili ponuda.** Najprije izvezite šifriranu prijenosnu kopiju na sigurno mjesto izvan telefona, sačuvajte lozinku i provjerite oporavak na odvojenoj testnoj instalaciji. Produkcijske nadogradnje moraju koristiti stabilan, ispravno zaštićen potpisni ključ.

### Ograničenja
Ovo je razvojna verzija. Nije dokazano pixel-perfect 1:1 svih 20 originalnih PNG ekrana niti odsutnost svih grešaka. Za produkciju nedostaju provjere na fizičkim Android uređajima, pravilno produkcijsko potpisivanje i, ovisno o poslovanju, fiskalizacija te strukturirani eRačun. PDF nije fiskalizirani račun niti eRačun.

Redovito izvozite verificiranu šifriranu kopiju izvan telefona. Nulti rizik od gubitka podataka na jednom uređaju nije moguć.

**Fakturiva by Brendigo.**

### Provjera izdanja

APK i AAB objavljuju se kao *razvojni pretpregled* uz SHA-256 sažetke. Za korištenje u proizvodnji potrebni su dodatni testovi na fizičkim Android uređajima i produkcijski upload ključ. Glavni README sadrži stvarne zaslone aplikacije.


### Završne provjere preview.2
Gradle izgradnja, osnovni testovi i Android emulator instrumentacijski testovi uspješno su izvršeni. Provjereno je 20 stvarnih emulator PNG-ova uz RGB MAE prosjek 16,31/255 (testni datum, vrijeme i sadržaj utječu na rezultat). Android fizički uređaji ostaju izvan dosega ovih automatiziranih provjera, a ovaj paket nije odobren za produkciju.
