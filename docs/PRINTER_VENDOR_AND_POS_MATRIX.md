# Fakturiva — printer/vendor and card-terminal integration matrix

Status: **development branch; hardware compatibility is NOT certified** (8 November 2026).

## Printing vs collecting a card payment

The invoice/quote printer path is independent of payment settlement and
fiscalization. A transport success means bytes reached a connection, **not**
that paper was printed, a card was charged, a fiscal receipt issued, or an
invoice marked paid. Never map "Print" to "Paid".

### Printing adapters currently in the app

| Route | Device class | Validation and coverage | Status |
| --- | --- | --- | --- |
| Android Print Framework | A3/A4/A5/A6/Letter/Legal via installed print service | Wi-Fi, USB, network, AirPrint-like manufacturer services only where Android driver supports them | Implemented, hardware QA pending |
| Bluetooth Classic SPP | Raster ESC/POS 384/512/576 dots | Previously paired devices, explicit nearby-devices permission on Android 12+ | Implemented, hardware QA pending |
| Local TCP socket | Raster ESC/POS, 58/80 mm | Manually chosen private IPv4 + port, no device scan, no cleartext Internet target | Implemented, hardware QA pending |
| **USB Host / Printer Class** | Bulk-OUT interface class 7, ESC/POS raster, 384/512/576 dots | **New:** explicit Android device-permission request, no device-name whitelist, bounded 8 KiB writes, fail on zero progress, no automatic replay on uncertain partial output | Implemented in this branch, hardware QA pending |
| Proprietary USB-only printer | Vendor-specific interface | Device-specific command set must be established and permission/license reviewed; **not sent arbitrary ESC/POS bytes** | Not implemented |
| Bluetooth LE printer | Vendor GATT implementation | No universal BLE print profile | Not implemented |
| Built-in SUNMI, PAX or other Android terminal printer | Vendor AIDL/service | Requires vendor contract/API and hardware matrix, may differ per model | Not implemented |

**International glyphs:** the existing thermal renderer converts text into raster
pixels using Android's font fallback. UTF-8 text (including Croatian diacritics
and potentially Chinese/Japanese glyphs) never becomes raw ESC/POS control
sequences. Glyph availability depends on installed typefaces and must be
verified on each real device. A successful test on one printer does not certify
all printer brands.

### Official manufacturer SDK candidates (not bundled or enabled)

1. **Epson TM**: Epson ePOS SDK for Android. The vendor currently publishes
   updated SDK packages and a printer-specific support matrix. Integrate in
   an optional vendor module only after checking licensing, model compatibility,
   native libraries and app size.
   https://download-center.epson.com/softwares/?device_id=TM-T20IV-SP&language=en&os=ARD&region=US
2. **Star Micronics**: StarXpand SDK / StarIO10 Android. Official repository
   currently lists v1.14.0; the vendor describes diagnostic collection by
   certain APIs. Review privacy and minimum Android version before bundling.
   https://github.com/star-micronics/StarXpand-SDK-Android
3. **SUNMI**: manufacturer printing services and AIDL on supported terminal
   models, distinct from the acquirer's card-payment interface.
   https://cdn.sunmi.com/public/generalfile/mgt-document/841c6680d673447ba9c5d9b1e1131d01.pdf

A general-purpose application cannot truthfully promise printer compatibility
for every Croatian, foreign or Chinese product solely from the manufacturer's
name. Use protocol + VID/PID + interface + firmware + connection type in the
hardware compatibility registry after actual testing.

## Croatian-market payment integration candidates

| Provider | Official publicly described integration | What remains necessary |
| --- | --- | --- |
| Monri | SinglePOS supports ECR partners; SoftPOS can receive payment amounts from other apps | Partner onboarding, documentation for exact acquirer app, approved test setup and transaction callbacks. https://monri.hr/proizvodi/instore-placanja/ https://monri.hr/proizvodi/softpos/ |
| Teya | Unified ePOS SDK / PosLink, including Android, cloud-connected terminals and OAuth credentials | Merchant/partner credentials, sandbox tests, exact terminal support, reconciliation/timeout behavior. https://docs.teya.com/epos-sdk/overview |
| Nexi Croatia | POS / SoftPOS offerings and developer terminal protocols | Confirm **Croatian** merchant contract and terminal firmware: public Nexi ECR17 docs may describe a different market, so they are not a Croatian certification. https://www.nexi.hr/hr/banke-i-financijske-institucije/rjesenja-za-prihvat-transakcija/pos-rjesenja/ |
| Worldline Croatia | Terminal API Cloud for Android SmartPOS; separate JSON/Nexo Retailer API documentation | Contract, secure credentials, allowed terminal fleet, webhook confirmation, idempotency and PCI requirements. https://worldline.com/hr-hr/home/main-navigation/solutions/merchants/products/integration/terminal-api-cloud |

**None of these acquirer payment SDKs has been integrated, authorized, or
certified in this branch.** A payment integration must be developed as a
separate capability behind a reviewable provider adapter, never reuse the
USB ESC/POS transport to command a payment terminal.

### Required payment adapter contract before activation

- Start purchase with immutable invoice identifier, exact ISO currency and
  integer-minor-unit amount; reject unsupported currency/exponents.
- Use unique, durable, idempotent payment request ID; prevent duplicate charging
  on retries or app process death.
- States: created -> pending -> approved/declined/cancelled/unknown; **unknown
  is not declined**. Reconcile through provider before retrying.
- Confirm payment through signed/authenticated provider result or a verified
  server-to-server status query. Do not set an invoice paid on app hand-off,
  receipt printing or unverified Intent extras.
- Track refund/reversal separately from invoice state and prevent
  overpayment/duplicate settlement.
- Keep card PAN, PIN and CVV out of Fakturiva logs, database, backups and UI.
  Sensitive provider secrets must not be committed into Android assets.
- Respect PCI DSS, merchant agreement and local fiscalization/eInvoice rules;
  production processing requires approval and end-to-end hardware testing.

## Hardware acceptance matrix

For every claimed model, record vendor, model, firmware, VID:PID (USB),
interface/protocol, OS API level, printable dots, paper width, Unicode test,
long invoice (including 160+ characters per field), connection loss, device
detachment, permission denial, paper out, and duplicate/retry risk.

USB acceptance must include: (1) USB host missing, (2) no device, (3) printer
class bulk OUT present, (4) request denied, (5) permission granted,
(6) device detached during transfer, (7) short or failed bulk transfer,
(8) full printed 58/80 mm invoice with Croatian and Chinese text, and
(9) no transmission to a payment terminal or an unrelated USB device.

Automated byte-stream checks are in `quality/UsbPrinterChunkSmoke.java`.
The USB feature does not warrant a release until APK build/lint and physical
printer tests have passed.
