# Fakturiva — kontrola Android izdanja

## Funkcionalni kriteriji
- Računi, ponude, klijenti, artikli i usluge, naplata i PDF izvoz rade s pravim podacima.
- Porezni identifikacijski broj je neobavezan; unos može sadržavati međunarodne oznake.
- Prikaz i PDF više ne sadrže razvojne poruke i napomene o nedostupnim integracijama.
- U Postavkama radi Pomoć s prečacima za račune, ponude, klijente i profil.
- Nacrti, potvrđeni dokumenti, uplate i sigurnosne kopije ostaju pouzdani.

## Provjere prije distribucije
- Android build/lint i instrumentacijski testovi moraju proći na završnom SHA-u.
- Testirati najmanje 9 Android profila, uključujući male telefone i povećani font.
- Provjeriti PDF i sadržaj s izvornim identifikacijskim brojevima, stranim adresama i praznim poreznim brojem.
- Zaslon 17 promijenjen je u Pomoć; stari dizajnerski prikaz integracija više nije cilj vizualnog usklađivanja.
- Produkcijski paket zahtijeva odgovarajući potpisni ključ i test instalacije/nadogradnje.
- Prije migracije podataka preporučiti stvarno provjerenu sigurnosnu kopiju.

Dokument služi za kontrolu izdanja; nije korisnički tekst u aplikaciji.
