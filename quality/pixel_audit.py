#!/usr/bin/env python3
"""Compare original Fakturiva reference PNGs with actual Android captures.

Never resize/mask references or captures. Full RGB MAE, true local-window
SSIM and full-resolution per-pixel diff images are produced for all screens.
QA-only: Pillow, NumPy and scikit-image are not Android runtime dependencies.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image
from skimage.metrics import structural_similarity


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def check(reference, capture, diff_file=None):
    with Image.open(reference) as ref_image, Image.open(capture) as capture_image:
        if ref_image.size != capture_image.size:
            return {"error": f"Dimensions: {ref_image.size} vs {capture_image.size}"}
        ref = np.asarray(ref_image.convert("RGB"), dtype=np.uint8)
        actual = np.asarray(capture_image.convert("RGB"), dtype=np.uint8)
        if min(ref.shape[:2]) < 7:
            return {"error": "Image too small for 7x7 SSIM window"}
        difference = np.abs(ref.astype(np.int16) - actual.astype(np.int16)).astype(np.uint8)
        height, width = ref.shape[:2]
        # Secondary diagnostic region. The primary score NEVER omits system bars.
        content = difference[100:height - 150, 25:width - 25]
        if not content.size:
            content = difference
        if diff_file is not None:
            diff_file.parent.mkdir(parents=True, exist_ok=True)
            Image.fromarray(difference, "RGB").save(diff_file)
        return {
            "rgb_mae": round(float(difference.mean()), 4),
            "main_content_mae": round(float(content.mean()), 4),
            "ssim": round(float(structural_similarity(
                ref, actual, channel_axis=2, data_range=255)), 6),
            "red_mae": round(float(difference[:, :, 0].mean()), 4),
            "green_mae": round(float(difference[:, :, 1].mean()), 4),
            "blue_mae": round(float(difference[:, :, 2].mean()), 4),
            "mismatched_pixels_pct": round(
                float(np.any(ref != actual, axis=2).mean() * 100), 3),
            "reference_sha256": digest(reference),
            "capture_sha256": digest(capture),
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("references", type=Path)
    parser.add_argument("captures", type=Path)
    parser.add_argument("--output", type=Path, default=Path("pixel_audit.csv"))
    parser.add_argument("--summary", type=Path, default=Path("pixel_audit_summary.json"))
    parser.add_argument("--diff-dir", type=Path)
    parser.add_argument("--expected", type=int, default=20)
    args = parser.parse_args()
    if args.expected <= 0:
        parser.error("--expected must be positive")
    references = sorted(args.references.glob("*.png"))
    if len(references) != args.expected:
        parser.error(f"Expected {args.expected} reference PNGs, found {len(references)}")
    rows = []
    for reference in references:
        capture = args.captures / reference.name
        if not capture.is_file():
            rows.append({"screen": reference.name, "error": "Screenshot missing"})
            continue
        try:
            metrics = check(reference, capture,
                            args.diff_dir / reference.name if args.diff_dir else None)
            rows.append({"screen": reference.name, **metrics})
        except (OSError, ValueError) as err:
            rows.append({"screen": reference.name, "error": f"Image read failed: {err}"})
    fields = ["screen", "rgb_mae", "main_content_mae", "ssim", "red_mae",
              "green_mae", "blue_mae", "mismatched_pixels_pct",
              "reference_sha256", "capture_sha256", "error"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    valid = [row for row in rows if "error" not in row]
    complete = len(valid) == args.expected
    summary = {
        "screens_expected": args.expected,
        "screens_compared": len(valid),
        "complete": complete,
        "average_rgb_mae": round(sum(r["rgb_mae"] for r in valid) / len(valid), 4)
            if valid else None,
        "average_ssim": round(sum(r["ssim"] for r in valid) / len(valid), 6)
            if valid else None,
        "reference_dir": str(args.references),
        "capture_dir": str(args.captures),
        "method": "unaltered full-resolution RGB; absolute difference; 7x7 SSIM",
    }
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
                            encoding="utf-8")
    for row in rows:
        print(row)
    print(summary)
    if not complete:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
