<div align="center">

<img src="docs/assets/fakturiva-logo.svg" alt="Fakturiva — računi i ponude" width="440">

### Računi i ponude. Jednostavno.

**Fakturiva** je Android aplikacija za izradu i organizaciju računa, ponuda, klijenata i uplata. Unesite podatke, pripremite dokument, pregledajte ga i podijelite kao PDF — bez nepotrebnih koraka.

[**Preuzmite aplikaciju**](https://github.com/bren-wp/Fakturiva/releases) · [**Pogledajte zaslone**](#fakturiva-u-radu) · [**Kako koristiti**](#kako-koristiti-fakturivu)

</div>

![Fakturiva — računi i ponude](docs/assets/fakturiva-banner.svg)

## Sve za vaše račune i ponude

| 🧾 Računi | 📋 Ponude | 👥 Klijenti |
| :--- | :--- | :--- |
| Izrada računa, stavke, iznosi, datumi dospijeća i evidencija naplate. | Profesionalne ponude s mogućnošću pretvaranja u nacrt računa. | Kontakti, adrese, identifikacijski brojevi i povijest dokumenata. |

| 🏷️ Artikli i usluge | 📈 Pregled poslovanja | 📄 PDF |
| :--- | :--- | :--- |
| Katalog, količine, cijene i porezne stope koje unosite. | Mjesečni pokazatelji, naplata i izvještaji. | Pregled, spremanje, dijeljenje i višestranični A4 dokumenti. |

## Kako koristiti Fakturivu

1. **Postavite profil izdavatelja.** Unesite naziv, adresu, državu, kontakt i ostale podatke koje želite prikazati na dokumentima. Porezni identifikacijski broj nije obvezan unos u aplikaciji.
2. **Dodajte klijenta.** Unesite naziv, adresu i željene kontaktne podatke. Podržani su različiti formati međunarodnih poreznih identifikacijskih oznaka.
3. **Izradite račun ili ponudu.** Dodajte stavke, količine, cijene, stope poreza, datume i napomene te pregledajte izračun.
4. **Potvrdite dokument i podijelite PDF.** Nakon potvrde dodjeljuje se broj dokumenta. Za račune možete evidentirati primljene uplate.

Već potvrđeni dokumenti čuvaju izvorne podatke izdavatelja i kupca, pa izmjene profila ne mijenjaju njihovu povijest.

## Hrvatska: Knjiga prometa i PO-SD

Za hrvatske samostalne djelatnosti Fakturiva sada priprema **Knjigu prometa**
prema datumu stvarne naplate i godišnji **PO-SD** za paušalne obveznike.
KPR možete izvesti u PDF za odabrani raspon datuma, a PO-SD u PDF za
2025. ili 2026. godinu. Upute, ograničenja, porezni razredi i provjere:
[Hrvatski KPR i PO-SD](docs/HR_KPR_POSD.md).

**Važno:** PO-SD XML trenutno se može preuzeti samo kao **radna datoteka
koja NIJE službeni XML za uvoz u ePoreznu**. Potvrda važeće XSD sheme
i probni uvoz obvezni su prije najave punog XML izvoza. Podatci u
referentnim PDF-ovima korisnika nisu kopirani u projekt.

## Korisnički račun, sigurnosne kopije i oporavak

Fakturiva koristi zasebne korisničke račune te šifrirane kopije podataka.
Sadržaj korisničkog računa pohranjuje se na HTTPS serveru nakon
provjerenog povezivanja. Android zadržava privremenu radnu bazu kako
bi se promjene mogle pripremati i bez interneta; **nepotvrđene promjene
mogu se izgubiti pri deinstalaciji** i zato aplikacija prikazuje
status posljednjeg uspješnog prijenosa.

- **Pohrana uz revizije:** server pohranjuje šifrirane JSON zapise i
  prethodne revizije, uz zaštitu od slučajnog prepisivanja.
- **Sigurnost korisnika:** podatci su odvojeni po korisniku,
  pristup se ograničava i autentificira, a sadržaj sigurnosnih
  kopija ostaje šifriran korisnikovom jakom lozinkom.
- **Oporavak:** nakon promjene ili deinstalacije uređaja korisnik
  može ponovno pristupiti serveru i potvrđenu kopiju vratiti u profil.
  Kratki lokalni PIN sam po sebi nije dovoljan za oporavak.
- **Administracija:** web ima zaseban, posebno autentificiran
  administratorski panel za korisničke račune, zabranu pristupa i
  dodjelu statusa. Administrator ne može dešifrirati privatne
  računovodstvene sadržaje korisnika.
- **Za produkciju:** zasebni offsite backup, vraćanje podataka,
  identitet voditelja obrade, pravna provjera i testirana
  sinkronizacija obavezni su uvjeti prije objave.

[Web postavljanje](web/README.md) ·
[Plan produkcijskih provjera](docs/PRODUCTION_ROADMAP_FAKTURIVA_2026.md) ·
[Politika privatnosti](web/politika-privatnosti/index.php)

## Fakturiva u radu

Galerija sadrži **20 stvarnih snimki iz ranijeg Android QA izdanja** (ne umjetno generirane prikaze). Na svakoj novoj verziji potrebno je ponovno snimiti zaslone i izmjeriti odstupanja prema originalnim referencama; ova galerija nije dokaz 0% razlike.

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
<td align="center"><img src="docs/screenshots/17_eracun_integracija.png" alt="Pomoć — stvarni Android screenshot" width="145"><br><b>Pomoć</b></td>
<td align="center"><img src="docs/screenshots/18_postavke.png" alt="Postavke — stvarni Android screenshot" width="145"><br><b>Postavke</b></td>
<td align="center"><img src="docs/screenshots/19_profil_tvrtke.png" alt="Profil tvrtke — stvarni Android screenshot" width="145"><br><b>Profil tvrtke</b></td>
<td align="center"><img src="docs/screenshots/20_tamna_tema.png" alt="Tamna tema — stvarni Android screenshot" width="145"><br><b>Tamna tema</b></td>
</tr>
</table>
</div>

U aplikaciji su dostupni i ponude, artikli i usluge, klijenti, izvještaji, naplata, pomoć, profil izdavatelja i postavke. Sučelje se prilagođava manjim i većim Android uređajima.

## Važne mogućnosti

- **Automatsko spremanje nacrta** i nastavak započetog dokumenta.
- **Zaštita pri otvaranju** prema postavkama Androidova zaključavanja.
- **Sigurnosne kopije** koje možete izvesti i vratiti uz vlastitu lozinku.
- **Svijetla i tamna tema** i pristupačne funkcionalne kontrole.
- **Uređivanje podataka izdavatelja i klijenata** bez promjene već potvrđenih dokumenata.

## Preuzimanje za Android

[**Sva izdanja Fakturive**](https://github.com/bren-wp/Fakturiva/releases)

Posljednje objavljeno izdanje: [**v0.17.0-preview.1**](https://github.com/bren-wp/Fakturiva/releases/tag/v0.17.0-preview.1). Izmjene na novijim otvorenim granama još nisu dio tog preuzimanja.

| Paket | Namjena |
| :--- | :--- |
| `debug.apk` | Instalacijski paket za testiranje na Androidu 7.0 i novijem |
| `DEBUG-SIGNED.aab` | Testni Android App Bundle |
| `UNSIGNED.aab` | Paket koji treba odgovarajuće potpisati prije distribucije kroz trgovinu |
| `SHA256SUMS.txt` | Provjera integriteta datoteka |

**Prije zamjene aplikacije ili promjene telefona izvezite i provjerite sigurnosnu kopiju.** Različiti potpisni certifikati mogu spriječiti instalaciju preko postojeće verzije.

## Android aplikacija na Windows računalu

Fakturiva ima Android aplikaciju. U mapi [`win/`](win/README.md)
razvija se i zaseban **Windows klijent**, koji trenutačno prikazuje
isti HTTPS web korisnički račun putem Microsoft WebView2.
Njegovi zasloni još nisu izvorno 1:1 izvedeni prema 20 Android referenci.
Zasebni Windows build isporučuje provjerene preview
artifakte kada GitHub Actions uspješno završi. Kompatibilan Android emulator na Windows računalu može pokrenuti
APK, uz ograničenja samog emulatora. Obrasci i navigacija prilagođavaju se
veličini Android prozora: telefonski raspored ostaje jednak izvornim mobilnim
referencama, a na širokim prozorima sadržaj je čitljivo centriran. Dostupni
su prečaci Ctrl+1–5 za glavne zaslone te Ctrl+P za ispis otvorenog dokumenta.

**Napomena:** Windows Subsystem for Android više nije dostupan putem
Microsoft Storea od 5. ožujka 2025. Podrška za fizičke pisače i ostale USB
uređaje ovisi o njihovoj dostupnosti unutar Android emulatora.
[Upute i provjera kompatibilnosti](docs/ANDROID_ON_WINDOWS.md).

## Međunarodni računi i ponude — razvojna grana

Fakturiva je namijenjena i obrtima, samostalnim djelatnostima, d.o.o., j.d.o.o., Ltd, LLC, Inc, GmbH i drugim oblicima poslovanja. U novoj razvojnoj grani profil izdavatelja sadrži pravni oblik, registracijski broj i tijelo, trgovački naziv, državu i regiju, dodatne porezne podatke, kontakt-osobu te detaljne bankovne i platne upute. Porezni broj može ostati prazan ili sadržavati međunarodnu oznaku.

**Valute:** EUR je zadana. Za nove dokumente možete odabrati podržanu ISO valutu s dvije decimalne znamenke (npr. USD, GBP, CHF, CAD, AUD i mnoge druge). Valuta se sprema uz račun ili ponudu i ostaje vezana uz dokument. Evidencija i izvještaji ne zbrajaju različite valute. **Konverzija tečaja nije automatska.** Valute s 0 ili 3 decimalna mjesta (npr. JPY, KWD) nisu još dostupne zbog potrebne zasebne nadogradnje novčanog modela.

**Jezici:** nova osnova koristi engleski kao zadani jezik, a korisnik u Postavkama može izabrati hrvatski. Pripremljen je popis **31 ciljnog jezika**, ali cjelovito prevođenje svih zaslona, obrazaca, dijaloga, PDF-a i sistemskih poruka još **nije dovršeno**, pa se ostali jezici ne prikazuju kao podržani. I dio engleskog/hrvatskog sučelja još zahtijeva lekturu i uklanjanje preostalih neprevedenih tekstova. [Pravila objave prijevoda](docs/INTERNATIONALIZATION_QA.md).

### Ispis računa i ponuda — razvojna verzija

Fakturiva podržava više načina ispisa dokumenata:

- **Pisači za A3, A4, A5, A6, US Letter i Legal:** preko Androidova sustava ispisa i instaliranih servisa proizvođača, uključujući kompatibilne Wi-Fi, USB i mrežne pisače.
- **Bluetooth Classic termalni pisači:** ESC/POS računi na **58 mm (384 točke)** ili **80 mm (512 ili 576 točaka)**, uz prethodno uparivanje.
- **Mrežni termalni pisači:** ESC/POS preko lokalne IPv4 adrese i TCP priključka (obično **9100**), na 58 mm ili 80 mm. Adresu i priključak unosi korisnik; javne internetske adrese namjerno su blokirane.

Korisnik odabire format i pisač u pregledu računa ili Postavkama. Svi izrađeni PDF-ovi imaju sitnu oznaku **Powered by Brendigo** u podnožju svake stranice, a termalni računi pri samom dnu ispisa. Ako korisnik želi zatražiti uklanjanje te oznake, u **Postavke → O aplikaciji i podrška** može poslati upit na **info@fakturiva.com**.

**Napomena:** Ne postoji univerzalna podrška za sve pisače i kartične POS terminale. Potrebna je kompatibilnost s Androidovim servisom ispisa ili ESC/POS protokolom i provjera konkretnog modela. Funkcija ne naplaćuje kartice i ne provodi fiskalizaciju.

[**Priručnik za pisače, kompatibilnost i privatnost**](docs/PRINTING_SUPPORT.md) · [**Proizvođači pisača i hrvatski POS servisi**](docs/PRINTER_VENDOR_AND_POS_MATRIX.md) · [**SUNMI SDK i drugi proizvođači**](docs/POS_INTEGRATION_MATRIX.md)

Ove značajke **još nisu dio posljednjeg objavljenog izdanja** v0.17.0-preview.1. Trenutačna aplikacija nije certificirana za automatsko ispunjavanje zakonskih obveza bilo koje države; korisnik provjerava obvezne podatke i računovodstvene zahtjeve u svojoj jurisdikciji.

---

<div align="center">

<img src="docs/assets/fakturiva-logo.svg" width="280" alt="Fakturiva by Brendigo">

**Fakturiva · [Brendigo](https://brendigo.com)**

*Računi i ponude, jednostavno.*

</div>
