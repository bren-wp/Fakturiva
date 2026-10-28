# Fakturiva 0.2.0 — razvojni pretpregled

Nastavak razvoja aplikacije Fakturiva s novim originalnim ikonama, šifriranim rotirajućim lokalnim kopijama, oporavkom stanja i uređivanjem spremljenih nacrta računa i ponuda.

**Datoteke pretpregleda**
- `Fakturiva-v0.2.0-preview.apk`: debug-potpisani Android APK za isprobavanje; nije produkcijsko potpisivanje.
- `Fakturiva-v0.2.0-preview-UNSIGNED.aab`: nepotpisani Android App Bundle; nije spreman za Google Play dok nije potpisan trajnim upload ključem.

**Provjere**: Gradle debug build, release bundle, lint i provjere aritmetike/kriptografije. Ručni testovi svih 20 zaslona i oporavka na Android uređaju nisu završeni.

**Važno**: PDF nije strukturirani eRačun niti zamjena za fiskalizirani račun. eRačun/fiskalizacija nisu implementirani. Podaci se čuvaju lokalno; bez izvezenog backup-a izvan uređaja nije moguće jamčiti oporavak nakon gubitka telefona.
