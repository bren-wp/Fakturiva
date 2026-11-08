# Fakturiva — lokalni profili, PIN i opcionalni self-hosted računi

## Obuhvat

Dvije nove vlasnikove reference prikazuju **Registraciju** i **Prijavu
PIN-om**. Oba su zaslona implementirana kao nativni Android tekstovi,
forme, gumbi i numerička tipkovnica, ne kao pozadinska slika.
Na uskim ekranima sadržaj je pomičan; na širokim Android prozorima
(i u kompatibilnom Windows Android emulatoru) širina forme je ograničena.

### Lokalni način (bez interneta)

- Prilikom prve registracije korisnik unosi jedinstveno korisničko ime
  i PIN od 4–6 znamenki.
- Lokalni registar pohranjen je kao **AES-256-GCM** šifrirana datoteka
  pomoću Android Keystorea. PIN se nikada ne pohranjuje kao tekst.
- Za PIN se čuva individualno soljen PBKDF2-HMAC-SHA1 sa 210.000
  iteracija (kompatibilan s minimalnim Android API 24).
- Nakon uzastopnih pogrešaka prijava se progresivno privremeno blokira.
  Blokada se čuva u šifriranom registru.
- Autorizacija je kratkotrajna, samo u memoriji procesa. Novo pokretanje
  aplikacije zahtijeva PIN. Po želji se može zadržati dodatna OS
  potvrda vjerodajnicama uređaja.
- Svaki račun ima zasebnu SQLite datoteku
  `fakturiva-<nasumični UUID>.db` i vlastite šifrirane lokalne
  oporavke. Nacrt nije dostupan drugom lokalnom profilu.
- Jednokratni prijelaz sa stare `fakturiva.db` na prvi račun radi
  kroz provjereni format sigurnosne kopije. Kasniji računi ne mogu
  prisvojiti tu bazu. **Prije nadogradnje korisnik treba izvesti
  zasebnu šifriranu sigurnosnu kopiju.**

**Ograničenje:** 4 znamenke imaju samo 10.000 kombinacija. To je PIN
za kontrolu pristupa na uređaju, **nije ekvivalent dugoj lozinci
ili samostalnoj snažnoj enkripciji SQLite baze**. Androidova zaštita
pohrane, zaključavanje uređaja, Keystore i mjere protiv brute forcea
ostaju važni. Lokalna SQLite baza nije zasebno SQLCipher šifrirana.
Na rootanom/kompromitiranom uređaju nije moguće jamčiti izolaciju.
Ne dijelite uređaj bez uključene zaštite na razini Androida.

### Vlastiti hosting (izričito po izboru korisnika)

U Postavkama postoji opcija `Vlastiti hosting`. Bez unosa HTTPS
adrese, korisničkog imena i vlastite poslužiteljske lozinke
**nema prijenosa korisničkih podataka**.

- Nema SQL baze i nema vanjskog API-ja treće strane.
- `server/` može se postaviti u root bilo koje HTTPS domene s
  PHP 8.1+, OpenSSL i potrebnim pravima zapisivanja.
- Korisnički račun na hostingu ima neovisnu jaku lozinku
  (najmanje 12 znakova), vremenski ograničeni nasumični bearer token
  i svoj izolirani šifrirani JSON zapis.
- Android šalje samo šifrirani `FAK1` format koji korisnik
  prethodno zaštiti poslužiteljskom lozinkom. Lozinka se koristi za
  server autentikaciju preko TLS-a, pa se **ne može tvrditi da je
  ovo kriptografski zero-knowledge sustav protiv samog administratora
  poslužitelja**. Administrator mora biti pouzdan.
- Prijenos je ručan, nikad prikriven ili automatski. Kopija je
  cjelovita zamjena, nije višekorisnička sinkronizacija uređivanja
  na razini pojedinačnih računa.
- Svako spremanje koristi očekivanu reviziju; ako drugi uređaj
  spremi novu kopiju između čitanja i zapisivanja, server vraća
  HTTP 409. Nikad se ne šalje signal plaćanja ili fiskalizacije.

## Sigurnosna i funkcionalna test-matrica

| Scenarij | Očekivano ponašanje |
| --- | --- |
| Dva lokalna korisnika | Različite SQLite baze i draft/backups direktoriji |
| Isti username različite veličine slova | Odbijanje duplikata |
| Krivi PIN | Generička greška, brojanje pokušaja i blokada |
| Gašenje aplikacije | PIN se ponovno traži, sesija se ne obnavlja automatski |
| Vraćanje stare baze | Samo prvi profil, bez miješanja poslovnih dokumenata |
| Izvoz na tuđi server | Nikada bez eksplicitnog unosa korisnika/hosta |
| HTTP ili preusmjeravanje na drugi host | Blokirano, potreban valjan HTTPS |
| Neispravan SSL certifikat | Android odbija vezu (nema custom trust-all) |
| Udaljeni token korisnika A za dokument korisnika B | Nije moguće: server ne prihvaća proizvoljan tenant ID |
| Istodobno spremanje | HTTP 409 za zastarjelu reviziju |
| Udaljeno brisanje | Briše račun, token i njegovu presliku, drugi račun ostaje |
| Lokalno brisanje | Briše bazu, lokalne kopije i lokalni profil |
| Nedostupnost servera | Lokalna aplikacija i dalje radi s lokalnim računom |
| Vizualni test 1:1 | Zahtijeva stvarne snimke na istoj rezoluciji; nije certificiran kao 0% |

## Google Play — koraci prije produkcijske objave

Prema [Google Play pravilima za podatke korisnika](https://support.google.com/googleplay/android-developer/answer/10144311)
i [zahtjevima za brisanje računa](https://support.google.com/googleplay/android-developer/answer/13327111):

1. Objavite **stvarnu**, javno dostupnu politiku privatnosti na
   HTTPS adresi koja navodi Fakturivu, kontakt, prikupljene podatke,
   svrhe, prijenos na korisnikov hosting, primatelje, brisanje i
   zakonske rokove čuvanja. Predložak `server/privacy.php`
   **nije gotova produkcijska politika**.
2. U Google Play Consoleu ažurirajte `Data safety` prema stvarnom
   ponašanju objavljene verzije aplikacije i integriranih SDK-ova.
3. U aplikaciji osigurajte vidljivu opciju udaljenog brisanja;
   za Google Play navedite i javnu vanjsku stranicu
   `https://vasa-domena.hr/delete-account.php`. Ako korisnici
   koriste različite domene, nositelj objavljene aplikacije mora
   osigurati i **vlastitu stabilnu javnu stranicu** s uputama
   koja ne ovisi o domeni pojedinog korisnika.
4. Ne obećavajte da će zakonski obavezni izdani poslovni dokumenti
   uvijek biti odmah trajno obrisani; definirajte pravila zadržavanja
   u skladu s hrvatskim i drugim primjenjivim zakonima.
5. Testirajte ekrane, sigurnost, gubitak uređaja, obnovu nakon
   prekida napajanja, više stvarnih računa, migraciju stare verzije
   i stvarno brisanje na shared hostingu.
6. Produkcijska distribucija zahtijeva odgovarajući AAB potpis,
   pregled deklaracija i pozitivne testove; sam kod na GitHubu
   nije Google Play odobrenje.

[Detaljne upute za hosting i putanje](../server/README.md).
