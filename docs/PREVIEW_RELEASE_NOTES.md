# Fakturiva v0.8.0-preview.1 — Native Android UI i stvarna poslovna funkcionalnost

**Računi i ponude, jednostavno.**

### Novo u v0.8
- Usklađivanje prema 20 originalnih korisničkih referenci (1179 × 2556 px): položaji, boje, ikone, forme i tamna tema.
- Nova prava SVG-vector Android ikona za postavke umjesto ikone trotočja u navigaciji.
- Preglednija početna stranica: profil tvrtke, iznos tekućih uplata, stvarna usporedba s prethodnim mjesecom kada postoje podaci, stvarni kumulativni graf uplata prema datumu, pregled posljednjih računa.
- Računi i ponude: koraci izrade imaju zajedničku strukturu, realan status numeriranja dokumenta, odabir klijenta i način plaćanja koji vodi na profil izdavatelja, zasebne dodatne informacije i dostupnu fiksnu tipku za nastavak.
- Stavke dokumenta: sada se stvarno uređuju, brisanje traži potvrdu, porezni iznosi izračunavaju se iz stvarnih stavki; nema prikazivanja izmišljenih iznosa.
- Kontrast pozadina ikona u tamnoj temi, pristupačniji opisi navigacijskih kontrola.
- Android instrumentacijski testovi: graf stvarnih uplata, odvajanje mjeseci i odbijanje neispravnih zapisa.
- Dokumentiran audit svih 20 izvornih zaslona.

### Dostupni razvojni paketi
- `Fakturiva-v0.8.0-preview.1-debug.apk` — Android testni APK potpisan debug ključem.
- `Fakturiva-v0.8.0-preview.1-DEBUG-SIGNED.aab` — debug-potpisani testni bundle.
- `Fakturiva-v0.8.0-preview.1-UNSIGNED.aab` — nepotpisani release bundle.
- `SHA256SUMS.txt` — provjera integriteta.

### Ograničenja i zaštita dokumenata
Ovo **nije potvrđena produkcijska verzija** i nije dokazana potpuna pixel-perfect 1:1 podudarnost svih 20 referenci. Dio razlika nastaje iz stvarnog datuma, zapisa testne baze i sistemske statusne/navigacijske trake Androida. Konceptni ekran vanjske prijave nije implementiran: Fakturiva je lokalna aplikacija bez servisa za korisničke račune. PDF nije strukturirani eRačun niti potvrda fiskalizacije. Ne koristiti kao zamjenu za zakonski obvezne integracije dok se one ne izvedu i provjere.

**Ne deinstalirajte prethodnu razvojnu verziju koja sadrži jedine primjerke računa.** Debug potpisi se mogu razlikovati između izdanja. Najprije izvezite šifriranu sigurnosnu kopiju izvan uređaja, sačuvajte njezinu lozinku i provjerite oporavak na odvojenoj testnoj instalaciji. Za pouzdane Play produkcijske nadogradnje potrebno je stabilno produkcijsko potpisivanje.

Fizički Android uređaji, recovery testovi na više verzija OS-a i završna revizija zakonskih zahtjeva ostaju uvjet produkcijskog odobrenja.

**Fakturiva by Brendigo.**
