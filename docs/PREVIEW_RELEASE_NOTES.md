# Fakturiva v0.6.0-preview.2 — Android razvojno izdanje

**Računi i ponude, jednostavno.** Native Android aplikacija s lokalnom SQLite pohranom, osobnim poslovnim profilom, evidencijom klijenata, naplatom i PDF dokumentima bez obveznog oblaka.

## Novo u v0.6

- Ispravljeno odsijecanje natpisa brzih radnji na početnom i tamnom zaslonu na temelju stvarnog emulatorskog testa.
- U marketing README ugrađeno svih 20 stvarnih Android screenshots, ne makete.

- Tri stvarna prikaza naplate: sve uplate, tekući mjesec i nenaplaćeni računi.
- Izvoz evidencije uplata u lokalni UTF-8 CSV; klijentski unosi zaštićeni od izvršavanja formula u tabličnom programu.
- Ugrađeni Android PdfRenderer za pregled pravog PDF-a računa i ponude, s povezanim akcijama spremanja i dijeljenja.
- Bolja validacija novčane uplate i datuma bez gubitka unosa u obrascu.
- Tamna tema usklađena s uzorkovanim RGB vrijednostima korisnikovih izvornih zaslona.
- Stroga provjera financijskih iznosa pri povratu sigurnosne kopije; odbijanje neispravne kopije i oporavak Android AtomicFile povijesne kopije.
- Regresijski Android testovi: višestranični PDF, djelomična uplata, dvostruko numeriranje, ponovno otvaranje baze i oporavak.
- Postupak za testiranje na stvarnim fizičkim Android uređajima u `docs/PHYSICAL_DEVICE_ACCEPTANCE.md`.

## Što je u paketu

- `Fakturiva-v0.6.0-preview.2-debug.apk` — APK potpisan Android debug ključem, **isključivo za testiranje**.
- `Fakturiva-v0.6.0-preview.2-DEBUG-SIGNED.aab` — razvojni AAB s debug ključem, **nije produkcijski Play Store paket**.
- `Fakturiva-v0.6.0-preview.2-UNSIGNED.aab` — release bundle bez produkcijskog potpisa, ne može se izravno poslati na Play bez ispravnog ključa.
- `SHA256SUMS.txt` — provjera integriteta.

## Status kvalitete

Snimke svih 20 originalnih referenci uspoređuju se sa stvarnim Android emulatorskim snimkama. **Potpuna 1:1 podudarnost nije potvrđena.** Testiranje na fizičkim uređajima nije izvršeno u ovom okruženju i preduvjet je za produkcijsko izdanje.

Fakturiva zasad **ne implementira fiskalizaciju ni razmjenu strukturiranih eRačuna**. Izvezeni PDF nije dokaz fiskalizacije. Nijedna aplikacija s isključivo lokalnom pohranom ne može jamčiti da podaci neće biti izgubljeni pri gubitku ili brisanju uređaja; izvezite provjerene šifrirane kopije izvan telefona.

**Fakturiva by Brendigo**.
