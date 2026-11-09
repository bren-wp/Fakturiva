# Fakturiva — statička revizija koda, autentifikacije i stabilnosti

Datum: 9. studenoga 2026. · Razvojni PR #29 (nastavak spojenog PR-a #28)

## Opseg i dokaz

Repozitorij sadrži Android (native Java), Windows WebView2, web PHP/JS, alat za ispis i QA workflowove. Dodan je automatski inventar `quality/code_security_audit.py`, koji pregledava izvorne Java, PHP, JS, XML, shell i C# datoteke te emitira `build/code-security-audit.json`. Inventar traži kritične obrasce (uključeno nezaštićeno HTTP povezivanje, debug manifest, tolerantni TLS, Android JS bridge, PHP eval/shell/unserialize, preostala domena fakturiva.com), izložene datoteke s ključevima i kandidate za neiskorištene privatne metode. Kandidati za dead code nisu automatski brisani jer refleksija, JNI, manifest i callbacki mogu dati lažno pozitivan rezultat.

Ovo je **repo-wide statička revizija s regresijskim testovima**, a **nije potvrda da je obavljen penetracijski test stvarnog hostinga, svih mrežnih usluga i svakog fizičkog uređaja**.

## Provedene ispravke

| Područje | Ispravak |
|---|---|
| Splash | Prikaz 4500 ms tijekom normalnog pokretanja, bez skraćivanja dodirom; debug snimanje ostaje bez čekanja |
| #09 Ponude | Filtri Sve/Poslane/Prihvaćene/Skice, pohranjeni prijelaz `SENT → ACCEPTED` samo nakon korisnikove potvrde, prikaz ispravnog statusa, podrška u backup validaciji |
| #05 Računi | Saldo neplaćenih računa uključuje sve potvrđene račune s pozitivnim potraživanjem (osim nacrta i storniranih) |
| #16 Naplata | Valutna izolacija povijesti uplata i otvorenih računa; lokalizirani iznosi i ujednačeni filtri |
| #14 Artikli i usluge | Optimizacija filtriranja i separatora s kvadratnog na linearni prolaz; uklonjen nepotreban wrapper; ispravljene hrvatske oznake jedinične cijene i PDV-a |
| Android prijava | Web e-adresa/lozinka se verificira prema `https://fakturiva.eu/` i za račune već prisutne na uređaju; lokalni PIN ostaje dodatna zaštita offline kopije |
| Registracija | Obavezna ispravna e-adresa na Androidu i novom web obrascu; starim računima s korisničkim imenom zadržana je prijava |
| Jezik | Hrvatski je zadani na uređajima s hrvatskim jezikom; izričito odabrani jezik ostaje nepromijenjen; svih 20 debug referentnih snimki koristi HR bez promjene korisničkih postavki |
| Web pohrana | `FAKTURIVA_STORAGE_ROOT` može premjestiti JSON bazu i glavni ključ u privatni direktorij izvan document root-a; dodan test izolacije ključa |

## Sigurnosni nalazi i produkcijske blokade

1. **Visok prioritet — javni web direktorij:** ako je `FAKTURIVA_STORAGE_ROOT` nepostavljen, postojeći kompatibilni zadani direktorij ostaje `web/storage/`. Na Apacheu se koristi `.htaccess`; na Nginxu to nije zaštita. U produkciji postaviti apsolutni privatni direktorij izvan public root-a, onemogućiti izravno serviranje `storage/` i sačuvati `secret.php` u sigurnom odvojenom backupu. **Promjenu lokacije izvršiti tek nakon kontrolirane migracije podataka i ključa zajedno.**
2. **Visok prioritet — potvrda pohrane:** Android lokalna SQLite baza i server FAK1 snimka nisu potpuno sinkrono dvosmjerno spremište. Dok `CloudMirror` pokazuje `dirty=true`, deinstalacija može obrisati još nepotvrđene izmjene. Konflikt revizija treba ručno riješiti, a ne automatski prepisati udaljenu kopiju.
3. **Visok prioritet — web ↔ Android:** login/registracija su u istom računu, a pregled financija na webu otključava server backup. **Uređivanje svih klijenata, računa, kataloga i naplata putem weba u stvarnom vremenu još nije dokazano**. Potrebni su integracijski E2E testovi na stvarnoj domeni, s dva korisnika i dva uređaja.
4. **Srednji prioritet — PIN:** 4–6-znamenkasti lokalni PIN inherentno je slabiji od lozinke; ublaženo KeyStore šifriranjem, PBKDF2 i lokalnim ograničenjem pokušaja. Za web prijavu koristi se snažna lozinka.
5. **Srednji prioritet — mogućnost prekinute registracije:** pri prekidu mreže između kreiranja računa, početnog backupa i lokalnog profila mogu ostati djelomični zapisi. Ne brisati ih automatski jer bi to moglo uzrokovati gubitak podataka. Potrebna je potvrđena procedura nastavka i oporavka.
6. **Web kriptografija:** AES-256-GCM i provjera SHA-256 koriste se za poslovne backupove. Web servis može privremeno dešifrirati podatke kada korisnik unese lozinku za prikaz financija; to nije tvrdnja o trajnom end-to-end šifriranju na poslužitelju.
7. **Fiskalizacija i eRačuni:** funkcionalnosti ostaju u projektu; nema potvrde produkcijskog certifikata, pristupa regulatornim servisima, potpunog API toka ni zakonski odobrenog izdavanja. Ove module ne zamjenjivati simuliranim uspjehom.

## Zaokružena revizija dead code-a

Izravno uklonjeno: nekorišteni `MainActivity.addProduct()` wrapper i `catalog.previousVisible` stanje, uz eliminaciju nepotrebnog drugog skeniranja i O(n²) provjere separatora. Novi cjelorepozitorijski skener daje dodatne *kandidate za ručnu provjeru* i ne briše refleksijom pozivane ili događajima povezane metode. Broj kandidata uzeti iz aktualnog CI artefakta, ne iz procjene.

## Obavezno prije spajanja PR-a

Pokrenuti `Android verification`, `Verify native Android screens` s pravim screenshotovima za svih 20 ekrana, cijelu responsive matricu, `Self-hosted Fakturiva JSON API security smoke`, Windows build, regresiju prihvata ponude i test izvanjskog privatnog storage root-a. Usporediti realne screenshotove s vlasnikovim izvornim referencama na 1179×2556 te izračunati MAE, SSIM i udio različitih piksela **za ovaj commit**. Raniji rezultati (09: 54,87%, 05: 54,65%, 16: 54,41%, 14: 52,32%) pripadaju starijoj Android v0.17 snimci, ne novim promjenama.

**Ne tvrditi 0% odstupanja, potpunu sigurnost, da se aplikacija ne može srušiti ili produkcijsku spremnost bez provjera.**

## Primjer konfiguracije privatne pohrane

Preporučena produkcijska struktura: PHP document root je direktorij `web/`, a `FAKTURIVA_STORAGE_ROOT=/var/lib/fakturiva-private` je izvan njega i čitljiv samo PHP servisnom korisniku. U PHP-FPM-u postaviti varijablu okruženja za pool, dopustiti čitanje/pisanje `/var/lib/fakturiva-private`, a Nginxu zabraniti `/storage/` i pristup dotfiles. Nije dovoljno samo promijeniti DNS na `fakturiva.eu`. Migracija već aktivnih računa zahtijeva kopiranje šifriranih JSON datoteka i pripadnog `secret.php` zajedno, u zaustavljenom servisu, s potvrdom hashova prije uključenja.
