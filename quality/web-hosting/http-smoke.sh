#!/usr/bin/env bash
set -euo pipefail
# Only the PHP built-in loopback server accepts this test-only HTTP override.
port="18089"
# Every HTTP test gets its own private encrypted storage. Never touch an
# operator's live accounts if the test runs from a deployed checkout.
temp="$(mktemp -d)"
mkdir -p "$temp/private"
FAKTURIVA_STORAGE_ROOT="$temp/private" FAKTURIVA_TEST_HTTP=1 \
  php -d display_errors=1 -S "127.0.0.1:$port" -t web > "$temp/php.log" 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true; rm -rf "$temp"' EXIT
ready=false
for _ in $(seq 1 30); do
  if curl --silent --fail "http://127.0.0.1:$port/api.php?action=health" > /dev/null; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then
  echo "Local PHP server did not start" >&2
  exit 1
fi
name="qa_http_$(date +%s)_$RANDOM@example.test"
password='A private hosting passphrase with 16+ chars'
invalid_status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  --data '{"action":"register","username":"not-an-email","passphrase":"a-long-valid-test-password"}' \
  "http://127.0.0.1:$port/api.php")"
test "$invalid_status" = "422" || { echo "API accepted a non-email registration" >&2; exit 1; }
body="$(printf '{"action":"register","username":"%s","passphrase":"%s"}' "$name" "$password")"
response="$(curl -fsS -X POST -H 'Content-Type: application/json' --data "$body"   "http://127.0.0.1:$port/api.php")"
token="$(printf '%s' "$response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')"
if [ -z "$token" ]; then echo "Missing session token" >&2;exit 1;fi
# Typed JSON contract: arrays, objects and explicit nulls must be rejected
# with one parseable JSON error, even when PHP display_errors=1.
for malformed in \
  '{"action":[]}' \
  '{"action":"login","username":[],"passphrase":"long-enough-credential"}' \
  '{"action":"login","username":"test@example.test","passphrase":{}}' \
  '{"action":"login","username":null,"passphrase":"long-enough-credential"}' \
  '{"action":"save","backup_b64":[]}'; do
  invalid_code="$(curl -sS -o "$temp/invalid.json" -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    --data "$malformed" "http://127.0.0.1:$port/api.php")"
  test "$invalid_code" = "400" || {
    echo "Malformed JSON value returned HTTP $invalid_code, expected 400" >&2; exit 1;
  }
  python3 -m json.tool "$temp/invalid.json" >/dev/null || {
    echo "Malformed JSON request produced warnings or invalid JSON" >&2; exit 1;
  }
  python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert d["ok"] is False and d["error"]=="Invalid string field"' "$temp/invalid.json"
done
status="$(curl -sS -o "$temp/unauth.json" -w '%{http_code}' -X POST   -H 'Content-Type: application/json' --data '{"action":"load"}'   "http://127.0.0.1:$port/api.php")"
test "$status" = "401" || { echo "Unauthorized access was not denied" >&2; exit 1; }
curl -fsS -X POST -H 'Content-Type: application/json'   -H "Authorization: Bearer $token" --data '{"action":"load"}'   "http://127.0.0.1:$port/api.php" | python3 -c 'import json,sys; r=json.load(sys.stdin); assert r["data"]["revision"]==0 and r["data"]["backup_b64"] is None'
curl -fsS -X POST -H 'Content-Type: application/json'   -H "Authorization: Bearer $token"   --data "$(printf '{"action":"delete","passphrase":"%s"}' "$password")"   "http://127.0.0.1:$port/api.php" | python3 -c 'import json,sys; assert json.load(sys.stdin)["data"]["deleted"]'
status="$(curl -sS -o "$temp/deleted.json" -w '%{http_code}' -X POST   -H 'Content-Type: application/json' -H "Authorization: Bearer $token"   --data '{"action":"load"}' "http://127.0.0.1:$port/api.php")"
test "$status" = "401" || { echo "Deleted account access was not revoked" >&2; exit 1; }
echo "HTTP typed JSON/auth/tenant gate/delete API smoke: OK"
