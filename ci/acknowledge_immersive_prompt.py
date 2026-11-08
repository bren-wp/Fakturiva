#!/usr/bin/env python3
"""Find Android's *system-owned* immersive-mode tutorial acknowledgment.

Print only a numeric tap coordinate for the known SystemUI prompt. Never
dismiss dialogs or controls owned by the Fakturiva application.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

BOUNDS = re.compile(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")
SYSTEM_PACKAGES = frozenset({"com.android.systemui", "android"})


def acknowledgment(root: ET.Element) -> tuple[int, int] | None:
    nodes = list(root.iter("node"))
    system = [n for n in nodes if n.get("package") in SYSTEM_PACKAGES]
    has_immersive_title = any(
        n.get("resource-id", "").endswith("immersive_cling_title") or
        n.get("text", "").strip().casefold() == "viewing full screen"
        for n in system
    )
    if not has_immersive_title:
        return None
    for n in system:
        label = n.get("text", "").strip().casefold()
        resource = n.get("resource-id", "")
        if label not in ("got it", "ok") and not resource.endswith("/ok"):
            continue
        matched = BOUNDS.fullmatch(n.get("bounds", ""))
        if not matched:
            continue
        x1, y1, x2, y2 = map(int, matched.groups())
        if 0 <= x1 < x2 and 0 <= y1 < y2:
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def main() -> int:
    if len(sys.argv) != 2:
        return 2
    result = acknowledgment(ET.parse(Path(sys.argv[1])).getroot())
    if result:
        print(*result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
