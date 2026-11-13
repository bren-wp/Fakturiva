# Fakturiva — stvarne Android snimke i vizualna provjera

Fakturiva se predstavlja isključivo **stvarnim snimkama pokrenute Android aplikacije**. Originalne korisnikove dizajnerske reference (`Fakturiva_Android_Brending_i_20_Ekrana(8).zip`, PNG 1179 × 2556 px) služe za mjerenje; **nisu** ugrađene kao statične pozadine aplikacije.

## Galerija od 20 stvarnih zaslona

[Otvorite galeriju u README-u](../README.md#pogledajte-fakturivu-u-radu).

Svaka slika `docs/screenshots/NN_naziv.png` nastaje pomoću `ci/capture_screenshots.sh`, pokretanjem stvarnog debug APK-a u Android emulatoru. Funkcionalne akcije, tekstovi, forme, SVG ikone i drugi elementi izrađeni su Android komponentama; galerija nije generirana iz dizajnerskog PNG-a.

**Provenijencija i postupak od v0.18.4-preview.1:** `.github/workflows/preview-release.yml` pri objavi pokreće Android 16 emulator i `ci/run_android_qa.sh`:
1. Snima **20 jedinstvenih APK screenshotova** na profilu 1179 × 2556 px.
2. Provjerava `uiautomator` XML hijerarhiju: zaslon mora biti stvarna aplikacija, bez Android dijaloga preko nje, s pristupačnim kontrolama unutar zaslona.
3. Izvodi instrumentacijske testove i zaustavlja izdanje ako bilo što padne.
4. Provjerava svih 20 stvarnih PNG-ova i uspoređuje ih s galerijom u `docs/screenshots`. Ako se binarno razlikuju, razlika se objavljuje kao upozorenje, uz izvještaj, a novi preview uključuje svježe stvarne snimke. Galerija se može ažurirati **samo putem zasebnog PR-a**, nikad izravnim zapisivanjem u `main`. Binarna razlika sama po sebi ne dokazuje vizualnu regresiju jer snimke ovise i o satu emulatora.
5. Nakon prolaska svih CI provjera objavljuje preview paket, ZIP svih 20 stvarnih snimki i SHA256SUMS uz točan commit.

Ako QA ne uspije, **nema objave novog izdanja niti zamjene galerije**. Osjetljivi produkcijski podaci ne koriste se u testnom emulatoru.

## Kako se provjerava 1:1?

Skripta `quality/pixel_audit.py` uspoređuje svaki RGB kanal, dimenzije, SSIM i postotak različitih piksela bez skaliranja, prekrivanja razlika ili izmjene izvornika. Testirana matrica sastoji se od deset emulator profila (od 280 dp do 800 dp, uz veći font i pejzažni način).

**0% razlike nije potvrđeno.** Bez izvornih 20 dizajnerskih referenci nije dopušteno prijaviti novu MAE/SSIM vrijednost. Debug fixture datumi nacrta za ekrane 06/07 fiksirani su na testno razdoblje kako usporedbe ne bi ovisile o datumu izvođenja. Različiti stvarni datumi, poslovni podaci, Android sistemske trake i fontovi mogu se razlikovati od statičnih referenci. Rezultat visual QA-a ne smije se zamijeniti potvrdom zakonske usklađenosti, integracije eRačuna, produkcijskog potpisivanja ili uređaja svih proizvođača.

[Android testovi i workflowi](https://github.com/bren-wp/Fakturiva/actions) · [Izdanja](https://github.com/bren-wp/Fakturiva/releases)
