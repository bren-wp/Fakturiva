# Fakturiva — POS printer and payment-provider integration matrix

**Scope:** Android invoice/quote printing. The terms "POS printer" and
"payment terminal" refer to different kinds of hardware. Printed document delivery
does not authorize a card transaction, issue a fiscal receipt, or send an eInvoice.

## Implemented transports

| Path | Current implementation | Limitations |
| --- | --- | --- |
| Android Print Framework | A3/A4/A5/A6/US Letter/Legal, through the user's installed print services | Actual model support depends on the print service |
| Bluetooth Classic SPP ESC/POS | User-paired printer, 58 mm (384 dots) and 80 mm (512/576 dots) | No BLE-only devices and no auto-pairing |
| LAN raw TCP ESC/POS | User-supplied RFC1918 IPv4 address, TCP 1024–65535 | Trusted LAN only, no automatic discovery or encryption of port-9100 traffic |
| **USB host ESC/POS** | **New: user-selected USB Printer Class interface with bulk OUT endpoint and Android permission; 384/512/576 dots** | Does not guess vendor-specific endpoints; requires Android USB host/OTG, printer-class device and raster ESC/POS support |

All ESC/POS paths use the same Unicode-safe thermal raster renderer, do not
automatically cut paper/open a drawer, and never retry after partial failure.
Success means bytes were transmitted, **not** that the physical output completed.

## Manufacturer compatibility — not a certification claim

The table describes **integration routes**, not universal working-device lists.
Model, firmware, transport, paper width, language and character output must be
confirmed on physical hardware. For the original SDK licenses and current versions,
consult the manufacturer; no proprietary vendor AAR/JAR is bundled in this branch.

| Family | Potential integration path | SDK/reference | Status in Fakturiva |
| --- | --- | --- | --- |
| Epson TM / TM-m / TM-P | Android Print Service; ESC/POS on supported interfaces; native ePOS SDK for model-specific features | https://www.epson.jp/dl_soft/readme/47013.htm | Generic path only; ePOS SDK not bundled |
| Star Micronics TSP / mC-Print / SM | Android print service where available; **StarXpand / StarIO10** SDK for native models | https://starmicronics.com/support/developers/android-sdks/ | Vendor SDK integration pending |
| BIXOLON SRP / SPP | ESC/POS only where model confirms compatible raster commands; BIXOLON Android SDK/B-gate | https://docs.bixolon.com/ | Generic path only |
| Citizen CT series | Print service/ESC/POS where supported; official POS Print SDK for Android | https://www.citizen-systems.co.jp/en/printer/download/sdk | Generic path only |
| SUNMI V/T/P with built-in printer | SUNMI printer service/library on supported terminal, separate from USB Printer Class | https://github.com/shangmisunmi/SunmiPrinterDemo | SUNMI-specific service not yet integrated |
| Xprinter XP series (China) | ESC/POS over Bluetooth/LAN/USB Printer Class **where supported**; Android SDK 3.5.8 | https://www.xprintertech.com/sdk.html | Generic path only; model test required |
| Rongta / HOIN / MUNBYN and other OEMs | ESC/POS on confirmed transport, or Android vendor print service | Verify exact manufacturer's model protocol and driver | No blanket compatibility promise |
| Canon / HP / Brother / Ricoh / Kyocera / Konica Minolta etc. | Android Print Framework with compatible installed print service | Respective manufacturer's Android printing solution | System print only; actual devices not tested |

**Important:** USB device detection is intentionally limited to USB Printer Class
interfaces with a bulk OUT endpoint. The presence of a supported class is not
proof of ESC/POS compliance; PDF-only, label-printer language (ZPL/TSPL/CPCL) or
proprietary models must not receive arbitrary ESC/POS commands.

## Croatian services, card-present POS and SoftPOS

These are **payment processing** options, not equivalent to a printer driver:

- **Monri** (Croatia): SinglePOS / in-store services, SoftPOS and a separate
  WebPay mobile SDK. A WebPay card-entry SDK is not the same thing as a
  card-present terminal SDK. See https://monri.hr/proizvodi/instore-placanja/,
  https://monri.hr/proizvodi/softpos/ and https://docs.monri.com/.
- **PAX** Android smart terminals: Android hardware SDK / Neptune access may
  require a deployment agreement; the acquiring/payment application's
  *separate* certified POS/ECR protocol is required for processing payments.
  See https://docs.hips.com/docs/pax-a920 and
  https://developer.visaacceptance.com/docs/vas/en-us/pax-all-in-one/integration/all/na/pax-all-in-one.html.
- Other Croatian acquirers and terminal distributors (e.g. **Payten**) must
  supply the applicable merchant-specific ECR/SDK documentation, sandbox and
  legal deployment approval before an adapter can be implemented.

**No payment SDK or card authorization is implemented here.** The app must not
claim that a document is paid after sending it to a printer or simply launching
a third-party POS app. A real payment adapter needs an approved merchant account,
terminal registration, sandbox/test terminal, authenticated callbacks and
status reconciliation; idempotent transactions and reversals/refunds; and no
PAN, PIN, CVV or raw EMV track data stored, logged or transmitted by Fakturiva.
Do not simulate success, and do not bypass the acquirer's certification.

**Croatian fiscalization and structured eRačun are separate regulated workflows.**
A PDF or thermal print is not sufficient evidence of compliance.

## Physical acceptance requirements

| Check | Required |
| --- | --- |
| USB permission | Android prompts for selected USB device; denial/disconnect must not crash |
| Device selection | Only printer-class bulk OUT interfaces listed; no mass-storage or arbitrary device |
| Paper widths | Physically verify 58/80 mm, 384/512/576-dot raster handling |
| Diacritics | Croatian č/ć/đ/š/ž plus mixed-script long item names print without control injection |
| Overflow | Long name/address/item values and multi-page A4 PDFs are not truncated |
| Failure | Cable unplug, out-of-paper, power loss, and timeout warn about possible partial print |
| Privacy | No background scans; only user-picked device receives document bytes |
| Compatibility | Record vendor, model, VID:PID, firmware, interface, paper dots, Android OS, test result |
| Payment | No fake transaction, fiscalization, card authorization or receipt-paid state |

### Visual parity priority from earlier measured reference runs

The highest documented gaps in the prior *pre-fix* 20-screen sample are:
**20 Dark Home (MAE 20.4392), 04 Home (16.1449), 11 Quote Preview
(12.9842), 08 Invoice Preview (12.8471), 15 Reports (12.1627)**.
Numbers are RGB mean absolute error /255 and are **not fresh measurements
for this branch**. Reference documents:
[recheck](V0.18_2_PARITY_RECHECK.md) and
[visual process](PIXEL_AUDIT.md).

New print choices intentionally extend functional print dialogues without
altering the 20 reference image files. Exact 1:1 / 0% visual deviation cannot
be certified until **new same-commit emulator captures**, exhaustive pixel diffs,
and responsive/real-device checks pass.

## Safe SDK next steps

1. Obtain **approved** vendor SDK, license and current model list; verify dependencies, Android API 24+ support, privacy, and release redistribution rights.
2. Implement each SDK behind a dedicated printer/terminal adapter, isolated from the invoice model and gated by runtime capability detection.
3. Add vendor sample/device checks, automated instrumentation, and hardware regression results; keep noncompatible vendor paths hidden or clearly unavailable.
4. Enable real card-present payment only with a certified ECR/SoftPOS integration supplied by the chosen provider, merchant credentials stored using the proper security model, and production approval.
