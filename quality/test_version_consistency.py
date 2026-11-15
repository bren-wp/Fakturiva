"""Fail closed when Android, Windows and the installer use divergent versions.

A public preview must not be shipped with misleading desktop package metadata.
All tests use only checked-in configuration and no business data.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


def find(pattern: str, path: str) -> str:
    source = (ROOT / path).read_text(encoding="utf-8")
    match = re.search(pattern, source, flags=re.MULTILINE)
    if match is None:
        raise AssertionError(f"Missing version field in {path}")
    return match.group(1)


class VersionConsistencyTest(unittest.TestCase):
    def test_android_windows_inno_and_notes_agree(self):
        version = find(r"^\s*versionName\s+'([^']+)'", "app/build.gradle")
        code = int(find(r"^\s*versionCode\s+(\d+)", "app/build.gradle"))
        desktop = find(r"<Version>([^<]+)</Version>", "win/Fakturiva.Win.csproj")
        installer = find(r'^#define MyAppVersion "([^"]+)"', "win/installer.iss")
        file_version = find(r"^VersionInfoVersion=(\d+\.\d+\.\d+\.\d+)", "win/installer.iss")

        match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)-preview\.(\d+)", version)
        self.assertIsNotNone(match, "Preview must have an explicit semver suffix")
        self.assertGreater(code, 0, "Android versionCode must be positive")
        self.assertEqual(desktop, version)
        self.assertEqual(installer, version)
        self.assertEqual(file_version, ".".join(match.groups()[:3]) + ".0")

        release_series = version.split("-preview.", 1)[0]
        notes = ROOT / "docs" / f"V{release_series}_RELEASE_NOTES.md"
        self.assertTrue(notes.is_file(), f"Missing release notes: {notes}")
        self.assertIn(version, notes.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
