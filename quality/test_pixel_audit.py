"""Focused pixel audit regression tests: real numbers, no generated APK captures."""
import tempfile
import unittest
from pathlib import Path
from PIL import Image
from pixel_audit import check


class PixelAuditTests(unittest.TestCase):
    def test_identity_is_exact(self):
        with tempfile.TemporaryDirectory() as folder:
            ref = Path(folder) / "reference.png"
            actual = Path(folder) / "capture.png"
            Image.new("RGB", (120, 180), (20, 40, 60)).save(ref)
            Image.new("RGB", (120, 180), (20, 40, 60)).save(actual)
            result = check(ref, actual)
            self.assertEqual(0.0, result["rgb_mae"])
            self.assertEqual(1.0, result["ssim"])
            self.assertEqual(0.0, result["mismatched_pixels_pct"])
            self.assertEqual(0, result["mismatched_pixel_count"])

    def test_differences_are_not_masked(self):
        with tempfile.TemporaryDirectory() as folder:
            ref = Path(folder) / "reference.png"
            actual = Path(folder) / "capture.png"
            diff = Path(folder) / "diff.png"
            Image.new("RGB", (120, 180), (0, 0, 0)).save(ref)
            Image.new("RGB", (120, 180), (255, 0, 0)).save(actual)
            result = check(ref, actual, diff)
            self.assertEqual(85.0, result["rgb_mae"])
            self.assertEqual(100.0, result["mismatched_pixels_pct"])
            self.assertLess(result["ssim"], 1.0)
            self.assertTrue(diff.is_file())

    def test_single_pixel_difference_is_never_reported_exact(self):
        with tempfile.TemporaryDirectory() as folder:
            ref = Path(folder) / "reference.png"
            actual = Path(folder) / "capture.png"
            Image.new("RGB", (120, 180), (0, 0, 0)).save(ref)
            frame = Image.new("RGB", (120, 180), (0, 0, 0))
            frame.putpixel((60, 90), (1, 0, 0))
            frame.save(actual)
            result = check(ref, actual)
            # Rounded MAE can be 0.0 on a large enough bitmap, but
            # true 1:1 pixel identity must use the full integer count.
            self.assertEqual(1, result["mismatched_pixel_count"])
            self.assertGreater(result["mismatched_pixels_pct"], 0)

    def test_dimension_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            ref = Path(folder) / "reference.png"
            actual = Path(folder) / "capture.png"
            Image.new("RGB", (120, 180)).save(ref)
            Image.new("RGB", (119, 180)).save(actual)
            self.assertIn("Dimensions", check(ref, actual)["error"])


if __name__ == "__main__":
    unittest.main()
