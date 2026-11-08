# Fakturiva — vlastiti PHP/JSON hosting

**Izbor, a ne obveza:** Android Fakturiva radi offline. Ovaj modul služi
samo za ručni izvoz/uvoz šifrirane sigurnosne kopije korisnika koji
izričito želi koristiti **vlastiti** HTTPS hosting. Nema Google/Apple
prijave, SQL baze ni automatskog slanja podataka.

## Instalacija na root bilo koje domene

Prekopirajte **sadržaj** direktorija `server/` (ne omotnicu `server/`)
u root svoje HTTPS domene ili u poddirektorij, primjerice
`public_html/` ili `public_html/fakturiva/`.

```text
public_html/
  api.php
  lib.php
  privacy.php
  delete-account.php
  storage/
    .htaccess
    .gitignore
    data/
      .htaccess
      index.php
      accounts/         (automatski se kreira)
      index/            (automatski se kreira)
      limits/           (automatski se kreira)
    secret.php          (automatski se kreira, nije u Git repozitoriju)
```

Potrebno je **PHP 8.1+** s `openssl`, `json`, `password_hash`,
pravima za zapisivanje u `storage/`, aktivnim **HTTPS/TLS** certifikatom
i dovoljno prostora. Ne pohranjujte osobne ili financijske podatke na
nezaštićeni hosting. Poslužitelj prvi put generira 256-bitni tajni
ključ u `storage/secret.php` i nikad ga ne smije prebrisati ili ponovno
generirati ako podaci već postoje.

### Zaštita datoteka

- JSON datoteke su AES-256-GCM šifrirane, svaki zapis s nasumičnim IV-jem.
- `.htaccess` blokira HTTP pristup `storage/` na Apacheu.
- Nginx **ne čita** .htaccess: administrator mora eksplicitno blokirati
  URL putanju `/storage/` (primjerice `location ^~ /storage/ { deny all; }`).
- Najbolje je premjestiti `storage/` **izvan javnog webroota** gdje
  hosting to omogućuje. U ovoj distribuciji omogućena je i root instalacija
  s obrambenim šifriranjem, ali je obavezno provjeriti da
  `https://vasa-domena.hr/storage/secret.php` ne izlaže izvorni kod
  i da `/storage/data/` nije moguće pregledavati ili preuzimati.
- API odbija nešifrirani HTTP. Ako ste iza vlastitog pouzdanog HTTPS
  reverse proxyja, odredite `FAKTURIVA_TRUSTED_PROXY_IP` prema
  stvarnom `REMOTE_ADDR` tog proxyja. Nikada ne postavite tu vrijednost
  na proizvoljan IP koji kontrolira klijent.
- Ne pohranjujte `storage/` u javni Git repo, javne backup direktorije
  niti na CDN.
- Provjerite da PHP služi `.php` kao izvršni kod. Nikad ne koristite
  web poslužitelj koji servira PHP izvore kao tekst.

Ako se datoteke nalaze u rootu, adresa za unos u Fakturivi:
`https://vasa-domena.hr/` (automatski koristi `api.php`).
U podmapi: `https://vasa-domena.hr/fakturiva/`.

Javni ping: `GET https://vasa-domena.hr/api.php?action=health`
odgovara podacima o verziji; ne otkriva račune.

## Android — ručna uporaba

1. Otvorite Fakturiva → Postavke → **Vlastiti hosting**.
2. Unesite svoj HTTPS URL, korisničko ime i *zasebnu* poslužiteljsku
   lozinku duljine najmanje 12 znakova. Kratki PIN ostaje isključivo
   na Android uređaju.
3. Registrirajte poslužiteljski račun. Registracija **ne šalje** fakture.
4. Odaberite **Pošalji šifriranu sigurnosnu kopiju** kada želite poslati
   podatke. Ako račun već ima kopiju, bit će zamijenjena tek nakon
   jasne korisničke potvrde. API sprečava natjecanje s paralelnim
   zapisivanjem kroz provjeru očekivane revizije.
5. Na drugom uređaju registrirajte **drugi lokalni PIN profil**, zatim
   prijavite isti *poslužiteljski* račun i odaberite **Preuzmi i vrati**.
   Izvoz se ne može spojiti s trenutačnim podacima automatski:
   vraćanje je zamjena samo aktivne lokalne baze, uz lokalni snapshot
   prije zamjene. Ne koristite dvije naprave koje istodobno mijenjaju
   iste račune bez plana usklađivanja.

**Veličina:** `SecureBackup` prihvaća do približno 25 MB šifriranog
sadržaja. Zbog Base64 i JSON proširenja, hosting mora omogućiti
`post_max_size` i odgovarajuća HTTP ograničenja od barem 40 MB.
Neki shared hostinzi to ne dopuštaju. Modul tada prijavljuje grešku,
ne odbacuje ili skraćuje račune.

**Opasnost od sukoba:** backend *uvijek* veže operaciju na korisnika
provjerene nasumične sesije, ne na ime ili ID iz podataka zahtjeva.
Korisnici imaju odvojene šifrirane datoteke.
Ne postoji univerzalno automatsko spajanje dokumenata; izričita
zamjena i revizije namjerno sprječavaju nevidljiv gubitak.

## Brisanje i pravila privatnosti

- Lokalni račun: Postavke → Korisnički računi → Trajno izbriši lokalni račun,
  uz potvrdu PIN-om. To nije brisanje udaljenih podataka.
- Udaljeni račun: Postavke → Vlastiti hosting → Trajno izbriši račun na hostingu
  ili vanjski obrazac `https://vasa-domena.hr/delete-account.php`.
- `privacy.php` je **predložak koji vlasnik hostinga mora prilagoditi**:
  voditelj obrade, kontakt, svrhe, primatelji, zadržavanje, prava.
- Za Google Play potrebni su ažurirana Data safety deklaracija,
  valjan javno dostupan URL pravila privatnosti, izravna i vanjska
  mogućnost brisanja računa te opis svih vrsta podataka.
- U financijskoj aplikaciji stvarno čuvanje izdanih računa/fiskalnih
  podataka može biti zakonska obveza: pribavite pravni savjet i
  jasno objavite razdoblja čuvanja. Ova skripta nije fiskalizacija.

## Testiranje

```sh
find server -name '*.php' -print0 | xargs -0 -n1 php -l
php server/tests/smoke.php
```

CI provjerava osnovnu izolaciju dva korisnika, HTTPS API u kodu,
verzije preslike, valjanost tokena i brisanje. **Prije produkcije**
potrebni su i testovi stvarnog hostinga, provjera Apache/Nginx
konfiguracije, integracijski test kroz Android na 2 uređaja,
sigurnosni pregled, rotacija ključa, rezervne kopije hostinga i
opterećenje brojnim paralelnim računima.

Ovo nije SQL sustav za višekorisničko uređivanje uživo. JSON + globalni
`flock` namjerno bira atomarnost umjesto skalabilnosti, pogodno za
manji shared hosting. Očuvajte `storage/secret.php` zajedno sa
šifriranim podacima u **zaštićenoj** sigurnosnoj kopiji hostinga;
gubitak ključa trajno onemogućuje dešifriranje.
