"""Regression tests for the repository-wide static security/dead-code audit."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "code_security_audit.py"
SPEC = importlib.util.spec_from_file_location("source_security_audit", SCRIPT)
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class StaticCodeAuditTest(unittest.TestCase):
    def test_detects_insecure_production_manifest(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = root / "app/src/main/AndroidManifest.xml"
            manifest.parent.mkdir(parents=True)
            manifest.write_text(
                '<manifest><application android:usesCleartextTraffic="true" '
                'android:debuggable="true"/></manifest>', encoding="utf-8")
            result = audit.scan_static(root)
            rules = {item["rule"] for item in result["critical_findings"]}
            self.assertIn("android-cleartext", rules)
            self.assertIn("android-debuggable", rules)

    def test_detects_production_shell_execution_and_secret_file(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            php = root / "web/public.php"
            php.parent.mkdir(parents=True)
            php.write_text(chr(10).join([
                "<?php", "function staleCode(){}", "function liveCode(){}",
                "liveCode();", ""
            ]), encoding="utf-8")
            session = root / "web/storage/data/sessions/sess_test"
            session.parent.mkdir(parents=True)
            session.write_text("private-session", encoding="utf-8")
            result = audit.scan_static(root)
            names = {row["function"] for row in result["possible_dead_php_functions"]}
            self.assertIn("staleCode", names)
            self.assertNotIn("liveCode", names)
            self.assertIn("web/storage/data/sessions/sess_test",
                          result["runtime_secret_files"])

    def test_dead_code_is_advisory_not_auto_deleted(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            java = root / "app/src/main/java/Fake.java"
            java.parent.mkdir(parents=True)
            java.write_text(
                "public class Fake {\n"
                "    private void uncalled() {}\n"
                "    private void used() {}\n"
                "    void onTap() { used(); }\n"
                "}\n", encoding="utf-8")
            result = audit.scan_static(root)
            names = {item["method"] for item in result["possible_dead_private_methods"]}
            self.assertIn("uncalled", names)
            self.assertNotIn("used", names)

    def test_real_production_source_contains_no_guarded_hazards(self):
        result = audit.scan_static()
        self.assertGreaterEqual(result["scanned_files"], 65)
        self.assertEqual(result["critical_findings"], [])
        self.assertEqual(result["runtime_secret_files"], [])


if __name__ == "__main__":
    unittest.main()
