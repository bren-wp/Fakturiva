# Autentični Android zasloni i pixel audit

Fakturiva ima **20 vizualnih referenci** od vlasnika, svaku u rezoluciji **1179 × 2556 px**.

Stvarne snimke preuzimaju se iz izoliranog Android emulatora na istom profilu i razlučivosti, bez grafički generiranih ilustracija. Testni podaci su jasno odvojeni od korisničkih podataka.

**Prvi komplet od 20 autentičnih Android snimki:**
[Preuzmi galeriju iz GitHub Actions](https://github.com/bren-wp/Fakturiva/actions/runs/40782723051/artifacts/12857668423).

U repozitoriju `docs/PIXEL_AUDIT.md` dokumentirana su izmjerena odstupanja za svih 20 ekrana, a `quality/pixel_audit.py` proizvodi ponovljivu RGB provjeru.

Nova provjera uključuje instrumentacijske testove na emulatoru prije snimanja. Ako GitHub Action nema pravo zapisivati u repozitorij, fotografije **ostaju sačuvane kao Actions artefakt**, bez lažnih prikaza u glavnom README-u.

Autentične snimke u `docs/screenshots` i u marketing README automatski se dodaju nakon što je GitHub Actions workflow tokenu omogućena write dozvola.
