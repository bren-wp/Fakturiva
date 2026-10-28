# Fakturiva

**Računi i ponude, jednostavno.**

Fakturiva je nativna Android aplikacija za samostalno poslovanje: priprema računa i ponuda, evidencija klijenata, katalog usluga, naplate te izvoz čitljivih PDF dokumenata. Vaši podaci ostaju na uređaju.

### Što dobivate

- **Dokumenti:** nacrti računa i ponuda, stavke, obračun PDV-a, numeriranje pri potvrdi i PDF.
- **Klijenti i usluge:** lokalna evidencija bez registracije ili oblaka.
- **Naplata i pregled:** knjiženje uplata i pregled iznosa.
- **Izvoz:** ugrađeni Android sustav za spremanje i dijeljenje PDF-a.
- **Privatnost:** nema oglasa, analitike, vanjskih SDK-ova ni dozvole za internet.
- **Brending:** Fakturiva; tamnoplava, plava i tirkizna.

> **Pravni status:** Ovo je razvojna verzija. PDF račun nije strukturirani eRačun. Nema mrežne fiskalizacije, slanja eRačuna, certifikata ni potvrđene zakonske usklađenosti. Takvu funkcionalnost treba zasebno integrirati prije upotrebe za obvezno izdavanje. Nemojte koristiti dokumente iz ove verzije kao zamjenu za zakonski obvezan fiskalizirani račun.

### Tehnički temelj

100 % nativni Android framework: Java, SQLite, PdfDocument, Storage Access Framework i Android Keystore/JCA za šifriranu sigurnosnu kopiju. Nema Composea, React Nativea, Fluttera, webviewa ni mrežnih servisa. Gradle i Android Gradle Plugin su **isključivo alati za izgradnju**, a nisu runtime ovisnosti.

- Android 7.0+ (minSdk 24), targetSdk 35.
- Java 17, Android Gradle Plugin 8.7.3 i Gradle 8.9.
- U Android Studiju otvoriti korijensku mapu i pokrenuti `app`.
- CI gradi debug APK kao GitHub Actions artifact; release AAB zahtijeva pravilno potpisivanje.
- Za gradnju iz terminala uz instaliran SDK i Gradle 8.9: `gradle :app:assembleDebug`.

### Upute

Pri prvom pokretanju upišite podatke tvrtke u Postavkama. Zatim kreirajte klijenta, artikl ili uslugu i nacrt dokumenta. Nakon provjere možete potvrditi dokument, spremiti PDF i upisati uplatu. Prije svake obnove telefona izvezite sigurnosnu kopiju i provjerite da je možete vratiti.

### Autorski identitet

Brend **Fakturiva** · Autor **Brendigo**.
