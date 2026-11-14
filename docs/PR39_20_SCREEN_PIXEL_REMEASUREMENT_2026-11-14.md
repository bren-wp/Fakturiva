# PR #39 Android pixel review — 14 November 2026

**Source image provenance:** owner-supplied private `Fakturiva_Android_Brending_i_20_Ekrana(8).zip`, `ekrani/*.png` at 1179×2556; real GitHub Actions artifact `Fakturiva-Android36-real-screenshots` ID `14096570763` from successful emulator run `43550726980` on commit `c9ac6cbdc3c63933e9a9b5c9d9eb08536616abc4`. Reference PNGs remain **private** and are not committed.

**Measurement:** exact RGB byte comparison with no resizing, no threshold; SSIM by scikit-image. Baseline mean RGB MAE 8.2728425 / 255. Exactly identical: **0 / 20**. A successful screenshot workflow is proof of capture/test completion, not pixel parity. The current dark/home/Settings UI fixes were made **after** that measured commit. A fresh artifact from the later commit is required to measure their actual effect.

## Per-screen baseline

| Screen | MAE (0–255) | SSIM | Changed pixels |
| --- | ---: | ---: | ---: |
| 01_pocetni_zaslon | 1.90286 | 0.979630 | 91.95318 % |
| 02_dobrodosli | 4.93390 | 0.931694 | 28.24968 % |
| 03_prijava | 6.70446 | 0.905194 | 15.10073 % |
| 04_pocetna | 10.69375 | 0.848134 | 43.60632 % |
| 05_racuni | 9.11282 | 0.840726 | 27.87623 % |
| 06_novi_racun | 8.75148 | 0.846021 | 22.26128 % |
| 07_stavke_racuna | 8.86104 | 0.867118 | 26.86011 % |
| 08_pregled_racuna | 7.79262 | 0.866618 | 28.76443 % |
| 09_ponude | 7.79330 | 0.857043 | 25.16545 % |
| 10_nova_ponuda | 8.75464 | 0.850318 | 22.00437 % |
| 11_pregled_ponude | 9.60288 | 0.851431 | 30.69941 % |
| 12_klijenti | 7.45489 | 0.867443 | 28.65831 % |
| 13_detalji_klijenta | 9.13940 | 0.874939 | 24.04305 % |
| 14_artikli_usluge | 7.43472 | 0.867201 | 25.05323 % |
| 15_izvjestaji | 8.35253 | 0.874082 | 26.23682 % |
| 16_naplata | 7.78145 | 0.869595 | 32.68008 % |
| 17_eracun_integracija | 7.54388 | 0.871644 | 20.78583 % |
| 18_postavke | 9.02353 | 0.834234 | 20.70450 % |
| 19_profil_tvrtke | 8.22251 | 0.857224 | 20.88369 % |
| 20_tamna_tema | 15.60019 | 0.801675 | 44.41242 % |

## Review findings and actionable fixes

1. **Dark Home (screen 20):** date/greeting positioned ~10–13 physical px below the owner reference while KPI hero y-offset is much smaller; adjust vertical rhythm independently instead of moving every element. Hero avatar was oversaturated electric blue compared with the reference's lighter lavender. The quick-action and recent-document header typography was larger and chevron heavier than reference. Implemented dark-specific greeting/hero spacing and shared section typography; must remeasure.
2. **Light Home (screen 04):** quick-action/recent-document headers and chevron appear oversized; first-column stats and quick-action cards begin roughly 3 physical pixels farther right. Implemented compact header type and 2dp horizontal card margins. Must remeasure.
3. **Settings (screen 18):** first visible list incorrectly started with Language, Currency and printer settings, hiding the nine core business rows visible in the owner reference. Reordered the first section to company, numbering, VAT/taxes, payment methods, document look, eInvoice/fiscalization, security, backups and theme. Preserved language, currency, USB/LAN printers, support, account privacy and all other actions in **Dodatne postavke** below. Regression test verifies preserved callback targets.
4. **Quote preview, client details, invoices (11, 13, 05):** still show visible typographic, element-alignment and synthetic-data variations from the references. No pixel-perfect claim; fix in order after current changes are measured.

**Data integrity:** the dashboard paid-revenue sparkline must use actual local confirmed receipts, not generated zigzags designed solely to resemble a reference image. A screenshot fixture's invoice number differs from the mockup; never alter real stored invoices to fit a mockup. Time/status/navigation bars can also differ legitimately by emulator/OS and must be distinguished from app UI mistakes. No fake screenshots or overlays.

## Exit criteria

- Fresh real Android emulator artifact on **exact final commit**; exact RGB/SSIM comparison of all 20 reference screens and separate attribution of system UI differences.
- Verify font scaling, tablet/small-screen layout, orientation where applicable, RTL/locale fallback, touch-target/accessibility descriptions, gesture navigation and light/dark themes on real supported hardware.
- Pixel 1:1 currently remains **unverified**; exact match 0/20 on the documented baseline. Keep release and PR gates explicit.
