# Fakturiva — pregled izvornog dizajna prijave i svih 20 zaslona

Datum: 9. studenoga 2026.

## Izvor i ograničenja

Uspoređene su **izvorne 1179×2556 PNG reference** i **stvarne Android snimke označene v0.17**, iz originalnog korisničkog ZIP-a `Fakturiva_SVE_REFERENCE_20_EKRANA_BRENDING_USPOREDBE.zip`. Bez skaliranja, poravnanja, izrezivanja ili uljepšavanja: svi su parovi 1179×2556. RGB MAE je srednja apsolutna razlika triju kanala od 0 do 255; postotak različitih piksela je udio piksela gdje se barem jedan kanal razlikuje. **To nisu mjerenja najnovijeg PR-a, niti jamstvo sukladnosti.**

| Zaslon | Naziv | RGB MAE /255 | Različiti pikseli |
|---|---|---:|---:|
| 01 | Splash | 1.9077 | 91.944% |
| 02 | Dobrodošli | 7.2573 | 30.356% |
| 03 | Prijava* | 6.3458 | 11.172% |
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
| 17 | eRačuni — referentni zaslon | 8.8714 | 47.956% |
| 18 | Postavke | 9.0120 | 20.470% |
| 19 | Profil tvrtke | 7.6064 | 20.310% |
| 20 | Tamna tema | 15.9911 | 75.830% |

## Kritičan nalaz — prijava i registracija

**Referenca `03_prijava.png` pokazuje e-adresu i lozinku, opcije „Zapamti me”, „Zaboravili ste lozinku?”, biometrijsku prijavu i link „Registrirajte se”.** U stvarnoj v0.17 snimci na putanji `03_prijava.png` prikazan je **zaslon sigurnosti uređaja („Zaštitite svoje poslovanje”)**, a ne prijava. Izračunati MAE 6.3458 i 11.172% zato **nisu validna mjera vizualne podudarnosti prijave**: slike ne predstavljaju isti ekran i dominantna bijela pozadina prikriva funkcionalnu razliku.

Trenutačni izvorni Android UI također prikazuje lokalnu prijavu PIN-om, a referenca traži prijavu e-adresom i lozinkom. Nužno je prebaciti autentifikaciju na poslužiteljski račun i mjeriti **pravi login protiv pravog login referentnog zaslona**, nakon testiranja registracije, oporavka, grešaka i odsutne mreže.

Paket s 20 referenci **ne sadrži zasebnu sliku registracije**: zaslon `03` je prijava, a registracija se ne može korektno ocijeniti numeričkom razlikom bez iste izvorne referentne slike. Za registraciju treba provjeriti odgovarajući drugi izvorni predložak prije tvrdnje o 1:1 podudarnosti.

## Prioriteti za nastavak

1. Prijava/registracija: stvarna funkcionalnost i originalna referenca, bez lažnih 0% odstupanja; nova konfiguracija zaslona i autentični screenshotovi.
2. Tamna tema (#20), ponude (#09), računi (#05), naplata (#16) i katalog (#14) imaju visoke postotke promijenjenih piksela u staroj usporedbi.
3. Novi build: snimiti stvarne reference svih 20 zaslona, mjeriti MAE/SSIM/različite piksele i potvrditi korisničke tokove na različitim DPI rezolucijama.
4. Ne uklanjati eRačune ili fiskalizaciju po zadnjoj odluci vlasnika. Njihove funkcije ostaju zadržane/planirane i smiju se oglašavati kao produkcijski verificirane tek nakon provjere certifikata, sučelja i zakonskih obveza.

## Domena

Službeni host je `https://fakturiva.eu`; sadržaj, Android i Windows moraju uporabljati tu domenu. Prije promjene produkcijskih URL-ova provjeriti SSL certifikat, DNS, e-poštu, odgovarajuće GDPR kontakte i dostupnost web API-ja. Nikad ne prepisati pohranjene prilagođene postavke korisnika prisilno.

