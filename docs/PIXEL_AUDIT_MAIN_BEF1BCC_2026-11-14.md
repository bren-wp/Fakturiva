# Fakturiva — latest verified 20-screen reference comparison
**Measured 14 November 2026 · baseline commit `bef1bcc67ef69f0b43312b23654f6d9a717da8ec`**

## Evidence and scope

- Reference: the owner's **private** `Fakturiva_Android_Brending_i_20_Ekrana(8).zip` (`ekrani/`, 20 PNGs, each 1179 × 2556); originals are not published in this repository.
- Actual: Android 36 emulator screenshots from [GitHub Actions run 43642215067](https://github.com/bren-wp/Fakturiva/actions/runs/43642215067), artifact `14133100499` (`Fakturiva-Android36-real-screenshots`), at the exact `main` SHA above.
- Results below reproduce the **user-supplied** `Fakturiva_main_bef1bcc_20_screen_pixel_audit_2026-11-14.csv` from that pair, not a new PR screenshot run.
- Measured unmodified RGB pixels at full size; RGB mean absolute error (MAE, 0–255), SSIM and mismatched-pixel count; **no reference masks, compositing, resizing, or private image uploads**.
- **20/20 compared; mean RGB MAE 8.229576/255; mean SSIM 0.868588; exact 1:1 = 0/20.**
- These are baseline values. The current PR has not yet demonstrated visual improvement. An updated measurement requires a matching new screenshot artifact and re-running `quality/pixel_audit.py`.

| # | Screen | RGB MAE /255 | SSIM | Unequal pixels | Unequal % |
|---|---|---:|---:|---:|---:|
| 01 | Početni zaslon | 1.902857 | 0.979630 | 2,771,031 | 91.953% |
| 02 | Dobrodošli | 4.931798 | 0.931727 | 851,358 | 28.251% |
| 03 | Prijava | 6.700790 | 0.905224 | 455,055 | 15.100% |
| 04 | Početna | 10.503165 | 0.849279 | 1,304,386 | 43.284% |
| 05 | Računi | 9.112940 | 0.840701 | 840,106 | 27.878% |
| 06 | Novi račun | 8.751517 | 0.845998 | 670,925 | 22.264% |
| 07 | Stavke računa | 8.861079 | 0.867094 | 809,512 | 26.863% |
| 08 | Pregled računa | 7.792666 | 0.866595 | 866,899 | 28.767% |
| 09 | Ponude | 7.795244 | 0.857021 | 758,563 | 25.172% |
| 10 | Nova ponuda | 8.754677 | 0.850294 | 663,183 | 22.007% |
| 11 | Pregled ponude | 9.613645 | 0.850759 | 923,731 | 30.653% |
| 12 | Klijenti | 7.454286 | 0.867454 | 863,599 | 28.657% |
| 13 | Detalji klijenta | 9.140982 | 0.874959 | 724,548 | 24.043% |
| 14 | Artikli i usluge | 7.436303 | 0.867220 | 754,990 | 25.053% |
| 15 | Izvještaji | 8.354116 | 0.874101 | 790,658 | 26.237% |
| 16 | Naplata | 7.783027 | 0.869614 | 984,827 | 32.680% |
| 17 | eRačun integracija | 7.543926 | 0.871660 | 626,335 | 20.784% |
| 18 | Postavke | 8.773570 | 0.840806 | 618,663 | 20.530% |
| 19 | Profil tvrtke | 8.224092 | 0.857244 | 629,340 | 20.884% |
| 20 | Tamna tema | 15.160833 | 0.804387 | 1,331,798 | 44.194% |

## Priority and acceptance

1. 20 Dark theme (MAE 15.160833): compare colors, system bars, dynamic content and layout.
2. 04 Home (10.503165): KPI and chart geometry, quick actions and bottom navigation.
3. 11 Quote preview (9.613645), 13 Client details (9.140982), 05 Invoices (9.112940), 18 Settings (8.773570).
4. Continue the remaining fourteen screens using the **same reference pixels and same emulator viewport**.
5. Capture ten responsive profiles independently; a passing layout test does not imply 1:1 parity.
6. Exact 1:1 can only be asserted with 0 unequal pixels on **all twenty** captures, SSIM=1 and RGB MAE=0, plus separate functional verification.

Local reproduction (using references supplied out-of-repository):

```bash
python3 quality/pixel_audit.py /private/Fakturiva/ekrani docs/screenshots \
  --output build/pixel-audit.csv \
  --summary build/pixel-audit-summary.json \
  --diff-dir build/pixel-diffs --expected 20
```

**Limits:** business data in the captures can differ from the design references. Do not overwrite customer data, add fabricated totals to the production application or substitute screenshots for real UI. Original reference PNGs remain private. A green emulator or PHP CI run is not an Android–web–Windows production E2E certification.
