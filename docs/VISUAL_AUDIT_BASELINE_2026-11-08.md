# Fakturiva — vizualni audit, polazna mjerenja za 20 zaslona

**VAŽNO — ovo nije audit novog PR-a #27.** Brojevi su uzeti iz
korisničke ranije CSV datoteke `fakturiva_v017_mae(1).csv`
(8. studenoga 2026.). Nisu izračunani iz novih buildova i
nisu dokaz 0% odstupanja. Uvijek mjeriti originalne reference
i nove stvarne snimke u **istoj fizičkoj rezoluciji**.

RGB MAE je srednja apsolutna razlika kanala RGB u rasponu
0–255, a „različiti pikseli” postotak piksela na kojima se
bar jedan RGB kanal razlikuje. **MAE i postotak vizualne greške
nisu ista metrika**. Na sustavne trake, rasterizaciju fonta
i anti-aliasing snažno utječe uređaj, verzija Androida i DPI.

| Br. | Zaslon | RGB MAE (0–255) | Različiti pikseli |
|---|---|---:|---:|
| 01 | Splash | 1.9077 | 91.944% |
| 02 | Dobrodošli | 7.2573 | 30.356% |
| 03 | Prijava | 6.3458 | 11.172% |
| 04 | Početna | 12.2709 | 45.290% |
| 05 | Računi | 8.7234 | 54.651% |
| 06 | Novi račun | 6.9277 | 19.451% |
| 07 | Stavke računa | 10.1399 | 39.445% |
| 08 | Pregled računa | 8.2208 | 28.359% |
| 09 | Ponude | 8.8242 | 54.866% |
| 10 | Nova ponuda | 7.6969 | 20.864% |
| 11 | Pregled ponude | 10.6825 | 33.243% |
| 12 | Klijenti | 6.8755 | 40.920% |
| 13 | Detalji klijenta | 8.1739 | 19.621% |
| 14 | Artikli i usluge | 8.2082 | 52.316% |
| 15 | Izvještaji | 11.7107 | 29.648% |
| 16 | Naplata | 10.2462 | 54.411% |
| 17 | Pomoć / stari modul | 8.8714 | 47.955% |
| 18 | Postavke | 9.0120 | 20.471% |
| 19 | Profil tvrtke | 7.6064 | 20.307% |
| 20 | Tamna tema | 15.9911 | 75.828% |

## Prioriteti na temelju stvarnih ranijih odstupanja

Najveći udio različitih piksela imaju Splash (91,944%),
Tamna tema (75,828%), Ponude (54,866%), Računi (54,651%),
Naplata (54,411%), Artikli i usluge (52,316%) te
stari modul br. 17 (47,955%). RGB MAE je različita metrika:
najveća prosječna razlika boje zabilježena je na Tamnoj temi,
Početnoj i Izvještajima.

*Ovo je polazno stanje iz prethodnog mjerenja. Novije promjene
profila tvrtke, podrške, web sinkronizacije i dizajna računa nisu
bile uključene.*

## Ponovni audit za PR #27

1. Pokrenuti Android debug + instrumentacijske testove na API 36.
2. Snimiti 20 stvarnih zaslona na 1179 × 2556 px i istoj gustoći
   s odgovarajućim fontom i sustavnim trakama.
3. Učitati izvorni ZIP referenci koji **nije spremljen u repozitorij**
   (bez umjetno generiranih slika).
4. Pokrenuti `python quality/pixel_audit.py REFERENCE_DIR CAPTURE_DIR --expected 20`
   s izvještajem CSV/JSON i 20 zasebnih diff slika kao QA artefaktima.
5. Odvojeno testirati web login/registraciju na 393 × 852 CSS px te
   Windows WebView2 izgled i udaljene tipkovnice/resize.
6. Pohraniti točne metričke rezultate za svaki zaslon, bez
   pripisivanja nule ako nema stvarnih identičnih piksela.

**Trenutačno nije dokazano 0% odstupanja nijednog zaslona
novog PR-a #27.** Cilj je smanjiti odstupanja bez kvarenja
funkcionalnosti, dostupnosti, sigurnosti ili čitljivosti.
