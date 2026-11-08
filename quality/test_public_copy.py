"""Guard production UI strings against obsolete integration and developer copy."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/brendigo/fakturiva/MainActivity.java"
PDF = ROOT / "app/src/main/java/com/brendigo/fakturiva/PdfMaker.java"
STORE = ROOT / "app/src/main/java/com/brendigo/fakturiva/Store.java"
HARNESS = ROOT / "app/src/debug/java/com/brendigo/fakturiva/ScreenshotHarness.java"
CHECKER = ROOT / "ci/verify_responsive_capture.py"


def java_literals(content: str) -> str:
    # Java string literals are distinct from comments and internal Java names.
    return "\n".join(re.findall(r'"(?:\\.|[^"\\])*"', content))


class ProductionCopyTests(unittest.TestCase):
    def test_invoice_quote_copy_has_no_removed_integration_or_developer_messages(self):
        strings = java_literals(MAIN.read_text(encoding="utf-8"))
        for obsolete in (
            "fiskaliz", "eRačun", "eRačuni", "eracun",
            "Vaši podaci ostaju na uređaju", "ostaju lokalno na uređaju",
            "Nativna Android aplikacija", "stvarnog PDF-a",
        ):
            self.assertNotIn(obsolete.casefold(), strings.casefold(), obsolete)

    def test_pdf_does_not_brand_customer_document_as_test_or_technical_output(self):
        strings = java_literals(PDF.read_text(encoding="utf-8"))
        self.assertNotIn("fiskaliz", strings.casefold())
        self.assertNotIn("eRačun".casefold(), strings.casefold())
        self.assertNotIn("lokalno izrađen PDF", strings)

    def test_help_replaces_integration_in_screenshot_capture(self):
        source = MAIN.read_text(encoding="utf-8")
        harness = HARNESS.read_text(encoding="utf-8")
        checker = CHECKER.read_text(encoding="utf-8")
        self.assertIn('case "guide":guide();break;', source)
        self.assertIn('case "17":call(a,"guide");break;', harness)
        self.assertIn('"Naplata", "Pomoć", "Postavke"', checker)

    def test_invoice_confirmation_does_not_require_croatian_tax_id(self):
        source = STORE.read_text(encoding="utf-8")
        start = source.index("public String confirm(long id)")
        end = source.index("public void pay(long id", start)
        self.assertNotIn("Oib.valid", source[start:end])
        self.assertIn('setting("address")', source[start:end])
        self.assertIn('setting("name")', source[start:end])


if __name__ == "__main__":
    unittest.main()
