# Fakturiva — international invoices, translations and currencies

Scope: Fakturiva is an invoice/quote organizer, not a fiscalization or structured eInvoice service. This plan makes the product usable for a variety of legal business forms without claiming universal tax/legal compliance.

## Languages (31 targets)

English (en, **default**), Croatian (hr), German (de), French (fr), Spanish (es), Italian (it), Portuguese (pt), Dutch (nl), Polish (pl), Czech (cs), Slovak (sk), Slovenian (sl), Hungarian (hu), Romanian (ro), Bulgarian (bg), Serbian (sr), Bosnian (bs), Ukrainian (uk), Russian (ru), Turkish (tr), Greek (el), Swedish (sv), Danish (da), Norwegian Bokmål (nb), Finnish (fi), Estonian (et), Latvian (lv), Lithuanian (lt), Arabic (ar), Japanese (ja), Chinese (zh).

**Current implementation:** persistent English-first picker, Croatian alternative, and an English glossary of ~185 common user-facing source phrases; the additional language IDs are targets, **not completed translations or selectable languages**. The pre-existing app was Croatian and still has untranslated strings, including dynamic concatenations, notifications, fields, dialogs, error paths and PDF. It would be incorrect to claim either complete 31-language support or 100% English translation. The app should not be publicly released as 31-language-ready until the remaining work is done.

### Translation acceptance gate — mandatory before a language is enabled

1. Inventory every hardcoded UI string, dialog/action button, input hint, accessibility content description, toast, validation error and printed PDF heading.
2. Move each string to stable translatable resource keys. Add plural rules, gender-neutral constructions and locale-dependent separators.
3. Provide a professionally reviewed target-language translation for **every key**, with placeholders preserved exactly; no blank strings and no silent Croatian/English fallbacks.
4. Validate generated receipts, quotes, multipage PDFs, pricing, legal-form labels, date formatting and currencies in each target language.
5. Test 280dp mobile, 1.5× font size, portrait/landscape, right-to-left Arabic, and accessibility screen readers.
6. For Android 13+ use the platform per-app locale API and resource locale config after translations are complete; for older Android versions keep the in-app selector synchronized.
7. Set the Play Store language listing and screenshots only after review. A language may be exposed only after all the above checks pass.

Relevant platform guidance: https://developer.android.com/guide/topics/resources/app-languages

## Currency rules and current limits

- EUR is the default selected ISO currency for **new** documents.
- A currency code is recorded on each invoice/quote in SQLite schema 6; legacy invoices and quotes default to EUR during non-destructive upgrade.
- Amounts use **integer hundredths** of the currency (the existing `*_cents` database fields). Supported currency options are restricted to ISO currencies whose default fractional digits equal exactly **2**; the available list is generated from Java Currency, not from an unvalidated editable code.
- Zero-decimal currencies (e.g., JPY, KRW) and three-decimal currencies (e.g., BHD, KWD) are deliberately excluded until monetary migration, validation, custom tax rates and PDF/report logic support their fractions end-to-end. Do not claim all ISO currencies are supported.
- Never combine EUR and USD balances without an explicit exchange conversion. Dashboard and report financial totals are scoped to selected currency; payment CSV includes the original currency code. Exchange rates and cross-currency accounting are **not supported**.
- A converted quote preserves its currency in the resulting invoice draft. Changing the selected default does not change issued documents.
- Numeric grouping and decimal display follow selected app locale where implemented. Data remain exact integers without binary floating-point calculations.

## Business profile and invoices

All these fields are optional except business name and address as required by the current confirmation workflow: legal name, legal form (e.g., sole trader/obrt, d.o.o., j.d.o.o., Ltd, LLC, Inc, GmbH), trading name, company registration number, registering authority, international tax identifier, VAT ID, tax registration details, contact person, address, address line 2, state/province/region, postal code, city, country, phone, email, website, business unit, operator reference, bank name/address, IBAN, account number, routing/ABA code, sort/branch code, BIC/SWIFT, payment method, payment reference, PO/invoice reference, payment terms and invoice footer note.

All fields are entered under the issuer profile and snapshotted onto new invoices so editing the profile cannot retroactively rewrite issued documents. PDF export includes the relevant nonempty fields.

## Data and verification

- Schema version 6 migrations must preserve previous invoices, references, dates, clients, item totals, invoice sequence, payment history and backups.
- Test old schema 5 upgrade, schema 3–5 backup import, missing tax IDs, multiple currencies, quote-to-invoice conversion, CSV currency labels and a full file roundtrip.
- Keep actual prices and currency selection visible in the PDF. Never mislabel one currency as another.
- Do not merge until Android build/lint, instrumentation and nine responsive emulator profiles pass on the same SHA.
- The old visual reference for screen 17 (retired eInvoice integrations) is no longer a valid 1:1 comparison target; screen 17 is Help.
