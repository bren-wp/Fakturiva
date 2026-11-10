# Fakturiva web — instalacija i sigurnosna pravila

**Domena:** https://fakturiva.eu/ · PHP 8.1+ · HTTPS · JSON pohrana izvan javnog pristupa.

## Jedna e-adresa i lozinka za Android i web

- Novi računi registriraju se na `/register.php` ili u Android aplikaciji unosom valjane e-adrese, lozinke od najmanje 12 znakova i potvrde lozinke.
- Prijava je na `/login.php` pomoću iste e-adrese i lozinke. Korisnik može otvoriti `/dashboard.php`, `/finance.php` i `/tax-reports.php`.
- `/api.php` koristi iste poslužiteljske račune za Android autentifikaciju i prijenos AES-GCM šifriranih preslika. Lokalne promjene moraju dobiti potvrdu revizije.
- Stare adrese za dodatne oblike prijave preusmjeravaju na `/login.php` i više ne prikazuju obrasce. Za ranije korisničko ime koje nije e-adresa, postojeći račun može se prijaviti istom poslužiteljskom lozinkom; novi račun mora imati e-adresu.
- Android zadržava postojeći profil i dokumente prilikom migracije vjerodajnice poslije uspješne server autentifikacije. Izmjene nisu potpuno dvosmjerni CRUD: web čita posljednju potvrđenu šifriranu presliku, a Android radi offline i sinkronizira po revizijama.

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
bash quality/web-hosting/http-smoke.sh
bash quality/web-hosting/web-form-http.sh
```

## Sigurnosne i funkcionalne granice

Nema automatskog spajanja paralelnih izmjena između dva uređaja. Konflikt
revizije zaustavlja prepisivanje, a nepotvrđena lokalna promjena može se izgubiti
pri uklanjanju aplikacije. Promjena lozinke nakon izrade šifrirane kopije blokirana
je dok se ne implementira provjereno ponovno šifriranje svih verzija. Nije
potvrđena potpuna produkcijska usklađenost niti stvarno certificirano slanje
eRačuna ili fiskalizacije.
