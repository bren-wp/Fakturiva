# Fakturiva — provjera na fizičkim Android uređajima

**Status:** još nije provedena na stvarnom telefonu iz ovog izvršnog okruženja. Sama uspješna emulatorska provjera nije ekvivalentna provjeri na fizičkom uređaju.

## Automatizirana provjera na vlastitom uređaju

Na testnom telefonu uključite USB debugging i autorizirajte vlastitu radnu stanicu. Ne preporučuje se korištenje telefona s jedinim primjerkom stvarnih računa; za testiranje koristite isključivo primjer podatke. Pokrenite u korijenu projekta:

`bash ci/test-physical-android-devices.sh`

Skripta namjerno odbija emulatore, provjerava Android API 24+, gradi i instalira debug aplikaciju te izvršava izolirane instrumentacijske testove podataka. Testovi ne trebaju mrežu, registraciju ni vanjske runtime biblioteke.

## Ručna matrica prije produkcije

| Scenarij | Očekivani rezultat |
| --- | --- |
| Prvi start nakon instalacije | Bez pada; jasno označeno da nema fiskalizacije ni eRačun razmjene |
| 20 ekrana; svijetla i tamna tema | Nema preklapanja, odsijecanja ili skrivenih akcija; usporediti sa stvarnim PNG referencama |
| Unos i kasnije izmjene profila tvrtke i klijenta | Novi dokument sadrži aktualne podatke, potvrđeni povijesni dokument ostaje nepromijenjen |
| Kreiranje računa, ponude, datum usluge, stavke | Izračun u centima, uredna validacija, unosi se čuvaju |
| Slanje aplikacije u pozadinu i force-stop usred uređivanja | Nacrt se može nastaviti bez gubitka već spremljenih podataka |
| Ponovljena potvrda računa | Nema dvostrukih brojeva ili dvostrukog knjiženja |
| Djelomična uplata, prekomjerna uplata, neispravan datum | Točan ostatak, odbijanje neispravne transakcije, obrazac ostaje otvoren |
| PDF s 100+ stavki i dugim adresama | Uredan višestranični PDF, podaci se ne skraćuju |
| CSV export uplata | Lokalna datoteka i ispravan redoslijed zapisa |
| Izvoz šifrirane kopije, test krive lozinke | Kriva lozinka ne mijenja postojeću bazu |
| Obnova oštećene kopije | Zamjena baze se odbija i originalni dokumenti ostaju |
| Prekid pisanja, ponovno pokretanje uređaja | Sačuvana SQLite baza i barem posljednja neoštećena lokalna kopija |
| Nadogradnja preko prethodne verzije bez brisanja aplikacije | Stari računi, ponude, numeracija i naplate ostaju |
| Gubitak ili reset uređaja | Povrat je moguć samo ako postoji provjerena šifrirana kopija izvan uređaja |
| Više uređaja: API 24, 28/29, 33, 35+ | Sve funkcije provjerene na realnoj tipkovnici, DPI-ju i sistemskoj navigaciji |

## Kriterij za produkciju

Ne spajati PR u produkcijsku granu niti tvrditi da postoji 100% pouzdanost dok nisu provedeni stvarni testovi i formalni sigurnosni pregled. Potrebni su produkcijski ključ za potpis, usklađenost s hrvatskim propisima i zasebna mrežna fiskalizacija/eRačun integracija ako je obvezna.

> Lokalna pohrana može biti iznimno pouzdana, ali nikad ne može jamčiti nulti rizik od gubitka pri gubitku uređaja, brisanju aplikacije ili kvaru memorije.
