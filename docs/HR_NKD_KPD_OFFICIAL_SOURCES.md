# Hrvatske NKD i KPD šifre — službeni izvori i verzioniranje

**Stanje propisa na dan 9. studenoga 2026.**

| Klasifikacija | Osnova | Primjena | Izvor |
|---|---|---|---|
| NKD 2025 | NN 47/2024 | 1. siječnja 2025. | https://narodne-novine.nn.hr/clanci/sluzbeni/2024_04_47_800.html |
| KPD 2025 | NN 151/2024 | 1. siječnja 2025. | https://narodne-novine.nn.hr/clanci/sluzbeni/2024_12_151_2491.html |
| Izmjena KPD 2025 | NN 119/2026 | **1. siječnja 2027.** | https://narodne-novine.nn.hr/clanci/sluzbeni/2026_10_119_1423.html |

## Kako će se koristiti šifrarnici

- NKD klasificira djelatnost, KPD klasificira proizvode i usluge.
  NKD nije zamjena za KPD šifru stavke strukturiranog računa.
- Svaki popis šifri mora imati verziju, datum početka primjene,
  službeni URL, kontrolnu sumu i test potpunosti.
- Šifre na već izdanom dokumentu ostaju nepromjenjive;
  izmjena šifrarnika ne smije prepisati povijest.
- Aktualni Android unos NKD-a provjerava samo **sintaktički format**,
  a ne postojanje šifre u potpunom službenom šifrarniku.
- Puni katalozi i pretraživanje **još nisu uvezeni**; nije
  dopušteno prikazivati automatsku zakonsku validaciju dok
  nema provjerenog popisa.
- Za 2026. primjenjuje se KPD 2025 iz NN 151/2024.
  Izmjene iz NN 119/2026 ne primjenjuju se prije 1.1.2027.

## Produkcijski postupak uvoza

1. Dohvatiti cijele službene kataloge DZS/Narodne novine.
2. Pretvoriti ih u verzionirane podatkovne datoteke i
   sačuvati SHA-256 sadržaja.
3. Potvrditi sve šesteroznamenkaste KPD kategorije i
   odgovarajuće NKD podrazrede uz službeni broj stavki.
4. Validirati isključivo kod važeće verzije prema datumu isprave.
5. Pridružiti KPD svakoj stavci računa, a NKD poslovnom profilu.
6. Izvesti testne strukturirane eRačune i provesti XSD
   validaciju te potvrdu pristupne točke.

## Države izvan Hrvatske

Hrvatski KPR i PO-SD ne primjenjuju se automatski na
Ltd, Inc, LLC, GmbH, d.o.o. ili j.d.o.o. Svaka država
zahtijeva provjerene službene obrasce, polja, valute,
pravila i mogućnost prijema. Korisnički profil
može prilagođavati relevantna polja, ali porezni obračun
ne smije se izmišljati iz predloška druge države.
