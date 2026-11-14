<div align="center">

<img src="docs/assets/fakturiva-logo.svg" alt="Fakturiva — poslovanje pod kontrolom" width="440">

# Fakturiva
### Vaši računi. Vaše ponude. Vaše poslovanje.

**Izradite profesionalne račune, pripremite ponude, pratite naplatu i organizirajte klijente — iz jedne aplikacije.**

Android aplikacija • Web pristup poslovnom pregledu • Windows klijent

[**Preuzmi najnovije izdanje**](https://github.com/bren-wp/Fakturiva/releases/latest) · [**Pogledaj stvarne ekrane**](#stvarni-zasloni-aplikacije) · [**Fakturiva web**](https://fakturiva.eu/)

<img src="docs/assets/fakturiva-banner.svg" alt="Fakturiva — računi, ponude i naplata" width="900">

</div>

## Od ponude do naplate, na jednom mjestu

| 🧾 Računi | 📋 Ponude | 💶 Naplata |
| :--- | :--- | :--- |
| Kreiranje, numeriranje, pregled, PDF i evidencija statusa. | Izrada ponuda, status prihvata i pretvaranje u račun. | Evidencija stvarnih uplata, djelomičnih plaćanja i preostalih iznosa. |
| 👥 Klijenti | 🏷️ Artikli i usluge | 📊 Izvještaji |
| Poslovni partneri, kontaktni podaci i povezani dokumenti. | Cijene, vrste stavki, jedinice mjere i stope PDV-a. | Financijski pregled, mjesečni pokazatelji i hrvatski KPR/PO-SD alati. |

### Prilagođena svakodnevnom poslovanju

- **Jednostavan radni tok:** podaci dokumenta → stavke → provjera → spremanje, dijeljenje ili ispis.
- **Različite valute:** valute se čuvaju uz dokument; iznosi iz različitih valuta ne zbrajaju se bez tečaja.
- **Račun i ponuda kao PDF:** podaci izdavatelja ostaju zamrznuti na potvrđenim dokumentima.
- **Offline rad i oporavak:** Android radi nad lokalnom bazom, a šifrirane kopije šalje na server nakon potvrđene sinkronizacije.
- **Svijetla i tamna tema:** prilagodljiv raspored za telefone, tablete i veći prikaz.
- **Ispis:** Androidov servis ispisa i podržani termalni ESC/POS uređaji, uz uvjete kompatibilnosti pojedinog pisača.

## Jedan Fakturiva račun za web i Android

**Registracija i prijava koriste e-adresu i lozinku na [fakturiva.eu](https://fakturiva.eu/), Androidu i webu.** U aplikaciji više nema dodatnog lokalnog koda ni alternativne prijave. Lozinka štiti i prijavu za rad bez interneta. Postojeći korisnici mogu migrirati lokalnu zaštitu nakon prve uspješne web prijave; ne brišite podatke ni aplikaciju prije provjere sigurnosne kopije.

1. Registrirajte se e-adresom, snažnom lozinkom i potvrdom lozinke na webu ili u Android aplikaciji.
2. Na Androidu unesite **istu e-adresu i lozinku**. Nakon uspješne HTTPS provjere aplikacija sprema zaštićeni lokalni profil za rad bez interneta.
3. Prijavite se istom e-adresom i lozinkom na drugom uređaju i vratite posljednju serverom potvrđenu šifriranu kopiju.
4. U aplikaciji otvorite **Postavke → Korisnički računi → Preuzmi novije podatke s weba** za sigurnu obnovu podataka promijenjenih na drugom uređaju. Lokalna kopija prvo se zaštićuje, a nepotvrđene offline promjene ne prepisuju se.
5. Na webu pregledajte poslovne podatke: **Poslovni podatci** služe za tvrtku, klijente, artikle i naplatu, a **Računi i ponude** za izradu, uređivanje i brisanje nacrta, potvrdu dokumenata, prihvat ponude i izradu nacrta računa iz ponude. Android preuzima nove potvrđene revizije samo kad je lokalna baza čista.

**Važno:** Novi API računi moraju koristiti ispravnu e-adresu, dok se postojeći računi s ranijim korisničkim imenima i dalje mogu prijaviti. Android pohranjuje i preuzima **cijele šifrirane kopije** računa, ponuda, klijenata, artikala, uplata i postavki. To omogućuje obnovu potvrđenih podataka na drugim uređajima, ali još nije istodobno uređivanje iste baze na dva uređaja. Web poslovni portal podržava i CRUD nacrta računa i ponuda, potvrdu dokumenta, prihvat ponude i izradu računa iz ponude. **Nisu implementirani svi Androidovi postupci (PDF, ispis, fiskalizacija) u web uređivaču, niti automatski merge istodobnih offline izmjena.** Nepotvrđene promjene na telefonu mogu se izgubiti ako se aplikacija ukloni prije uspješne sinkronizacije. **Ne deinstalirajte aplikaciju bez provjere zadnje pohrane i izvoza vlastite kopije.**

## Stvarni zasloni aplikacije

Svih **20 slika su stvarne snimke pokrenute Android aplikacije na QA emulatoru**, nisu dizajnerski mockupovi ni AI generirane ilustracije. GitHub Actions pri izdavanju ponovno pokreće APK i snima svih 20 ekrana u zaseban CI artefakt; **galerija u README-u ne osvježava se automatski ako se novi screenshotovi razlikuju od commita**. Referentne dizajnerske slike koriste se isključivo za usporedbu, nikad kao dokaz implementacije.

**Provjereni vizualni audit v0.18.6 (14. 11. 2026.):** 20 stvarnih snimki s merge commita `7de2d9ad` uspoređeno je s vlasnikovih 20 originalnih PNG referenci 1179 × 2556. Prosječni **RGB MAE 8,273/255**, svi zasloni imaju različite piksele; **potpuna 1:1 podudarnost nije postignuta**. Najveća odstupanja: Tamna tema, Početna, Pregled ponude. [Potpuni audit: 20 zaslona, SSIM i odstupanja](docs/V0.18.6_20_SCREEN_PIXEL_AUDIT_2026-11-14.md).

<div align="center">
<table>
<tr>
<td align="center"><img src="docs/screenshots/01_pocetni_zaslon.png" alt="Splash — stvarni Android screenshot" width="145"><br><b>Splash</b></td>
<td align="center"><img src="docs/screenshots/02_dobrodosli.png" alt="Dobrodošli — stvarni Android screenshot" width="145"><br><b>Dobrodošli</b></td>
<td align="center"><img src="docs/screenshots/03_prijava.png" alt="Prijava — stvarni Android screenshot" width="145"><br><b>Prijava</b></td>
<td align="center"><img src="docs/screenshots/04_pocetna.png" alt="Početna — stvarni Android screenshot" width="145"><br><b>Početna</b></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/05_racuni.png" alt="Računi — stvarni Android screenshot" width="145"><br><b>Računi</b></td>
<td align="center"><img src="docs/screenshots/06_novi_racun.png" alt="Novi račun — stvarni Android screenshot" width="145"><br><b>Novi račun</b></td>
<td align="center"><img src="docs/screenshots/07_stavke_racuna.png" alt="Stavke — stvarni Android screenshot" width="145"><br><b>Stavke</b></td>
<td align="center"><img src="docs/screenshots/08_pregled_racuna.png" alt="Pregled računa — stvarni Android screenshot" width="145"><br><b>Pregled računa</b></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/09_ponude.png" alt="Ponude — stvarni Android screenshot" width="145"><br><b>Ponude</b></td>
<td align="center"><img src="docs/screenshots/10_nova_ponuda.png" alt="Nova ponuda — stvarni Android screenshot" width="145"><br><b>Nova ponuda</b></td>
<td align="center"><img src="docs/screenshots/11_pregled_ponude.png" alt="Pregled ponude — stvarni Android screenshot" width="145"><br><b>Pregled ponude</b></td>
<td align="center"><img src="docs/screenshots/12_klijenti.png" alt="Klijenti — stvarni Android screenshot" width="145"><br><b>Klijenti</b></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/13_detalji_klijenta.png" alt="Detalji klijenta — stvarni Android screenshot" width="145"><br><b>Detalji klijenta</b></td>
<td align="center"><img src="docs/screenshots/14_artikli_usluge.png" alt="Artikli i usluge — stvarni Android screenshot" width="145"><br><b>Artikli i usluge</b></td>
<td align="center"><img src="docs/screenshots/15_izvjestaji.png" alt="Izvještaji — stvarni Android screenshot" width="145"><br><b>Izvještaji</b></td>
<td align="center"><img src="docs/screenshots/16_naplata.png" alt="Naplata — stvarni Android screenshot" width="145"><br><b>Naplata</b></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/17_eracun_integracija.png" alt="eRačuni — stvarni Android screenshot" width="145"><br><b>eRačuni</b></td>
<td align="center"><img src="docs/screenshots/18_postavke.png" alt="Postavke — stvarni Android screenshot" width="145"><br><b>Postavke</b></td>
<td align="center"><img src="docs/screenshots/19_profil_tvrtke.png" alt="Profil tvrtke — stvarni Android screenshot" width="145"><br><b>Profil tvrtke</b></td>
<td align="center"><img src="docs/screenshots/20_tamna_tema.png" alt="Tamna tema — stvarni Android screenshot" width="145"><br><b>Tamna tema</b></td>
</tr>
</table>
</div>

## Sigurnost podataka i privatnost

Fakturiva šifrira sigurnosne kopije pomoću AES-256-GCM, provjerava integritet podataka i odjeljuje zapise korisničkih računa. Jedinstvena prijavna lozinka na poslužitelju i lokalni zaštićeni verifikator omogućuju autentifikaciju s internetom ili bez njega. Neuspjeh mreže ili sukob revizija ne smije automatski prebrisati noviju kopiju.

Sigurnosni mehanizmi ne zamjenjuju potrebu za **provjerenim HTTPS hostingom, sigurnim čuvanjem poslužiteljskog ključa, offsite backupom i testom povrata podataka**. [Saznajte više o web postavljanju](web/README.md).

## eRačuni, fiskalizacija i hrvatski porezni alati

U aplikaciji su dostupni **ekran eRačuna**, koraci konfiguracije informacijskog posrednika te alati za hrvatsku **Knjigu prometa i PO-SD**. Postavljanje naziva posrednika samo po sebi **nije aktivacija službene integracije**. Strukturirano slanje eRačuna, potvrda fiskalizacije i regulatorno certificirani tokovi zahtijevaju odgovarajuće poslužiteljske integracije i vjerodajnice.

PO-SD XML je trenutačno **radni XML koji nije namijenjen izravnom uvozu u ePoreznu**. [Pravila i ograničenja KPR/PO-SD](docs/HR_KPR_POSD.md).

## Preuzimanje

[**GitHub Releases — Android, Windows i web**](https://github.com/bren-wp/Fakturiva/releases)

| Paket | Sadržaj |
| :--- | :--- |
| **Android APK** | Instalacija i funkcionalno testiranje na Androidu 7.0+ (API 24+) |
| **Android AAB** | Testni debug-signed AAB i zaseban nepotpisani release AAB |
| **Windows portable / setup** | Windows klijent temeljen na webu, uz WebView2 i dostupnost servera |
| **Web paket** | PHP/JSON hosting za korisničke račune i šifrirane preslike |
| **SHA256SUMS.txt** | Provjera integriteta objavljenih datoteka |

**Preview paketi nisu automatski potpisani produkcijskim Google Play ključem.** Pri prijelazu između različitih certifikata instalacije možda neće biti moguće ažurirati izravno. Produkcijska kompatibilnost, regulatorne integracije i dostupnost servera potvrđuju se zasebno.

## Praktični vodič

1. U **Profilu tvrtke** ispunite podatke izdavatelja i valutu.
2. U **Klijentima** i **Artiklima i uslugama** pripremite osnovne podatke.
3. Odaberite **Novi račun** ili **Nova ponuda**, unesite stavke i provjerite PDV i ukupni iznos.
4. Spremite ili podijelite PDF; za stvarno primljena plaćanja koristite **Naplatu**.
5. Provjerite **Pohranu na serveru i oporavak** te izvezite vlastitu sigurnosnu kopiju.

Dokumentacija: [Android na Windowsu](docs/ANDROID_ON_WINDOWS.md) · [Ispis i pisači](docs/PRINTING_SUPPORT.md) · [KPR/PO-SD](docs/HR_KPR_POSD.md) · [Privatnost](web/politika-privatnosti/index.php) · [Plan provjera produkcije](docs/PRODUCTION_ROADMAP_FAKTURIVA_2026.md).

---

<div align="center">

<img src="docs/assets/fakturiva-logo.svg" alt="Fakturiva by Brendigo" width="260">

**Fakturiva · [Brendigo](https://brendigo.com)**

*Pametniji način upravljanja računima, ponudama i naplatom.*

</div>
