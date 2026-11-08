# Fakturiva — printer and receipt support

## Implemented printer paths (hardware confirmation still required)

Fakturiva can print its existing **invoices and quotes** through two distinct paths. A printed invoice/quote does **not** change the document's paid/unpaid state and does not perform fiscalization or card payment acceptance.

| Transport | Paper / format | Driver / protocol | Status |
| --- | --- | --- | --- |
| Android Print Framework | A3, A4, A5, A6, US Letter, US Legal | Android system print service, user-selected (Wi-Fi/USB/network drivers as available) | Implemented in code; real printer validation required |
| Bluetooth Classic (paired) | 58 mm / 384 dots; 80 mm / 512 or 576 dots | ESC/POS over standard Bluetooth SPP / RFCOMM UUID 00001101-0000-1000-8000-00805F9B34FB | Implemented in code; real printer validation required |
| Local TCP network printer | 58 mm / 384 dots; 80 mm / 512 or 576 dots | ESC/POS raw TCP (typically port 9100) with manually supplied RFC1918 IPv4 | Implemented in code; real printer validation required |
| Bluetooth LE only devices | None | Proprietary BLE GATT services vary | Not implemented |
| Proprietary card-payment POS terminals | None | Vendor/acquirer approval and SDK are required | **Not implemented; does not process cards** |
| Direct USB ESC/POS | None | Vendor USB endpoints and drivers vary | Use Android Print Framework/installed service; generic raw-USB driver not implemented |

The Android Print Framework selects the real destination printer. Compatibility with a Wi-Fi, USB or network printer depends on the installed print service. No generic API can promise universal hardware compatibility.

## How to print

1. Open an invoice or quote, then choose **Print invoice or quote** or **Print / POS printer** from document options.
2. For page printers select A4, A5, US Letter or Legal. Android displays the normal printer-selection dialog. A matching-size **vector PDF** is prepared locally.
3. For a compatible **Bluetooth Classic** thermal printer, pair the printer in Android's Bluetooth settings first, select 58 mm / 384 dots or 80 mm / 512 or 576 dots, and pick the paired device.
4. For a **LAN ESC/POS** printer, select the corresponding LAN option and enter its private IPv4 (for example `192.168.1.50`) and raw TCP port (usually `9100`). Fakturiva does not scan the network or resolve arbitrary hostnames; public IP, loopback, multicast and broadcast are rejected. An unsafeguarded public-network print destination is not supported.
5. The last chosen paper format and optional LAN address/port are saved in local app preferences. No Bluetooth printer identifier is saved in the Fakturiva database.
6. Check paper and support for ESC/POS raster `GS v 0`. Some manufacturers use proprietary protocols/SDKs and will not work with generic SPP/TCP. Print-service support for USB, Wi-Fi Direct or network printers likewise depends on the installed Android driver.

Settings → Printers and paper lets users choose a default format and open Android Bluetooth settings. Settings → LAN receipt printer allows editing the stored local IP and port. All actual print operations require an explicit user action.

## Thermal output

Receipts contain the saved document number or a clearly marked **DRAFT — NOT ISSUED** status, seller and customer identity, international company/tax details, descriptions, quantities, unit prices, tax rate, totals and currency, payment status, optional bank details and business notes. Missing optional fields are omitted, not fabricated.

Text is rasterized as black/white pixels so user-provided UTF-8 characters and diacritics do **not** pass raw ESC/POS control bytes to the device. The renderer splits long fields to keep allocations bounded and refuses excessively long documents rather than silently dropping items. For very large invoices, use PDF printing.

All PDF invoice pages contain a small **Powered by Brendigo** label in the bottom margin. Thermal receipts contain the same phrase on the **final line**, centered and set in a smaller font. This applies to the app's normal PDF export, share, Android print dialog and ESC/POS output. There is no undocumented switch or printer-command escape to remove the attribution. To request removal, open **Settings → About and support** and contact **info@plati.com.hr** via the built-in email action.

The code initializes ESC/POS and feeds paper. It deliberately does **not** kick a cash drawer, automatically cut, trigger card payments, or retry after a failed transfer. On transport failure it warns that a *partial print* may already have occurred. A successful SPP transfer only means the data was sent; it does not prove the physical output completed.

## Permissions and privacy

- Android 12+ asks for `BLUETOOTH_CONNECT` only after the user explicitly selects Bluetooth printing.
- Android 11 and older use legacy `BLUETOOTH` permission with maxSdkVersion 30.
- No scanning, location, BLE discovery, hidden pairing, background listening, or Bluetooth advertising.
- The `INTERNET` permission is needed for the **explicit local TCP printer** feature only; selected invoice data travels to the user-entered LAN address. Raw port-9100 printing is not end-to-end encrypted, so use trusted local networks and never expose the printer port to the Internet. No automatic network printer discovery, connection or background document transfer is performed.
- Selected printer receives the user's invoice details **only after selection**. Use trusted printers and be aware that Bluetooth transport security depends on the printer and Android pairing.
- Android print services are third-party/system components; their data handling depends on the installed service. Fakturiva does not upload the document to a Fakturiva server.

## QA and limits

The automated checks cover raster command headers for 384/512/576-dot widths, Unicode and non-Latin characters, oversized receipts, footer content, private IPv4 safety validation, and true PDF media dimensions including A3 and A6. Emulator checks **do not replace real-hardware tests**. Before releasing, test representative ESC/POS models from multiple vendors and at least one physical printer for each media size. Confirm device pairing/permissions on Android API 24, 30, 31/32, 33–36 and on small/large screens.

**Important:** No guarantee of compatibility with every POS device or legal acceptance for all receipt/invoice types. Card payment terminals and fiscalization are deliberately outside the current Fakturiva product scope.
