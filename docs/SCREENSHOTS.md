# Fakturiva — stvarne Android snimke i vizualna provjera

Fakturiva se predstavlja isključivo **stvarnim snimkama pokrenute Android aplikacije**. Originalne korisnikove dizajnerske reference (`Fakturiva_Android_Brending_i_20_Ekrana(8).zip`, PNG 1179 × 2556 px) služe za mjerenje; **nisu** ugrađene kao statične pozadine aplikacije.

## Galerija od 20 stvarnih zaslona

[Otvorite galeriju u README-u](../README.md#pogledajte-fakturivu-u-radu).

Svaka slika `docs/screenshots/NN_naziv.png` nastaje pomoću `ci/capture_screenshots.sh`, pokretanjem stvarnog debug APK-a u Android emulatoru. Funkcionalne akcije, tekstovi, forme, SVG ikone i drugi elementi izrađeni su Android komponentama; galerija nije generirana iz dizajnerskog PNG-a.

**Provenijencija ažuriranja za v0.11.0-preview.1:** `.github/workflows/preview-release.yml` pri objavi pokreće Android 16 emulator i `ci/run_android_qa.sh`:
1. Snima **20 jedinstvenih APK screenshotova** na profilu 1179 × 2556 px.
2. Provjerava `uiautomator` XML hijerarhiju: zaslon mora biti stvarna aplikacija, bez Android dijaloga preko nje, s pristupačnim kontrolama unutar zaslona.
3. Izvodi instrumentacijske testove i zaustavlja izdanje ako bilo što padne.
4. Ažurira praćene PNG-ove u `docs/screenshots` isključivo snimkama upravo testiranog APK-a.
5. Objavljuje verzionirani preview paket i SHA-256 kontrolne sažetke uz točan commit.

Ako QA ne uspije, **nema objave novog izdanja niti zamjene galerije**. Osjetljivi produkcijski podaci ne koriste se u testnom emulatoru.

## Kako se provjerava 1:1?

Skripta `quality/pixel_audit.py` uspoređuje svaki RGB kanal, dimenzije, SSIM i postotak različitih piksela bez skaliranja, prekrivanja razlika ili izmjene izvornika. Testirana matrica sastoji se od devet emulator profila (od 280 dp do 800 dp, uz veći font i pejzažni način).

**0% razlike nije potvrđeno.** Različiti stvarni datumi, poslovni podaci, Android sistemske trake i fontovi mogu se razlikovati od statičnih referenci. Rezultat visual QA-a ne smije se zamijeniti potvrdom zakonske usklađenosti, integracije eRačuna, produkcijskog potpisivanja ili uređaja svih proizvođača.

[Android testovi i workflowi](https://github.com/bren-wp/Fakturiva/actions) · [Izdanja](https://github.com/bren-wp/Fakturiva/releases)
