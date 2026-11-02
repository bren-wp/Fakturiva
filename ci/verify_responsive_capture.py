#!/usr/bin/env python3
"""Validate genuine Android screenshots and accessibility trees at one viewport.

This is a responsive functional smoke audit, NOT a pixel-parity measurement
against the owner's 1179x2556 reference image. All 20 app screens must render
in the requested physical size and remain accessible to the user.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

SCREEN_NAMES = [
    "pocetni_zaslon", "dobrodosli", "prijava", "pocetna", "racuni",
    "novi_racun", "stavke_racuna", "pregled_racuna", "ponude",
    "nova_ponuda", "pregled_ponude", "klijenti", "detalji_klijenta",
    "artikli_usluge", "izvjestaji", "naplata", "eracun_integracija",
    "postavke", "profil_tvrtke", "tamna_tema",
]
VISIBLE_HEADERS = [
    "Fakturiva", "Fakturiva", "Zaštitite svoje poslovanje", "Fakturiva",
    "Računi", "Novi račun", "Stavke računa", "Pregled računa", "Ponude",
    "Nova ponuda", "Pregled ponude", "Klijenti", "Detalji klijenta",
    "Artikli i usluge", "Izvještaji", "Naplata", "eRačuni", "Postavke",
    "Profil tvrtke", "Fakturiva",
]
RECT = re.compile(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")
APP = "com.brendigo.fakturiva"


def png_dimensions(path: Path) -> tuple[int, int]:
    with path.open("rb") as stream:
        header = stream.read(24)
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Invalid PNG header: {path}")
    if header[12:16] != b"IHDR":
        raise ValueError(f"Missing PNG IHDR: {path}")
    return struct.unpack(">II", header[16:24])


def examine_hierarchy(path: Path, width: int, height: int,
                      header: str, density: int) -> dict:
    tree = ET.parse(path)
    root = tree.getroot()
    if root.tag != "hierarchy":
        raise ValueError(f"Not a UIAutomator hierarchy: {path}")
    nodes = list(root.iter("node"))
    app_nodes = [n for n in nodes if n.attrib.get("package") == APP]
    if len(nodes) < 3 or len(app_nodes) < 2:
        raise ValueError(f"Missing Fakturiva UI hierarchy: {path}")
    visible_text = " ".join(
        (n.attrib.get("text", "") + " " + n.attrib.get("content-desc", ""))
        for n in app_nodes
    )
    if header.casefold() not in visible_text.casefold():
        raise ValueError(f"Expected heading {header!r} not visible in {path}")

    offscreen = []
    small_taps = []
    clickables = 0
    for n in app_nodes:
        match = RECT.match(n.attrib.get("bounds", ""))
        if not match:
            continue
        x1, y1, x2, y2 = (int(x) for x in match.groups())
        if x2 < x1 or y2 < y1:
            raise ValueError(f"Inverted view bounds: {path}: {match.group(0)}")
        if n.attrib.get("clickable") != "true" or x2 <= x1 or y2 <= y1:
            continue
        clickables += 1
        desc = n.attrib.get("content-desc") or n.attrib.get("text") or "control"
        if x1 < -2 or y1 < -2 or x2 > width + 2 or y2 > height + 2:
            offscreen.append({"name": desc[:80], "bounds": match.group(0)})
        dp_w = (x2 - x1) * 160 / density
        dp_h = (y2 - y1) * 160 / density
        # Advisory only: tiny icon hit targets can be intentional and are
        # better corrected manually after seeing a genuine emulator capture.
        if dp_w < 44 or dp_h < 44:
            small_taps.append(
                {"name": desc[:80], "width_dp": round(dp_w, 1),
                 "height_dp": round(dp_h, 1)}
            )
    if offscreen:
        raise ValueError(f"Clickable controls outside viewport: {offscreen}")
    return {
        "nodes": len(nodes), "app_nodes": len(app_nodes),
        "clickables": clickables, "small_tap_targets": small_taps[:30]
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("profile")
    parser.add_argument("width", type=int)
    parser.add_argument("height", type=int)
    parser.add_argument("density", type=int)
    parser.add_argument("font_scale", type=float)
    args = parser.parse_args()
    if min(args.width, args.height, args.density) <= 0:
        parser.error("Viewport and density must be positive")
    if not 0.8 <= args.font_scale <= 2.0:
        parser.error("Font scale out of supported test range")

    results = []
    errors = []
    signatures = set()
    for index, name in enumerate(SCREEN_NAMES, start=1):
        stem = f"{index:02d}_{name}"
        png = args.folder / f"{stem}.png"
        xml = args.folder / f"{stem}.xml"
        record = {"screen": stem}
        try:
            if not png.is_file() or not xml.is_file():
                raise ValueError(f"Missing screenshot or accessibility XML: {stem}")
            dimension = png_dimensions(png)
            if dimension != (args.width, args.height):
                raise ValueError(f"Incorrect physical size {dimension}, "
                                 f"wanted {(args.width, args.height)}")
            if png.stat().st_size < 14_000:
                raise ValueError("Suspiciously small PNG (possible blank screen)")
            sha = hashlib.sha256(png.read_bytes()).hexdigest()
            if sha in signatures:
                raise ValueError("Duplicate screenshot of a different screen")
            signatures.add(sha)
            record.update(examine_hierarchy(
                xml, args.width, args.height,
                VISIBLE_HEADERS[index - 1], args.density
            ))
            record["png_sha256"] = sha
            record["bytes"] = png.stat().st_size
            record["passed"] = True
        except (ValueError, OSError, ET.ParseError) as exc:
            record["passed"] = False
            record["error"] = str(exc)
            errors.append(f"{stem}: {exc}")
        results.append(record)
    summary = {
        "profile": args.profile, "width_px": args.width,
        "height_px": args.height, "density_dpi": args.density,
        "font_scale": args.font_scale,
        "screens_expected": len(SCREEN_NAMES),
        "screens_passed": len(SCREEN_NAMES) - len(errors),
        "complete": not errors, "screens": results,
    }
    args.folder.parent.mkdir(parents=True, exist_ok=True)
    report_path = args.folder.parent / "responsive-results.json"
    report_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Responsive {args.profile}: {summary['screens_passed']}/"
          f"{len(SCREEN_NAMES)} screens at {args.width}x{args.height}px, "
          f"{args.density}dpi, font {args.font_scale}")
    for error in errors:
        print("::error::" + error)
    if errors:
        return 1
    warnings = sum(len(row.get("small_tap_targets", [])) for row in results)
    print(f"Advisory small touch targets: {warnings}; details in {report_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
