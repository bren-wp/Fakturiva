# Fakturiva v0.5.0 — nativni Android pretpregled

**Računi i ponude, jednostavno.**

Fakturiva čuva evidenciju vaših poslovnih dokumenata lokalno na Android uređaju — uz pregledno sučelje na hrvatskom jeziku, klijente, ponude, račune, katalog usluga, naplatu i PDF izvoz.

## Poboljšanja verzije 0.5
- **Ujednačeniji dizajn:** novi tamni Fakturiva splash zaslon, preglednije kartice i razmaci početne stranice, profil izdavatelja s brendiranom karticom i trajno vidljivim gumbom za spremanje.
- **Potpuni podaci na dokumentima:** naziv i identitet tvrtke/obrta, OIB, PDV ID, puna adresa, banka, IBAN, SWIFT, klijent, kontakt, datumi, stavke i iznosi.
- **Višestranični PDF:** dugi nazivi i adrese više se ne odsijecaju; tekst prelazi na novu stranicu i zadržava unose.
- **Evidencija računa i ponuda:** nacrti, kontrolirano numeriranje, konverzija ponude u račun, djelomične uplate i izvještaji.
- **Očuvanje podataka:** SQLite s transakcijama i strožim zapisivanjem, šifrirani radni nacrti, rotirajuće lokalne kopije, upozorenje ako kopiranje ne uspije te prijenosni AES-GCM izvoz.
- **Provjere kvalitete:** automatski build/lint, matematički i kriptografski testovi, testovi baze/PDF-a na Androidu i mjerljiva usporedba svih 20 izvornih ekrana.

## APK i AAB
- `Fakturiva-v0.5.0-preview-debug.apk`: instalacijski Android APK potpisan privremenim debug ključem za testiranje.
- `Fakturiva-v0.5.0-preview-DEBUG-SIGNED.aab`: debug-potpisani AAB, nije produkcijski Google Play upload.
- `Fakturiva-v0.5.0-preview-UNSIGNED.aab`: nepotpisani release AAB; za trgovinu potreban je vaš produkcijski upload ključ.
- `SHA256SUMS.txt`: integritet APK/AAB datoteka.

**Napomena:** Nema fiskalizacije ni razmjene strukturiranih eRačuna. Lokalni PDF nije eRačun niti dokaz fiskalizacije. Sadržaj je razvojni pretpregled dok se ne dovrše testovi i zakonska provjera. Lokalna pohrana ne može jamčiti očuvanje podataka ako uređaj nestane ili se podaci izbrišu; redovito izvozite šifriranu kopiju izvan telefona.

Fakturiva · Brendigo.
