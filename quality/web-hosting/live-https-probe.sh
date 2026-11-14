#!/usr/bin/env bash
# Read-only production reachability smoke. Never send credentials or write data.
set -euo pipefail
URL="${1:-https://fakturiva.eu/api.php?action=health}"
case "$URL" in
  https://fakturiva.eu/api.php\?action=health) ;;
  *) echo "Only canonical production HTTPS health URL is permitted" >&2; exit 2 ;;
esac
body="$(mktemp)"
headers="$(mktemp)"
trap 'rm -f "$body" "$headers"' EXIT
http_code="$(curl --silent --show-error --proto '=https'   --max-redirs 0 --connect-timeout 8 --max-time 18   --output "$body" --dump-header "$headers"   --write-out '%{http_code}' "$URL")"
if [[ "$http_code" != "200" ]]; then
  echo "Public server health returned HTTP $http_code; no account action attempted." >&2
  exit 1
fi
python3 - "$body" <<'PY'
import json, sys
from pathlib import Path
raw=Path(sys.argv[1]).read_bytes()
if len(raw)>4096:
    raise SystemExit("Unexpectedly large public health response")
data=json.loads(raw)
if not isinstance(data,dict) or data.get("ok") is not True or data.get("service")!="fakturiva-self-host":
    raise SystemExit("HTTPS responded, but not with the expected Fakturiva health contract")
print("Public HTTPS Fakturiva health: OK; API version:",data.get("version","unspecified"))
PY
