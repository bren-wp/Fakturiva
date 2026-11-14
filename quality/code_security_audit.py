#!/usr/bin/env python3
"""Repository-wide static inventory for security hazards and possible dead code.

This scan deliberately reports *candidates*, not proof of a vulnerability or
unreachable code. Runtime penetration tests, mobile device testing and hosting
configuration verification remain separate release gates.
"""
from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "build" / "code-security-audit.json"
SOURCE_EXTENSIONS = {".java", ".php", ".js", ".xml", ".sh", ".cs"}
EXCLUDE_DIRS = {".git", ".gradle", "build", ".idea", "node_modules"}
RULES = (
    ("android-cleartext", "*.xml", r'android:usesCleartextTraffic\s*=\s*"true"'),
    ("android-debuggable", "*.xml", r'android:debuggable\s*=\s*"true"'),
    ("android-backup-enabled", "*.xml", r'android:allowBackup\s*=\s*"true"'),
    ("android-universal-file-access", "*.java", r'\bsetAllowUniversalAccessFromFileURLs\s*\(\s*true\s*\)'),
    ("windows-webview-host-objects", "*.cs", r'\bAreHostObjectsAllowed\s*=\s*true\b'),
    ("windows-webview-messages", "*.cs", r'\bIsWebMessageEnabled\s*=\s*true\b'),
    ("windows-webview-devtools", "*.cs", r'\bAreDevToolsEnabled\s*=\s*true\b'),
    ("permissive-tls", "*.java", r'setHostnameVerifier\s*\([^;]*->\s*true'),
    ("trust-all-certs", "*.java", r'X509TrustManager\s*\[\s*\]\s*.*trustAll'),
    ("android-js-bridge", "*.java", r'\baddJavascriptInterface\s*\('),
    ("php-command-execution", "*.php", r'\b(?:eval|shell_exec|passthru|system)\s*\('),
    ("php-unserialize", "*.php", r'\bunserialize\s*\('),
    ("production-old-domain", "*.java", r'https://fakturiva\.com(?:/|["\x27])'),
)
PRIVATE = re.compile(r"^\s*private\s+(?:static\s+)?(?:final\s+)?"
                     r"(?:[\w<>\[\],?]+\s+)+([a-zA-Z_]\w*)\s*\(")


def source_paths(root: Path = ROOT) -> list[Path]:
    return sorted(path for path in root.rglob("*")
                  if path.is_file() and path.suffix in SOURCE_EXTENSIONS
                  and not any(part in EXCLUDE_DIRS for part in path.relative_to(root).parts)
                  and "docs" not in path.relative_to(root).parts)


def scan_static(root: Path = ROOT) -> dict:
    paths = source_paths(root)
    texts: dict[Path, str] = {}
    critical: list[dict] = []
    counts = Counter()
    for path in paths:
        relative = path.relative_to(root).as_posix()
        counts[path.suffix] += 1
        raw = path.read_text(encoding="utf-8", errors="replace")
        texts[path] = raw
        # Fixtures and intentional negative-security tests are excluded.
        if ("/androidTest/" in "/" + relative or
            "/debug/" in "/" + relative or relative.startswith("quality/")):
            continue
        for name, pattern, regex in RULES:
            if not path.match(pattern):
                continue
            # Android manifest attributes count only inside production manifest.
            if name in ("android-cleartext", "android-debuggable", "android-backup-enabled"):
                if relative != "app/src/main/AndroidManifest.xml":
                    continue
            for match in re.finditer(regex, raw, flags=re.IGNORECASE | re.DOTALL):
                line = raw.count("\n", 0, match.start()) + 1
                critical.append({"rule": name, "file": relative, "line": line})

    production_java = {
        path: code for path, code in texts.items()
        if "/src/main/java/" in "/" + path.relative_to(root).as_posix()
    }
    all_java = "\n".join(
        code for path, code in texts.items() if path.suffix == ".java"
    )
    # Advisory only. Reflection, XML references and third-party callers can
    # make a method live despite a single textual occurrence.
    possible_dead: list[dict] = []
    for path, code in production_java.items():
        for lineno, line in enumerate(code.splitlines(), 1):
            match = PRIVATE.search(line)
            if not match:
                continue
            name = match.group(1)
            uses = len(re.findall(r"\b" + re.escape(name) + r"\s*\(", all_java))
            references = all_java.count("::" + name) + all_java.count('"' + name + '"')
            if uses == 1 and references == 0:
                possible_dead.append({
                    "file": path.relative_to(root).as_posix(),
                    "line": lineno, "method": name
                })

    # PHP global-function reachability is only a conservative text heuristic.
    # Dynamic callbacks and cross-file entrypoints require manual verification.
    php_files = {path: code for path, code in texts.items()
                 if path.suffix == ".php" and path.relative_to(root).parts[0] == "web"}
    all_php = "\n".join(php_files.values())
    possible_dead_php = []
    for path, code in php_files.items():
        for match in re.finditer(r"(?m)^\s*function\s+([A-Za-z_]\w*)\s*\(", code):
            name = match.group(1)
            calls = len(re.findall(r"\b" + re.escape(name) + r"\s*\(", all_php))
            refs = all_php.count("'" + name + "'") + all_php.count('"' + name + '"')
            if calls == 1 and refs == 0:
                possible_dead_php.append({
                    "file": path.relative_to(root).as_posix(),
                    "line": code.count("\n", 0, match.start()) + 1,
                    "function": name
                })

    # Scan the full tree for runtime records (including .json excluded from
    # source scans). This is a filesystem inventory, not just a PHP scan.
    secret_files = []
    for path in root.rglob("*"):
        if not path.is_file() or any(part in EXCLUDE_DIRS for part in path.relative_to(root).parts):
            continue
        relative = path.relative_to(root).as_posix()
        if re.match(r"web/storage/(?:secret\.php|data/(?:accounts|index|limits)/.*\.json|data/sessions/sess_.*)$",
                    relative):
            secret_files.append(relative)
    large_modules = sorted((
        {"file": path.relative_to(root).as_posix(),
         "lines": code.count("\n") + 1}
        for path, code in texts.items()
        if code.count("\n") + 1 > 600
    ), key=lambda row: -row["lines"])
    return {
        "scanned_files": len(paths), "by_extension": dict(sorted(counts.items())),
        "critical_findings": critical, "possible_dead_private_methods": possible_dead,
        "possible_dead_php_functions": possible_dead_php,
        "runtime_secret_files": secret_files,
        "large_source_modules_for_refactoring": large_modules,
        "limits": "Static heuristics only; runtime, server configuration, third-party "
                  "dependencies and UX visual comparisons need separate verification.",
    }


def main() -> int:
    audit = scan_static()
    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_text(json.dumps(audit, indent=2, ensure_ascii=False) + "\n",
                    encoding="utf-8")
    print(f"Static audit: {audit['scanned_files']} files, "
          f"{len(audit['critical_findings'])} guarded-pattern matches, "
          f"{len(audit['possible_dead_private_methods'])} dead-code candidates.")
    print(f"Report: {DEST.relative_to(ROOT)}")
    for finding in audit["critical_findings"] + [
            {"rule": "tracked-secret", "file": x, "line": 1}
            for x in audit["runtime_secret_files"]]:
        print(f"::error::{finding['rule']}: {finding['file']}:{finding['line']}")
    return 1 if audit["critical_findings"] or audit["runtime_secret_files"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
