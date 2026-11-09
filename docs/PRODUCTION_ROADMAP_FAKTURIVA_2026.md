# Fakturiva — centralni poslužitelj, administratorski panel i pravna usklađenost

## Status
Radna razvojna grana nastala iz aktualnog `main` nakon spajanja PR-a #26. **Nije završeno niti certificirano produkcijsko izdanje.** Funkcionalne i zakonske stavke provjeravaju se zasebno.

### Prioriteti
1. Centralna autentifikacija i poslužiteljska pohrana `web/storage/data` s izolacijom korisnika, povijesti promjena, prijenosom preko TLS-a, provjerom obnove i redovitim nezavisnim sigurnosnim kopijama.
2. Odvojeni administratorski pristup uz jaku autentifikaciju, pregled metapodataka bez pristupa šifriranom sadržaju, suspenziju, brisanje i kontrolu tarife (FREE, PRO, BUSINESS). Aplikacija ne oglašava tarife niti uvodi obmanjujuće obrasce plaćanja.
3. Fakturiva.com: registracija, prijava, korisnički panel, uvjeti, politika privatnosti i brisanje računa.
4. Odvojeni zasloni **Podrška** i **O aplikaciji** u Android postavkama. Transparentna obrada podataka kroz privatnost i Data Safety, bez programerskih tekstova.
5. Podrška za pravne oblike i države preko profila pravila; obrasci i obveze samo kada su službeno potvrđeni. Hrvatski NKD 2025/KPD 2025 s verzioniranjem prema datumima primjene.
6. Generiranje KPR/PO-SD na temelju stvarno potvrđenih uplata; validirani službeni XML tek nakon XSD/probnog uvoza.
7. Odvojeni opcionalni Fiskalizacija 1.0/2.0 adapteri sa sigurnim certifikatima; bez tvrdnji o fiskalizaciji prije stvarne potvrde.
8. Početna Windows implementacija u `/win` koja koristi isti server i dizajn. Izlazne .exe datoteke tek nakon uspješnog Windows CI builda i potpisivanja ako je primjenjivo.
9. Usporedba svih 20 referentnih Android ekrana bez izmišljanja 0% odstupanja. Bez generiranja slika u chatu.

## Kriteriji za objavu
- Android build, instrumentacija, web smoke, responsive QA i provjera korisničke izolacije moraju biti zeleni.
- Za deinstalaciju i povrat: automatska kopija mora biti **potvrđena na serveru** prije nego što se korisniku kaže da su podatci sigurni; nepoznate promjene i sukobi verzija ne smiju se prepisati.
- Poslužiteljska arhiva se zasebno kopira i oporavak se redovito testira (uključujući `storage/secret.php`).
- Pravni tekstovi ostaju nacrti do potvrde identiteta voditelja obrade, procesora, rokova i pravnog pregleda; Google Play Data Safety mora točno odražavati stvarni prijenos.
- Demo podatci samo u izoliranoj razvojnoj varijanti, nikad zajedničke tvorničke vjerodajnice u produkciji.

## Službene reference (prije produkcije ponovno provjeriti)
- [Porezna uprava: eRačun / Fiskalizacija 2.0](https://porezna.gov.hr/fiskalizacija/bezgotovinski-racuni/eracun)
- [NKD 2025 NN 47/2024](https://narodne-novine.nn.hr/clanci/sluzbeni/2024_04_47_800.html)
- [KPD 2025 NN 151/2024](https://narodne-novine.nn.hr/clanci/sluzbeni/2024_12_151_2491.html)
- [KPD izmjena NN 119/2026, primjena 1.1.2027.](https://narodne-novine.nn.hr/clanci/sluzbeni/2026_10_119_1423.html)
- [Google Play User Data](https://support.google.com/googleplay/android-developer/answer/10144311)
- [GDPR 2016/679](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
