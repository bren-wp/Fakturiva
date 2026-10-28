# Fakturiva v0.3.0-preview.1 — Android pretpregled

Nastavak izrade Fakturive: pouzdanije čuvanje računa i ponuda, uglađeni vizualni elementi brenda i automatski oporavak radnih dokumenata.

## Novo u izdanju

- Automatsko šifrirano spremanje nedovršenog računa ili ponude tijekom unosa.
- Oporavak unesenih podataka nakon prekida rada ili ponovnog pokretanja uređaja.
- Upozorenje prije odbacivanja nedovršenog dokumenta.
- Sigurnija rotacija lokalnih šifriranih kopija uz atomsko prepisivanje.
- Usklađenije nativne ikone i početna ploča.
- Postojeće funkcije: klijenti, ponude, računi, naplate, PDF i šifrirani izvoz.

## Datoteke

- `Fakturiva-v0.3.0-preview.apk` — debug-potpisani instalacijski paket za isprobavanje.
- `Fakturiva-v0.3.0-preview-UNSIGNED.aab` — **nepotpisani** Android App Bundle, nije spreman za prijenos na Google Play bez trajnog upload ključa.
- `SHA256SUMS.txt` — kontrolni zbrojevi za provjeru preuzimanja.

Ovo je **razvojni pretpregled**, ne produkcijsko porezno rješenje. Prolazna kompilacija, lint i unit smoke testovi nisu zamjena za mjerenje 1:1 podudarnosti na uređajima ili za opsežne funkcionalne testove.

**Obvezna napomena:** PDF nije eRačun; fiskalizacija ni razmjena eRačuna nisu implementirane. Podaci su lokalni — obvezno čuvajte izvezenu šifriranu kopiju izvan uređaja.
