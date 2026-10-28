# Fakturiva — izmjerena vizualna odstupanja (20/20 zaslona)

**Referenca:** vlasnikov izvorni `Fakturiva_Android_Brending_i_20_Ekrana.zip`, PNG 1179 × 2556.
**Prvi stvarni Android emulator:** commit `c370c255`, [20 emulatorskih PNG snimki](https://github.com/bren-wp/Fakturiva/actions/runs/40782723051/artifacts/12857668423).

Metoda: prosječna apsolutna razlika za **svaki RGB kanal i svaki piksel**, nenormalizirano 0–255, bez pretvaranja u sivu i bez prekrivanja odstupanja. Rezultati uključuju različit sat, sistemsku navigaciju, datume i testne podatke, pa **nisu samo geometrijska odstupanja**. Dostupan je i automatizirani alat `quality/pixel_audit.py` za nove usporedbe.

| # | Referentni ekran | RGB MAE /255 | Stanje i najveći problem |
|---|---|---:|---|
| 01 | Početni splash | 187,12 | Prethodno se prikazivala dobrodošlica; naknadno implementiran tamni Fakturiva splash |
| 02 | Dobrodošli | 29,60 | Nedostaje izvorna ilustracija računa; tipografija/vertikalni razmaci |
| 03 | Prijava | 27,41 | Offline aplikacija trenutno nema prijavu; potrebno je zasebno dizajnirati lokalnu zaštitu umjesto lažne prijave u oblak |
| 04 | Početna | 17,89 | Različiti poslovni podaci; višak visine i sadržaja u karticama |
| 05 | Računi | 15,76 | Filteri, gustoća liste, razmaci i sistemska navigacija |
| 06 | Novi račun | 18,27 | Proširenje obaveznih podataka za dokument i stil obrazaca |
| 07 | Stavke računa | 19,38 | Pregled stavki, tipografija, akcije |
| 08 | Pregled računa | 23,15 | Prikaz PDF-a u aplikaciji, proporcije i hijerarhija |
| 09 | Ponude | 16,60 | Sažetak liste i statusi |
| 10 | Nova ponuda | 18,23 | Forme i raspored akcija |
| 11 | Pregled ponude | 22,96 | Vizualni PDF prikaz i raspored potvrde |
| 12 | Klijenti | 13,38 | Redovi i kartice klijenata |
| 13 | Detalji klijenta | 20,48 | Vizualne KPI kartice, raspored kontakt podataka |
| 14 | Artikli/usluge | 16,16 | Kartice/filtri i razmaci |
| 15 | Izvještaji | 13,35 | Graf i datum, testni podaci |
| 16 | Naplata | 32,66 | Ispis uplatnih stavki značajno odstupa od reference |
| 17 | eRačuni | 17,06 | Referenca prikazuje aktivaciju servisa koji nije implementiran; bez lažnih funkcija |
| 18 | Postavke | 14,71 | Vizualni raspored kartica, različit broj stavki |
| 19 | Profil tvrtke | 16,50 | Naknadno implementirane naslovna kartica i fiksna akcija spremanja |
| 20 | Tamna tema | 24,44 | Kontrast, raspored i sistemska navigacija |

**Zaključak:** dok je RGB MAE različit od nule i ekrani imaju neusklađene strukture ili podatke, ne označavati projekt kao potvrđeno pixel-perfect 1:1.

### Prioriteti za sljedeću iteraciju
1. Zadržati originalnu Fakturiva ikonu i naziv na svim ekranima, bez lažnih funkcija.
2. Ponoviti capture nakon izmjena splasha i profila tvrtke, usporediti točno isti uređaj, rezoluciju i gustoću.
3. Dovršiti naplatu i preglede računa/ponude te KPI kartice s pravim SQLite podacima.
4. Provjeriti početni, prazni, popunjeni, tamni i validacijski prikaz na različitim Android uređajima.
5. U README objaviti samo autentične snimke, ne slike dizajnerskih referenci predstavljene kao aplikacija.

Korisnički originali ostaju referentni materijal i nisu generirani niti mijenjani. Stvarne snimke potječu iz debug emulatora s testnim podacima.
