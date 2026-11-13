"""Regression checks for measured Android reference-screen corrections.

These are source-level guardrails, not proof of pixel-perfect visual parity.
Real Android screenshot/UISmoke jobs and private original PNG audit remain mandatory.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
MAIN=(ROOT/"app/src/main/java/com/brendigo/fakturiva/MainActivity.java").read_text(
    encoding="utf-8")
HARNESS=(ROOT/"app/src/debug/java/com/brendigo/fakturiva/ScreenshotHarness.java").read_text(
    encoding="utf-8")


def method(source: str, start: str, next_start: str) -> str:
    return source.split(start,1)[1].split(next_start,1)[0]


class HighDeviationScreenGuards(unittest.TestCase):
    def test_payments_monthly_hero_uses_same_real_sqlite_receipts_as_home(self):
        home=method(MAIN,"    private void home(){","    private void showQuickActions(){")
        payments=method(MAIN,"    private void payments(){","    private void paymentDivider(")
        self.assertIn("db.monthlyReceipts(selectedCurrency(),displayClock)",home)
        self.assertIn("long monthReceived=db.monthlyReceipts(",payments)
        self.assertIn("selectedCurrency(),referenceAwareClock())[11]",payments)
        self.assertIn("money(monthReceived)",payments)
        self.assertNotIn("money(db.receivedCents(selectedCurrency()))",payments)
        self.assertIn("19",HARNESS)  # 20-screen genuine harness stays present

    def test_company_first_card_contains_core_fields_before_country_options(self):
        company=method(MAIN,"    private void company(){","    /** Guidance to create an invoice")
        self.assertIn("int[][] sections={{0,1,3,7,8,19",company)
        self.assertIn('place(form,label("Podaci o tvrtki"',company)
        self.assertIn('"Naziv tvrtke","OIB / porezni broj"',company)
        self.assertIn('"Adresa","Poštanski broj"',company)
        self.assertIn('"E-adresa","Telefon"',company)
        self.assertLess(company.index("place(body,form);gap(body,14);"),
                        company.index("place(body,legalChoice);gap(body,14);"))
        self.assertIn("countrySelector.setOnItemSelectedListener(",company)
        self.assertIn("BusinessProfileRules.validate(",company)

    def test_company_reference_uses_actual_store_profile_and_no_fake_overlay(self):
        screen19=method(HARNESS,'            case "19":','            case "20":')
        for key in ("name","country","legal_form","company_number","address",
                    "city","email","phone"):
            self.assertIn('db.setting("'+key+'"',screen19)
        self.assertIn('call(a,"company");',screen19)
        self.assertNotIn("Bitmap",screen19)

    def test_invoice_and_quote_previews_preserve_genuine_document_semantics(self):
        preview=method(MAIN,"    private void showDocument(long id){",
                       "    /** Paper-like native preview")
        paper=method(MAIN,"    private LinearLayout documentPaper(Store.Document d){",
                     "    private void paperRule(")
        self.assertIn("document=db.document(id)",preview)
        self.assertIn("db.confirm(id)",preview)
        self.assertIn("d.number==null",preview)
        self.assertIn('invoice?"Izdaj račun":"Potvrdi ponudu"',preview)
        self.assertIn("d.items",paper)
        self.assertIn('"Dokument za pregled · Nije fiskaliziran."',paper)
        self.assertIn('if(Store.INVOICE.equals(d.type))',paper)
        self.assertIn('else gap(paper,12)',paper)
        self.assertNotIn('"Ponuda nije račun niti dokaz o plaćanju."',paper)


if __name__ == "__main__":
    unittest.main()
