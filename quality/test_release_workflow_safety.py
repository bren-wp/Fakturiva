"""Regression checks for immutable PR-reviewed preview releases.

These are static safety guards; successful end-to-end GitHub Actions jobs
must still be verified before publishing any release.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
PREVIEW = (WORKFLOWS / "preview-release.yml").read_text(encoding="utf-8")
LEGACY = (WORKFLOWS / "pr27-preview-candidate.yml").read_text(encoding="utf-8")

REQUIRED_CHECKS = (
    "Android verification",
    "Verify native Android screens",
    "Responsive Android screen matrix",
    "Self-hosted Fakturiva JSON API security smoke",
    "Fakturiva Windows preview build",
)


class ReleaseWorkflowSafetyTest(unittest.TestCase):
    def test_main_is_never_modified_by_release_jobs(self):
        for path in WORKFLOWS.glob("*.yml"):
            with self.subTest(workflow=path.name):
                source = path.read_text(encoding="utf-8")
                self.assertNotRegex(
                    source, r"git\s+push\s+(?:origin\s+(?:main|HEAD:main)|[^\n]*refs/heads/main)",
                )
        self.assertNotRegex(PREVIEW, r"\bgit\s+(?:commit|push)\b")
        self.assertIn("git diff --quiet HEAD -- docs/screenshots/", PREVIEW)
        self.assertIn("Fakturiva-screenshot-gallery-differences.txt", PREVIEW)

    def test_release_requires_main_ref_and_immutable_version_tag(self):
        self.assertIn("if: github.ref == 'refs/heads/main'", PREVIEW)
        self.assertIn('gh release view "v${VERSION}"', PREVIEW)
        self.assertIn('refs/tags/v${VERSION}', PREVIEW)
        self.assertIn("should_release=false", PREVIEW)
        self.assertIn("needs: [prepare-preview, windows-preview]", PREVIEW)
        self.assertIn('if: needs.prepare-preview.outputs.should_release == \'true\'', PREVIEW)
        self.assertIn('--target "$RELEASE_TARGET"', PREVIEW)

    def test_release_gates_are_all_required(self):
        for check in REQUIRED_CHECKS:
            with self.subTest(check=check):
                self.assertIn(check, PREVIEW)
        self.assertIn("gh api", PREVIEW)
        self.assertIn("actions/runs?head_sha=$GITHUB_SHA", PREVIEW)
        self.assertIn("Main-branch workflow gates did not all pass", PREVIEW)
        self.assertNotIn("done <<", PREVIEW)

    def test_release_captures_real_screenshots_without_publishing_mocks(self):
        self.assertIn("bash ci/run_android_qa.sh", PREVIEW)
        self.assertIn("docs/screenshots/*.png", PREVIEW)
        self.assertIn("Fakturiva-v${VERSION}-screenshots.zip", PREVIEW)
        self.assertIn("git ls-files 'docs/screenshots/*.png'", PREVIEW)

    def test_release_dependencies_revalidate_on_version_changes(self):
        for name in ("screenshots.yml", "responsive.yml", "self-host-json.yml", "windows.yml"):
            with self.subTest(workflow=name):
                source = (WORKFLOWS / name).read_text(encoding="utf-8")
                self.assertIn("'.github/workflows/preview-release.yml'", source)
                self.assertIn("'docs/screenshots/**'", source)
            if name in ("self-host-json.yml", "windows.yml"):
                self.assertIn("'app/build.gradle'", source)

    def test_real_emulator_invoice_fixtures_have_fixed_dates(self):
        harness = (
            ROOT / "app/src/debug/java/com/brendigo/fakturiva/ScreenshotHarness.java"
        ).read_text(encoding="utf-8")
        self.assertIn('set(a,"draftIssue","2026-10-27")', harness)
        self.assertIn('set(a,"draftDue","2026-11-10")', harness)
        self.assertIn('set(a,"draftSupply","2026-10-27")', harness)
        self.assertEqual(harness.count("fixedReferenceInvoiceDates(a);"), 2)

    def test_legacy_pr27_cannot_publish_historical_release(self):
        self.assertIn("workflow_dispatch:", LEGACY)
        self.assertNotIn("gh release create", LEGACY)
        self.assertNotIn("contents: write", LEGACY)
        self.assertNotIn("branches: [feat/server-accounts-admin-compliance-win-2026]", LEGACY)


if __name__ == "__main__":
    unittest.main()
