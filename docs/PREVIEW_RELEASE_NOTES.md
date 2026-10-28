# Fakturiva v0.4.0 — pretpregled za Android

**Računi i ponude, jednostavno.**

Fakturiva je lokalna Android aplikacija za pripremu računa i ponuda, klijente, katalog artikala/usluga, evidenciju uplata i PDF dokumente. Prepoznatljiv vizualni identitet: tamnoplava, električno plava, tirkizna, bijela te svijetla i tamna tema.

### Novo u 0.4
- Preciznija provjera hrvatskog OIB-a i kalendarskih datuma dokumenta; dospijeće ne može prethoditi datumu izdavanja.
- SQLite s WAL zapisivanjem i trajnim (FULL) sinkroniziranjem za otpornije zapisivanje.
- Dosljedna snimka cijele baze prilikom šifriranog izvoza i automatskog oporavka.
- Provjera obračuna u centima, validacije podataka i šifrirane sigurnosne kopije u CI-u.
- Dodatno usklađeni nazivi i statusi računa/ponuda.
- Poseban postupak za snimanje stvarnih prikaza Android emulatora radi vizualne usporedbe s 20 referenci.

### Sadržaj izdanja
- **Fakturiva-v0.4.0-preview-debug.apk** — debug-potpisan Android instalacijski paket, samo za testiranje.
- **Fakturiva-v0.4.0-preview-DEBUG-SIGNED.aab** — AAB potpisan privremenim Android debug ključem, **nije Play Store production upload**.
- **Fakturiva-v0.4.0-preview-UNSIGNED.aab** — nepotpisani release AAB za daljnje sigurno produkcijsko potpisivanje; **nije spreman za Play Store bez vašeg ključa**.
- **SHA256SUMS.txt** — SHA-256 sažeci navedenih datoteka.

### Očuvanje poslovnih podataka
Aplikacija čuva dokumente u lokalnoj bazi, omogućuje nastavak prekinutih nacrta te rotirajuće lokalne šifrirane kopije. **Ni jedan sustav s podacima samo na telefonu ne može jamčiti da račun nikada neće biti izgubljen** pri gubitku uređaja, kvaru memorije ili brisanju aplikacije. Redovito izvođenje i provjera šifrirane sigurnosne kopije izvan uređaja ostaje obvezna preporuka.

### Važna ograničenja
Ovo je razvojni pretpregled, ne računovodstveno certificirana aplikacija. **PDF nije strukturirani eRačun niti potvrda fiskalizacije.** Zakonski obvezna fiskalizacija, razmjena eRačuna, certifikati i odgovarajuća mrežna komunikacija nisu implementirani. Vizualna 1:1 podudarnost sa svim referencama nije potvrđena dok se ne obavi usporedba emulatorskih snimki i ispravci.

**Fakturiva by Brendigo**.
