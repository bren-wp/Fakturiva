# Fakturiva razvojni plan

## Implementirano u prvom PR-u
- Nativni Android projekt bez vanjskih runtime biblioteka.
- Lokalna baza klijenata, kataloga, ponuda, računa i uplata.
- Izrada nacrta dokumenta sa stavkama, obračunom centi i PDV-a.
- Numeriranje tek prilikom potvrde; pretvaranje ponude u novi nacrt računa.
- PDF dokumenti, izvoz/dijeljenje, postavke tvrtke i statistika.
- Šifrirani lokalni izvoz i uvoz sigurnosne kopije (lozinka).
- Temelj sučelja prema 20 dostavljenih vizualnih referenci.

## Potrebno prije produkcije
- Usporedba svih 20 ekrana sa stvarnim snimkama emulatora u više rezolucija; ovo je nativna interpretacija dizajna, ne potvrđena pixel-perfect preslika.
- Automatizirani UI, instrumentacijski i migracijski testovi te Android uređaji.
- Fiksiranje pravila računovodstva, razlika obrtnik/tvrtka, PDV kategorija, stope, datum prometa, JIR/ZKI/QR ili eRačun XML kada su primjenjivi.
- Fiskalizacija i eRačun putem odgovarajućih ovlaštenih sustava: nemoguće bez mreže i propisanih vjerodajnica.
- Pravila izmjene, storna, razmjene, sljedivosti, zakonodavna i sigurnosna revizija.
- Zaštita korisnika PIN/biometrijom ako bude potrebna.
- Release signing, Play Store dokumentacija, pristupačnost i testovi oporavka backup-a.
