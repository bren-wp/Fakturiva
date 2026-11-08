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

## Fakturiva u radu

Prikazane su stvarne snimke aplikacije, a ne dizajnerski prikazi.

<div align="center">
<table>
<tr>
<td align="center"><img src="docs/screenshots/02_dobrodosli.png" alt="Dobrodošli" width="195"><br><b>Jednostavan početak</b></td>
<td align="center"><img src="docs/screenshots/04_pocetna.png" alt="Početna ploča" width="195"><br><b>Poslovanje na jednom mjestu</b></td>
<td align="center"><img src="docs/screenshots/05_racuni.png" alt="Popis računa" width="195"><br><b>Pregled svih računa</b></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/06_novi_racun.png" alt="Novi račun" width="195"><br><b>Račun korak po korak</b></td>
<td align="center"><img src="docs/screenshots/08_pregled_racuna.png" alt="Pregled računa" width="195"><br><b>Dokument spreman za PDF</b></td>
<td align="center"><img src="docs/screenshots/20_tamna_tema.png" alt="Tamna tema" width="195"><br><b>Udobna tamna tema</b></td>
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

## Međunarodni računi i ponude — razvojna grana

Fakturiva je namijenjena i obrtima, samostalnim djelatnostima, d.o.o., j.d.o.o., Ltd, LLC, Inc, GmbH i drugim oblicima poslovanja. U novoj razvojnoj grani profil izdavatelja sadrži pravni oblik, registracijski broj i tijelo, trgovački naziv, državu i regiju, dodatne porezne podatke, kontakt-osobu te detaljne bankovne i platne upute. Porezni broj može ostati prazan ili sadržavati međunarodnu oznaku.

**Valute:** EUR je zadana. Za nove dokumente možete odabrati podržanu ISO valutu s dvije decimalne znamenke (npr. USD, GBP, CHF, CAD, AUD i mnoge druge). Valuta se sprema uz račun ili ponudu i ostaje vezana uz dokument. Evidencija i izvještaji ne zbrajaju različite valute. **Konverzija tečaja nije automatska.** Valute s 0 ili 3 decimalna mjesta (npr. JPY, KWD) nisu još dostupne zbog potrebne zasebne nadogradnje novčanog modela.

**Jezici:** nova osnova koristi engleski kao zadani jezik, a korisnik u Postavkama može izabrati hrvatski. Pripremljen je popis **31 ciljnog jezika**, ali cjelovito prevođenje svih zaslona, obrazaca, dijaloga, PDF-a i sistemskih poruka još **nije dovršeno**, pa se ostali jezici ne prikazuju kao podržani. I dio engleskog/hrvatskog sučelja još zahtijeva lekturu i uklanjanje preostalih neprevedenih tekstova. [Pravila objave prijevoda](docs/INTERNATIONALIZATION_QA.md).

### Ispis računa i ponuda — razvojna verzija

Fakturiva podržava više načina ispisa dokumenata:

- **Pisači za A3, A4, A5, A6, US Letter i Legal:** preko Androidova sustava ispisa i instaliranih servisa proizvođača, uključujući kompatibilne Wi-Fi, USB i mrežne pisače.
- **Bluetooth Classic termalni pisači:** ESC/POS računi na **58 mm (384 točke)** ili **80 mm (512 ili 576 točaka)**, uz prethodno uparivanje.
- **Mrežni termalni pisači:** ESC/POS preko lokalne IPv4 adrese i TCP priključka (obično **9100**), na 58 mm ili 80 mm. Adresu i priključak unosi korisnik; javne internetske adrese namjerno su blokirane.

Korisnik odabire format i pisač u pregledu računa ili Postavkama. Svi izrađeni PDF-ovi imaju sitnu oznaku **Powered by Brendigo** u podnožju svake stranice, a termalni računi pri samom dnu ispisa. Ako korisnik želi zatražiti uklanjanje te oznake, u **Postavke → O aplikaciji i podrška** može poslati upit na **info@plati.com.hr**.

**Napomena:** Ne postoji univerzalna podrška za sve pisače i kartične POS terminale. Potrebna je kompatibilnost s Androidovim servisom ispisa ili ESC/POS protokolom i provjera konkretnog modela. Funkcija ne naplaćuje kartice i ne provodi fiskalizaciju.

[**Priručnik za pisače, kompatibilnost i privatnost**](docs/PRINTING_SUPPORT.md)

Ove značajke **još nisu dio posljednjeg objavljenog izdanja** v0.17.0-preview.1. Trenutačna aplikacija nije certificirana za automatsko ispunjavanje zakonskih obveza bilo koje države; korisnik provjerava obvezne podatke i računovodstvene zahtjeve u svojoj jurisdikciji.

---

<div align="center">

<img src="docs/assets/fakturiva-logo.svg" width="280" alt="Fakturiva by Brendigo">

**Fakturiva · [Brendigo](https://brendigo.com)**

*Računi i ponude, jednostavno.*

</div>
