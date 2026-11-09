# Fiskalizacija 1.0 i Fiskalizacija 2.0 — sigurnosna i zakonska specifikacija

**Status 9. 11. 2026.: projekt ima infrastrukturne preduvjete, ali
nema certificiranu produkcijsku integraciju s Poreznom upravom.**
Dok se ne završe službene sheme, integracijski testovi,
pristupni podaci i ovlaštenje, Fakturiva ne smije lažno prikazivati
račune kao fiskalizirane.

## Dva odvojena načina

| Sustav | Namjena | Podatci i provjere | Produkcijski status |
|---|---|---|---|
| Fiskalizacija 1.0 | Primjenjivi B2C računi i potvrda Porezne | Prijavljen poslovni prostor, oznaka naplatnog uređaja, operater, OIB, brojenje računa, valjani certifikat, potpis, zahtjev/odgovor, JIR/ZKI i QR pod propisanim uvjetima | Adapter i stvarno povezivanje **nisu dovršeni** |
| Fiskalizacija 2.0 / eRačun | Strukturirani računi među hrvatskim poduzetnicima | EN 16931 / propisana XML struktura, KPD stavki, pristupna točka ili propisana razmjena, identiteti primatelja, statusi zaprimanja, izvještavanje i potvrde | Stvarna razmjena **nije dovršena** |

Obveze su fazne: prema uputama Porezne uprave od 1. 1. 2026.
obveznici PDV-a izdaju i zaprimaju eRačune u odgovarajućim tuzemnim
B2B odnosima; obveznici izvan sustava PDV-a tijekom 2026.
primarno zaprimaju, a obveza izdavanja im se širi od
1. 1. 2027. Primjena na svakoga korisnika provjerava se
prema njegovu stvarnom statusu i slučaju izdavanja.

## Certifikati

- Korisnik mora biti ovlašteni vlasnik certifikata. Prihvati
  samo provjerenu vrstu privatnog ključa/certifikata i
  odgovarajuću lozinku; ne držati privatni ključ ili lozinku
  u GitHubu, kodu aplikacije, plain JSON-u, logovima ili
  Android SharedPreferences bez sigurne enkripcije.
- Predviđena je upload konfiguracija s krajnjim šifriranjem
  te autentificiranom spremom vezanom uz korisnikov identitet,
  periodom valjanosti i mogućnošću opoziva/rotacije.
  **Upload PKCS#12/PFX datoteka u produkciji još nije omogućen.**
- Povezivanje s produkcijskim endpointima aktivirati tek nakon
  provjere certifikata, pristupne točke, propisane poruke,
  povratne potvrde i zakonske osnove korisnika.
- Certifikate čuvati odvojeno od baze s računima uz pristup
  samo ovlaštenom potpisnom procesu. Ne slati certifikat
  vanjskim servisima bez pravne i sigurnosne provjere.

## Stanja isporuke

`NEPOSTAVLJENO` → `KONFIGURIRANO` → `POSLANO` →
`POTVRĐENO` ili `ODBIJENO`.

- Bez potvrde ne prikazivati JIR, ZKI, izmišljeni QR niti
  tvrdnju „fiskalizirano”.
- Nikad ne ponavljati slanje bez idempotentnog identifikatora
  i čuvanja prethodnog zahtjeva/odgovora.
- Ne dopustiti ručnu promjenu broja ili iznosa ranije
  izdanog i potvrđenog dokumenta.
- Integracija mora pravilno čuvati stanja greške, naknadne
  potvrde, odbijanja, tehnički prekid, rokove i ponavljanja.
- Ako fiskalizacija nije konfigurirana, Fakturiva može
  izrađivati obične nacrte ili dokumente gdje je to
  dopušteno, ali **ne smije se predstavljati kao alat
  kojim se izvršava zakonski obvezna fiskalizacija**.
  U scenariju gdje korisnik mora fiskalizirati račun,
  potvrda i izdavanje nisu produkcijski spremni bez adaptera.

## Prije javnog izdanja obavezno

1. Uvesti aktualne službene XSD/WSDL specifikacije i njihove kontrolne sume;
   potvrditi aktualnu produkcijsku i testnu adresu usluge.
2. Provjeriti certifikat korisnika, izdavatelja i ovlasti pristupne točke.
3. Implementirati kriptografsko potpisivanje prema službenoj specifikaciji,
   provjeru odgovora, retries, idempotenciju i nadzor grešaka.
4. Validirati 2025/2026 KPD klasifikacije na stavkama eRačuna;
   imati verzije prema datumu propisa.
5. Provesti testne poruke, negativne scenarije, revizijski dnevnik
   i test obnove servera bez dupliciranja računa.
6. Dokumentirati podatke koji se šalju Poreznoj, čuvanje isprava
   i ovlasti korisnika za potrebe GDPR-a.

Službeni izvor: [Porezna uprava — eRačun i Fiskalizacija 2.0](https://porezna.gov.hr/fiskalizacija/bezgotovinski-racuni/eracun).
