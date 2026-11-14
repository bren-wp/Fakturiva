# Fakturiva web — instalacija i sigurnosna pravila

**Domena:** https://fakturiva.eu/ · PHP 8.1+ · HTTPS · JSON pohrana izvan javnog pristupa.

## Jedna e-adresa i lozinka za Android i web

- Novi računi registriraju se na `/register.php` ili u Android aplikaciji unosom valjane e-adrese, lozinke od najmanje 12 znakova i potvrde lozinke.
- Prijava je na `/login.php` pomoću iste e-adrese i lozinke. Korisnik može otvoriti `/dashboard.php`, `/finance.php` i `/tax-reports.php`.
- `/api.php` koristi iste poslužiteljske račune za Android autentifikaciju i prijenos AES-GCM šifriranih preslika. Lokalne promjene moraju dobiti potvrdu revizije.
- Stare adrese za dodatne oblike prijave preusmjeravaju na `/login.php` i više ne prikazuju obrasce. Za ranije korisničko ime koje nije e-adresa, postojeći račun može se prijaviti istom poslužiteljskom lozinkom; novi račun mora imati e-adresu.
- Android zadržava postojeći profil i dokumente prilikom migracije vjerodajnice poslije uspješne server autentifikacije. Novi web poslovni uređivač na `/business.php` može **uređivati tvrtku, klijente i katalog, evidentirati stvarno primljene uplate te na `/documents.php` izrađivati, uređivati i brisati nacrte računa i ponuda, potvrđivati dokumente i označivati prihvaćene ponude**, pri čemu stvara novu FAK1 reviziju iste baze koju Android koristi. Android pri prijavi automatski provjerava server i preuzima novije podatke samo ako je lokalna kopija čista i nema otvorenog radnog nacrta ili aktivnog konflikta. Windows je WebView2 omotač ovog portala.
- `POST /api.php` s `{"action":"status"}` i valjanim Bearer tokenom vraća samo `revision`, `has_backup` i `saved_at`, bez prijenosa pune preslike. Odvojeni `load` preuzima šifrirane bajtove samo kad je potreban. Starije verzije servera ostaju dostupne preko kontroliranog `load` fallbacka.
- Web evidencija uplata prihvaća samo potvrđene račune s nepodmirenim iznosom, datumom i načinom primitka. Server računa preostali dug iz dokumenta i prethodnih uplata; ne vjeruje iznosima ili statusu koje je poslao preglednik. Djelomična uplata ostavlja račun otvorenim; zadnja uplata zaključuje status `PAID`. Svaka izmjena prolazi istu provjeru revizije i povijesti kao Android.
- `/documents.php` nudi izradu, uređivanje i brisanje **samo nacrta** računa i ponuda. Potvrda dodjeljuje brojeve iz zajedničkih sekvenci, smrzava podatke izdavatelja te obračunava stavke i PDV prema Android Money pravilima. Prihvat ponude i izrada novog nacrta računa iz ponude čuvaju izvorne dokumente. Ne dopušta brisanje niti uređivanje potvrđenih dokumenata.
- Kod statusa 409 obrazac može prikazati korisniku samo dopuštena nespremljena polja za ručnu usporedbu; nikad ne zapisuje lozinku, ne sprema niti automatski primjenjuje konfliktni sadržaj.
- Web i Android ne rade istovremeno spajanje pojedinačnih poslovnih zapisa. Web izdaje novu verziju samo ako se `expected_revision` podudara, a sukob se mora ručno riješiti uz očuvanje lokalne i povijesne kopije.

## Arhitektura pohrane

`lib.php` zadržava postojeće korisničke i API funkcije. Enkripcija AES-GCM, lokacija privatne pohrane i atomski zapis izdvojeni su u `storage-engine.php` bez promjene postojećeg formata ili ključeva. Izolirani test `quality/web-hosting/storage-engine-smoke.php` ne pristupa stvarnim korisničkim računima. Nikada ne premještati samo `data/` bez odgovarajućeg `secret.php`.

## Instalacija

Sadržaj mape `web/` postavite u document root HTTPS domene uz PHP 8.1+,
`openssl`, `json`, odgovarajuće dozvole za zapis i pouzdane server konfiguracije.
Prije produkcije provjerite:
1. `storage/data` i glavni ključ `storage/secret.php` nisu dostupni HTTP-om. Na Nginxu izričito konfigurirajte zabranu pristupa jer `.htaccess` ne vrijedi.
2. Šifriranu pohranu i ključ spremite u testirane nezavisne sigurnosne kopije pomoću `web/cli/offsite-backup.php` i crona.
3. `GET /api.php?action=health`, TLS, registraciju, prijavu, opoziv sesija i brisanje računa verificirajte na stvarnom hostingu.
4. Postavite sigurnu administratorsku lozinku preko `FAKTURIVA_ADMIN_PASSWORD_HASH` i po mogućnosti dodatnu mrežnu zaštitu `/admin.php`.
5. Provjerite rokove čuvanja računa, Politiku privatnosti i Google Play Data safety prije javne distribucije.

## API i testiranje

```bash
find web -name '*.php' -print0 | xargs -0 -n1 php -l
php quality/web-hosting/smoke.php
php quality/web-hosting/web-auth-smoke.php
php quality/web-hosting/business-sync-smoke.php # uključuje uplate, sve CRUD operacije nad nacrtima, iznos/PDV i sukobe revizija
bash quality/web-hosting/http-smoke.sh
bash quality/web-hosting/web-form-http.sh
```

## Sigurnosne i funkcionalne granice

Web `business.php` otključava FAK1 podatke u PHP RAM-u samo za
vrijeme jednog HTTPS zahtjeva; ovo nije „zero knowledge” u odnosu na
server operatora. Dokumente nije dopušteno spremati u web session,
logove ni nešifrirane temp datoteke. Stvarnu HTTPS produkcijsku
instalaciju zasebno treba potvrditi; promjene GitHub repozitorija same
ne postavljaju datoteke na `fakturiva.eu`.

Nema automatskog spajanja paralelnih izmjena između dva uređaja. Konflikt
revizije zaustavlja prepisivanje, a nepotvrđena lokalna promjena može se izgubiti
pri uklanjanju aplikacije. Promjena lozinke nakon izrade šifrirane kopije blokirana
je dok se ne implementira provjereno ponovno šifriranje svih verzija. Nije
potvrđena potpuna produkcijska usklađenost niti stvarno certificirano slanje
eRačuna ili fiskalizacije.
