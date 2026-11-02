"""Pure-Python regressions for the responsive Android screenshot validator."""
import importlib.util
import struct
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "ci" / "verify_responsive_capture.py"
spec = importlib.util.spec_from_file_location("responsive_qa", SCRIPT)
qa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa)


class ResponsiveCheckerTest(unittest.TestCase):
    def test_png_header_reports_real_physical_dimensions(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "frame.png"
            path.write_bytes(
                b"\x89PNG\r\n\x1a\n" +
                struct.pack(">I", 13) + b"IHDR" +
                struct.pack(">II", 1179, 2556) + b"\0" * 13
            )
            self.assertEqual(qa.png_dimensions(path), (1179, 2556))

    def test_missing_expected_app_header_is_a_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "layout.xml"
            path.write_text(
                '<hierarchy>'
                '<node package="com.brendigo.fakturiva" '
                'text="Fakturiva" bounds="[0,0][560,1120]" clickable="false"/>'
                '<node package="com.brendigo.fakturiva" '
                'text="Novi račun" bounds="[10,50][240,150]" clickable="true"/>'
                '<node package="com.brendigo.fakturiva" '
                'text="Klijent" bounds="[10,150][240,190]" clickable="false"/>'
                '</hierarchy>',
                encoding="utf-8",
            )
            result = qa.examine_hierarchy(path, 560, 1120, "Novi račun", 320)
            self.assertEqual(result["clickables"], 1)
            with self.assertRaisesRegex(ValueError, "not visible"):
                qa.examine_hierarchy(path, 560, 1120, "Pregled računa", 320)

    def test_offscreen_clickable_control_is_a_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "layout.xml"
            path.write_text(
                '<hierarchy>'
                '<node package="com.brendigo.fakturiva" '
                'text="Postavke" bounds="[0,0][560,1120]" clickable="false"/>'
                '<node package="com.brendigo.fakturiva" '
                'text="Fakturiva" bounds="[0,10][560,60]" clickable="false"/>'
                '<node package="com.brendigo.fakturiva" '
                'text="Spremi" bounds="[500,100][600,170]" clickable="true"/>'
                '</hierarchy>',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "outside viewport"):
                qa.examine_hierarchy(path, 560, 1120, "Postavke", 320)

    def test_all_twenty_screen_identifiers_are_unique(self):
        self.assertEqual(len(qa.SCREEN_NAMES), 20)
        self.assertEqual(len(set(qa.SCREEN_NAMES)), 20)
        self.assertEqual(len(qa.VISIBLE_HEADERS), 20)


if __name__ == "__main__":
    unittest.main()
