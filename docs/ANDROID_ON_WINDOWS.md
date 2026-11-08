# Fakturiva: Android aplikacija na Windows računalu

**Nema zasebne Windows aplikacije, .exe datoteke ni web-omotača.**
Fakturiva ostaje izvorni Android APK / AAB. Na Windows računalu može
raditi samo u kompatibilnom Android okruženju/emulatoru, uz ograničenja
tog okruženja. Instalacija APK-a izravno u Windows bez Android runtimea
nije podržana.

Microsoft Windows Subsystem for Android (WSA) i Amazon Appstore prestali
su biti dostupni putem Microsoft Storea 5. ožujka 2025., pa se **WSA
ne smije navoditi kao preporučeni aktualni način instalacije**.
Službena Microsoftova obavijest:
https://support.microsoft.com/en-us/windows/apps/mobileapps/troubleshooting-and-faq-for-mobile-apps-on-windows

## Jedna Android aplikacija, fleksibilan prozor

- Telefonski prikaz sa širinom manjom od 840 dp ostaje izvorni vertikalni
  mobilni prikaz, uključujući dizajnersku referencu od 393 dp.
- U širokom Android prozoru poslovni sadržaj i donja navigacija su
  centrirani i ograničeni na 840 dp, umjesto da se obrasci i gumbi
  rastegnu preko cijelog 16:9 monitora.
- Promjena veličine prozora / rotacija ne pokreće zaslon dobrodošlice
  i ne odbacuje trenutačno uređivanje obrasca. Android sam ponovno
  mjeri postojeći raspored; odvojeni uređaji s promjenom gustoće/fonta
  i dalje mogu pokrenuti uobičajeno ponovno stvaranje aktivnosti.
- Sve tipične dodirne kontrole rade i s mišem gdje ih Android runtime
  podržava; ScrollView omogućuje pomicanje sadržaja kotačićem miša.
- Uvode se Androidovi tipkovnički prečaci za poslovne zaslone:

| Prečac | Zaslon / radnja |
| --- | --- |
| Ctrl+1 | Početna |
| Ctrl+2 | Računi |
| Ctrl+3 | Ponude |
| Ctrl+4 | Klijenti |
| Ctrl+5 | Postavke |
| Ctrl+P | Ispis, samo kada je otvoren postojeći račun/ponuda |

Prečaci se obrađuju u Android runtimeu, ne kao globalni Windows prečaci.

## Provjera prije produkcije

Koristiti podržani Android emulator na Windowsu s odgovarajućom
verzijom Androida. Pokrenuti debug APK i ručno testirati sljedeće
veličine prozora te promjene veličine **dok je otvoren unos računa**:

| Virtualni zaslon | Namjena |
| --- | --- |
| 280 × 640 dp | Vrlo mali telefon |
| 393 × 852 dp | Izvorna referentna geometrija |
| 600 × 960 dp | Tablet, uski prozor |
| 840 × 720 dp | Širi tablet / emulatorski prozor |
| 1280 × 800 dp | Windows Android emulator, pejzažni prikaz |
| 1600 × 900 dp | Veliki monitor / Android desktop prikaz |

Na svakoj veličini: 20 zaslona, dodavanje i uređivanje klijenata,
artikala, ponuda i računa, scroll, potvrda, izvoz, otvaranje,
sigurnosna kopija, promjena jezika/valute, tipkovnica i miš.
Provjeriti da nema horizontalnog odrezivanja, preklapanja FAB-a,
zaglavlja, sustavne navigacije i tipkovnice.

Ispis na **fizičkom pisaču povezanom s Windows računalom** nije
automatski omogućen: ovisi o podršci Android emulatora za USB Host,
Bluetooth, LAN i Android Print Framework. Bez te podrške koristiti
funkciju lokalnog PDF izvoza i ispis kroz Windows na uobičajen način.

**Status:** adaptivna ograničenja širine i tipkovnički prečaci su
implementirani u kodu ove grane. Hardversko i cjelovito vizualno
testiranje još nije provedeno; 0% odstupanja nije potvrđeno.
