# Fakturiva — siguran postupak izdavanja pretpregleda

Ovaj dokument opisuje tehničke kontrole GitHub izdanja. Ne zamjenjuje sigurnosni, financijski ni vizualni audit.

## Redoslijed

1. Sve izmjene, uključujući snimke zaslona iz stvarnog APK-a, moraju proći kroz zasebnu granu i Pull Request; **nikada ne commitati izravno u `main`**.
2. Kad je odobreni PR spojen u `main`, `.github/workflows/preview-release.yml` provjerava `versionName` iz `app/build.gradle` i postojanje pripadnog taga/izdanja. Već objavljen tag ne mijenja se i ne izdaje ponovno.
3. Sva petorica obveznih workflowa pokreću se na **svakom pushu u `main`**, bez path filtra. Na istom commit SHA moraju uspjeti **Android verification**, **Verify native Android screens**, **Responsive Android screen matrix**, **Self-hosted Fakturiva JSON API security smoke** i **Fakturiva Windows preview build**. Za izmjene release workflowa ili Android verzije relevantni workflowi izričito se aktiviraju.
4. Release posao ponovno gradi Android/Windows, pokreće Android instrumentaciju i snima svih 20 stvarnih zaslona. Svi zasloni moraju proći provjere autentičnosti, pristupačnosti, rezolucije i jedinstvenosti. Razlika u odnosu na verzioniranu README galeriju **prijavljuje se upozorenjem i popisom različitih datoteka**, ali sama binarna razlika (npr. zbog sistemskog vremena emulatora) nije dokaz regresije i ne blokira preview. Svježe snimke prilažu se izdanju; zastarjelu galeriju smije se uskladiti isključivo kroz novi PR. Snimke se **ne** committaju i **ne** pushaju iz GitHub Actions.
5. Ako su sve provjere uspješne, objavljuju se immutable preview release artefakti: Android testni APK/AAB, nepotpisani release AAB, Windows setup/portable, web/source ZIP, ZIP stvarnih snimki i SHA256SUMS. Release target je testirani `GITHUB_SHA`.
6. Ako provjera padne, prvo utvrditi uzrok, u novom PR-u ispraviti kod ili ažurirati stvarne snimke i ponovno provesti CI. Ne gasiti testove ili zaobilaziti PR.

## Posebno važne granice

- Originalne dizajnerske reference nisu isto što i screenshotovi pokrenute aplikacije. Release gate dokazuje konzistentnost stvarnih snimki s pregledanim datotekama, **ne dokazuje pixel-perfect 0 %**. MAE, SSIM i pixel-diff zahtijevaju originalne reference.
- Debug-potpisani Android APK/AAB i nepotpisani release AAB nisu produkcijski Google Play potpis. Bez stvarnog keystorea i potpunih provjera nije dopušteno tvrditi suprotno.
- Povijesni `pr27-preview-candidate.yml` ostaje dostupan samo ručnim pokretanjem za testne buildove, bez ovlasti objave izdanja. Time se ne mijenjaju postojeće objave.

## Obvezna zaštita grane main — ručno na GitHubu

**Provjera 13. 11. 2026.:** `main` je bio nezaštićen (`protected=false`), a repozitorij nije imao ruleset. Sam GitHub Actions YAML ne sprječava ručni izravni push.

Vlasnik repozitorija treba u **Settings → Rules → Rulesets** dodati aktivan branch ruleset za `main`, s ovim zahtjevima:

- Za sve promjene zahtijevati Pull Request (po mogućnosti i odobrenje pregleda).
- Zahtijevati zelene relevantne status provjere iz stvarnog PR-a; ne koristiti samo tekstualne nazive workflowa ako su im check-run nazivi različiti.
- Blokirati force push i brisanje grane te ograničiti zaobilaženje pravila.
- Onemogućiti izravni push u `main` i računima i automatizacijama koje nemaju izričitu iznimku za hitne administrativne zahvate.
- Provjeriti pravila na testnom PR-u i nakon aktiviranja evidentirati tu promjenu u repozitoriju.

Bez tih postavki politika zabrane rada u `main` ostaje djelomično proceduralna.
