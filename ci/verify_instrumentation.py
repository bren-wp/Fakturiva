#!/usr/bin/env python3
"""Fail CI if Android instrumentation crashed or silently skipped its tests.

AGP can produce a successful Gradle task even when its HTML report contains
'testRunFailed: Process crashed'. This independent check validates generated
test evidence before screenshot capture continues.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET


def count_from_html(path: Path) -> tuple[int, int, int]:
    text = path.read_text(encoding="utf-8")
    if "testRunFailed" in text or "Test run failed to complete" in text:
        raise RuntimeError("Instrumentation process crashed; see android-test.html")
    found: dict[str, int] = {}
    for name in ("tests", "failures", "skipped"):
        match = re.search(
            rf'<div class="infoBox" id="{name}">\s*<div class="counter">(\d+)</div>',
            text,
        )
        if not match:
            raise RuntimeError(f"Instrumentation HTML missing {name} counter")
        found[name] = int(match.group(1))
    return found["tests"], found["failures"], found["skipped"]


def count_from_xml(directory: Path) -> tuple[int, int, int] | None:
    if not directory.is_dir():
        return None
    xml_files = sorted(directory.rglob("TEST-*.xml"))
    if not xml_files:
        return None
    tests = failures = skipped = 0
    for xml_file in xml_files:
        root = ET.parse(xml_file).getroot()
        suites = [root] if root.tag == "testsuite" else root.findall(".//testsuite")
        if not suites:
            raise RuntimeError(f"Invalid test XML: {xml_file}")
        for suite in suites:
            tests += int(suite.attrib.get("tests", 0))
            failures += int(suite.attrib.get("failures", 0))
            failures += int(suite.attrib.get("errors", 0))
            skipped += int(suite.attrib.get("skipped", 0))
    return tests, failures, skipped


def main() -> int:
    report = Path("app/build/reports/androidTests/connected/debug/index.html")
    if not report.is_file():
        print(f"::error::Missing Android instrumentation report: {report}")
        return 1
    try:
        html_result = count_from_html(report)
        xml_result = count_from_xml(Path("app/build/outputs/androidTest-results/connected"))
        if xml_result is not None and xml_result != html_result:
            raise RuntimeError(
                f"Test result mismatch, XML {xml_result} vs HTML {html_result}"
            )
        tests, failures, skipped = html_result
        print(f"Android instrumentation: {tests} tests, {failures} failures, "
              f"{skipped} skipped")
        if tests < 10:
            raise RuntimeError(
                f"Only {tests} test(s) ran; expected at least 10 regression tests"
            )
        if failures != 0 or skipped != 0:
            raise RuntimeError(
                f"Test failures={failures}, skipped={skipped}; no green CI"
            )
        return 0
    except (RuntimeError, ValueError, OSError, ET.ParseError) as error:
        print(f"::error::{error}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
