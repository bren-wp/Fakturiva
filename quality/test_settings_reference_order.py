"""Source-level safety regression for preserving reference-first Settings actions.

This guards action order and exact callback connectivity; it does not claim
pixel-perfect identity or replace real screenshots/UI instrumentation.
"""
from pathlib import Path
import re
import unittest

JAVA=(Path(__file__).resolve().parents[1]/
      "app/src/main/java/com/brendigo/fakturiva/MainActivity.java").read_text(encoding="utf-8")

class SettingsReferenceOrderTests(unittest.TestCase):
    def test_first_nine_settings_match_owner_reference(self):
        beginning=JAVA.index('private void settings()')
        end=JAVA.index('private void languagePicker()',beginning)
        settings=JAVA[beginning:end]
        primary=settings.split('LinearLayout options=card();',1)[1].split(
            'LinearLayout advanced=card();',1)[0]
        rows=re.findall(r'settingRow\\(options,"[^"]+","([^"]+)"',primary)
        self.assertEqual(rows,[
            "Tvrtka i poslovni podaci",
            "Numeracija dokumenata",
            "PDV i porezne postavke",
            "Načini plaćanja",
            "Izgled dokumenta",
            "eRačuni i fiskalizacija",
            "Sigurnost",
            "Izvoz i sigurnosne kopije",
            "Izgled aplikacije",
        ])

    def test_all_existing_advanced_callbacks_remain_reachable(self):
        beginning=JAVA.index('private void settings()')
        end=JAVA.index('private void languagePicker()',beginning)
        settings=JAVA[beginning:end]
        for handler in [
            'this::languagePicker','this::currencyPicker','this::printerPreferences',
            'this::networkPrinterSettings','this::usbPrinterInfo',
            'this::company','this::numberingInfo','this::invoiceAppearance',
            'this::eracunScreen','this::guide','this::selfHostedSettings',
            'this::privacyInfoScreen','this::localAccountSettings',
            'this::backupScreen','this::supportScreen','this::aboutScreen',
            'securityScreen(false,true)'
        ]:
            with self.subTest(handler=handler):
                self.assertIn(handler,settings)
        self.assertIn('prefs.edit().putBoolean("dark",dark).apply()',settings)
        self.assertIn('settingRow(tools,"▣","Artikli i usluge"',settings)

if __name__=="__main__":
    unittest.main()
