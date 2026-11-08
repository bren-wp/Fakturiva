# Fakturiva — printer and receipt support

## What works in this development branch

Fakturiva can print its existing **invoices and quotes** through two distinct paths. A printed invoice/quote does **not** change the document's paid/unpaid state and does not perform fiscalization or card payment acceptance.

| Transport | Paper / format | Driver / protocol | Status |
| --- | --- | --- | --- |
| Android Print Framework | A4 (595×842 pt), A5 (420×595 pt), US Letter (612×792 pt), US Legal (612×1008 pt) | Android system print service, user-selected | Implemented in code; real printer validation required |
| Bluetooth Classic (paired) | 58 mm (384-dot raster) and 80 mm (576-dot raster) | ESC/POS over standard Bluetooth SPP / RFCOMM UUID 00001101-0000-1000-8000-00805F9B34FB | Implemented in code; real printer validation required |
| Bluetooth LE only devices | None | Proprietary BLE GATT services vary | Not implemented |
| Proprietary card-payment POS terminals | None | Vendor/acquirer approval and SDK are required | **Not implemented; does not process cards** |
| Direct IP socket / USB ESC/POS | None | Depends on model/driver | Use Android print service if available; direct protocol not implemented |

The Android Print Framework selects the real destination printer. Compatibility with a Wi-Fi, USB or network printer depends on the installed print service. No generic API can promise universal hardware compatibility.

## How to print

1. Open an invoice or quote, then choose **Print invoice or quote** or **Print / POS printer** from document options.
2. For page printers select A4, A5, US Letter or Legal. Android displays the normal printer-selection dialog. A matching-size **vector PDF** is prepared locally.
3. For a compatible thermal printer, pair the printer in Android's Bluetooth settings first. Choose **58 mm Bluetooth ESC/POS** or **80 mm Bluetooth ESC/POS**, then select its entry from the paired-device list.
4. The last chosen paper format is saved as a preference. No printer Bluetooth address is stored in the Fakturiva database.
5. Check that the paper is loaded and the printer supports ESC/POS raster `GS v 0` commands. Some models use proprietary codecs/SDKs and will not work over generic SPP.

Settings → Printers and paper lets users choose a default format and open Android Bluetooth settings.

## Thermal output

Receipts contain the saved document number or a clearly marked **DRAFT — NOT ISSUED** status, seller and customer identity, international company/tax details, descriptions, quantities, unit prices, tax rate, totals and currency, payment status, optional bank details and business notes. Missing optional fields are omitted, not fabricated.

Text is rasterized as black/white pixels so user-provided UTF-8 characters and diacritics do **not** pass raw ESC/POS control bytes to the device. The renderer splits long fields to keep allocations bounded and refuses excessively long documents rather than silently dropping items. For very large invoices, use PDF printing.

The code initializes ESC/POS and feeds paper. It deliberately does **not** kick a cash drawer, automatically cut, trigger card payments, or retry after a failed transfer. On transport failure it warns that a *partial print* may already have occurred. A successful SPP transfer only means the data was sent; it does not prove the physical output completed.

## Permissions and privacy

- Android 12+ asks for `BLUETOOTH_CONNECT` only after the user explicitly selects Bluetooth printing.
- Android 11 and older use legacy `BLUETOOTH` permission with maxSdkVersion 30.
- No scanning, location, BLE discovery, hidden pairing, background listening, or Bluetooth advertising.
- Selected printer receives the user's invoice details **only after selection**. Use trusted printers and be aware that Bluetooth transport security depends on the printer and Android pairing.
- Android print services are third-party/system components; their data handling depends on the installed service. Fakturiva does not upload the document to a Fakturiva server.

## QA and limits

The automated checks cover raster command headers for 58/80 mm, Unicode and non-Latin characters, oversized receipts, and true PDF media dimensions. Emulator checks **do not replace real-hardware tests**. Before releasing, test representative ESC/POS models from multiple vendors and at least one physical printer for each media size. Confirm device pairing/permissions on Android API 24, 30, 31/32, 33–36 and on small/large screens.

**Important:** No guarantee of compatibility with every POS device or legal acceptance for all receipt/invoice types. Card payment terminals and fiscalization are deliberately outside the current Fakturiva product scope.
