# Fakturiva — Android ↔ web povezivanje (PR #28)

Datum: 9. studenoga 2026.  
Primarna domena: **https://fakturiva.eu/**  
Status: implementacija u razvojnom PR-u; **nije potvrđena produkcijska dostupnost hostinga**.

## Jedan poslužiteljski identitet

- Web `register.php` stvara korisnički račun s korisničkim imenom, web PIN-om i
  snažnom lozinkom poslovnih podataka.
- Android `AccountBridge.register` poziva isti `web/api.php` s akcijom
  `register`, stvara odvojeni lokalni PIN i šalje prvu šifriranu poslovnu
  kopiju uz `expected_revision=0`. Web i Android ne smiju zasebno stvoriti
  dva računa za isto korisničko ime.
- Android `AccountBridge.restoreLogin` autentificira korisnika istim
  korisničkim imenom i lozinkom kao web, preuzima postojeći FAK1 backup,
  verificira SHA-256 i AES-GCM autentifikaciju, vraća ga u novu lokalnu
  SQLite radnu kopiju i tek tada pohranjuje potvrđenu reviziju.
- Za stare lokalne račune ostaju PIN prijava i izričito povezivanje preko
  postavki. Novi login **ne smije automatski prebrisati** postojeći lokalni profil.

## Podatkovni tok

1. Android radi nad izoliranom lokalnom SQLite bazom profila.
2. Nakon svake uspješne promjene `Store.snapshotAfterWrite` trajno označava
   nesinkronizirani zapis te `CloudMirror.runPending` asinkrono šalje
   šifriranu FAK1 presliku na poslužitelj (HTTPS).
3. Web API atomarno zapisuje novi JSON revizijski dokument samo ako vrijedi
   `expected_revision`. Odgovor s kontrolnim zbrojem mora se podudarati
   s poslanim bajtovima prije označavanja promjene kao sinkronizirane.
4. Ako je poslužiteljska revizija novija, automatsko prepisivanje se
   **zaustavlja**. Korisnik mora provjeriti sukob i izričito obnoviti podatke.
5. Web `dashboard.php`, `finance.php` i `tax-reports.php` rade
   nad istim izoliranim poslužiteljskim računom; za čitanje sadržaja FAK1
   potrebna je lozinka. Windows WebView otvara web-hosted sučelje.
6. Udaljeni backup **nije isto što i trajno potvrđena sinkronizacija**:
   promjene s oznakom `dirty` mogu se izgubiti pri deinstalaciji prije
   potvrde prijenosa. Korisniku ne obećavati oporavak tih nepotvrđenih izmjena.

## Ograničenja i obvezne provjere

- Domena i `/api.php?action=health` nisu bili dostupni za pouzdanu
  vanjsku potvrdu iz ovog radnog okruženja. **Kod u repozitoriju nije
  dokaz da je web aplikacija objavljena na fakturiva.eu.**
- Hosting zahtijeva valjan DNS/TLS, PHP 8.1+, OpenSSL, zapisiv i iz javnog
  weba **nedostupan** `storage/`. Na Nginxu dodati `deny all` za
  `/storage/`; Apache `.htaccess` ne štiti Nginx. U idealnom slučaju
  šifrirane zapise i glavni ključ držati izvan javnog document root-a.
- Provjeriti revizijske sukobe na dvama uređajima, istek sesije, mrežni
  prekid tijekom spremanja, obnovu nakon deinstalacije, validaciju i
  rollback oštećenih kopija, ulazne podatke, kontrole pristupa tenantima
  te stvarni hosting backup glavnog ključa.
- Web trenutačno prikazuje poslovne podatke iz šifriranog snapshota;
  **nije dokazano potpuno dvosmjerno uređivanje svih podataka kroz web i
  Android**. Ne označavati cijeli proizvod usklađenim dok edit tokovi
  nisu implementirani i testirani.
- Referenca #03 iz vlasnikova ZIP-a pokazuje e-adresu i lozinku.
  QA harness sada snima `loginServerScreen` umjesto stranice zaštite
  uređaja; kvantitativni pixel-perfect rezultat **još nije potvrđen**.
- Reference #17 (eRačuni), fiskalizacija, postojeće porezne funkcije,
  KPR i PO-SD **ostaju u projektu**. Nema tvrdnje da su hrvatske
  produkcijske fiskalne/eRačun integracije certificirane bez neovisne
  provjere i potrebnih produkcijskih vjerodajnica.

## Reproduktivni testovi prije produkcije

1. Na testnom hostingu registrirati korisnika preko weba, prijaviti se
   u Androidu istim podacima, postaviti lokalni PIN i potvrditi reviziju 1.
2. Iz Androida izraditi nacrt računa, provjeriti da `dirty` postane
   `false` tek nakon server ACK-a te prikazati zapis kroz web `finance.php`.
3. Odjaviti se, napraviti čistu instalaciju na drugom emulatoru, unijeti
   isti web račun i provjeriti identične račune, klijente, iznose i statuse.
4. Napraviti konkurentne promjene na dvama uređajima; potvrditi HTTP 409
   i da noviji podatci nisu izbrisani.
5. Potvrditi da korisnik B ne može pročitati niti obnoviti kopiju korisnika A,
   uključujući revizijsku povijest i administratorski pregled.
6. Pokrenuti sva GitHub Actions pravila: Android compile/lint, web PHP i HTTP
   smoke, Windows preview, stvarne emulator screenshotove i responsive matricu.

**Novi Android AAB/APK ne označavati Google Play produkcijski potpisanima dok
ispravan release ključ, Play Console verzija i provjere nisu potvrđeni.**
