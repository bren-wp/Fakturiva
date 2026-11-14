# Fakturiva — smjer proizvoda

Fakturiva je aplikacija za izradu računa i ponuda, vođenje klijenata, katalog artikala i usluga, evidenciju uplata, izvještaje i PDF dokumente.

## Zadržati i razvijati
- Brz tijek: izdavatelj → klijent → stavke → pregled → potvrda → PDF.
- Pouzdanu numeraciju, zaključavanje potvrđenih dokumenata i točne obračune.
- Odvojene poslovne podatke za postojeće dokumente i novu dokumentaciju.
- Neobavezne međunarodne porezne identifikacijske brojeve, adresu i državu izdavatelja.
- Zaštitu pristupa, sigurnosne kopije i oporavak podataka.
- Responsivno sučelje, tamnu temu i provjerene prikaze računa i ponuda.
- Jednostavnu pomoć i funkcionalne prečace za korisničke radnje.

## Planirano za potpuniju međunarodnu uporabu
- Odabir valute po dokumentu i ispravno čuvanje valute u potvrđenom dokumentu.
- Prijevodi korisničkog sučelja, formatiranje datuma i brojeva po jeziku korisnika.
- Prilagodljivi porezni nazivi i stope za različite poslovne slučajeve.
- Dodatni predlošci za prikaz i izvoz PDF dokumenata.

## Kontrole kvalitete
- Svi testovi kompilacije, obračuna, oporavka, navigacije i radnih nacrta.
- Usporedba sa stvarnim Android snimkama na raznim veličinama zaslona.
- Zaslon 17 prikazuje **eRačune i fiskalizaciju** kao pripremu i postavke integracije. Stvarna produkcijska razmjena i certifikacija nisu potvrđene; postojeća referenca zaslona 17 i dalje se mjeri u vizualnom auditu.
- Bez lažnih poslovnih podataka, nedostupnih funkcija ili demonstracijskih poruka u aplikaciji.
- Produkcijska isporuka samo nakon provjere potpisa, testova i korisničkih tokova.

Dokumenti su predlošci za poslovnu upotrebu. Korisnik treba provjeriti zahtjeve koji vrijede u njegovoj državi. U kodu postoje podrška za valutu dokumenta i više valutnih oznaka; ispravnost prikaza, zaokruživanja i usporedne Android–web–Windows obrade svake podržane valute treba zasebno potvrditi.
