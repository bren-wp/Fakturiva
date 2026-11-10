#!/usr/bin/env bash
set -euo pipefail
# Only the PHP built-in loopback server accepts this test-only HTTP override.
port="18089"
FAKTURIVA_TEST_HTTP=1 php -S "127.0.0.1:$port" -t web > /tmp/fakturiva-http-test.log 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true' EXIT
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
status="$(curl -sS -o /tmp/fakturiva-unauth.json -w '%{http_code}' -X POST   -H 'Content-Type: application/json' --data '{"action":"load"}'   "http://127.0.0.1:$port/api.php")"
test "$status" = "401" || { echo "Unauthorized access was not denied" >&2; exit 1; }
curl -fsS -X POST -H 'Content-Type: application/json'   -H "Authorization: Bearer $token" --data '{"action":"load"}'   "http://127.0.0.1:$port/api.php" | python3 -c 'import json,sys; r=json.load(sys.stdin); assert r["data"]["revision"]==0 and r["data"]["backup_b64"] is None'
curl -fsS -X POST -H 'Content-Type: application/json'   -H "Authorization: Bearer $token"   --data "$(printf '{"action":"delete","passphrase":"%s"}' "$password")"   "http://127.0.0.1:$port/api.php" | python3 -c 'import json,sys; assert json.load(sys.stdin)["data"]["deleted"]'
status="$(curl -sS -o /tmp/fakturiva-deleted.json -w '%{http_code}' -X POST   -H 'Content-Type: application/json' -H "Authorization: Bearer $token"   --data '{"action":"load"}' "http://127.0.0.1:$port/api.php")"
test "$status" = "401" || { echo "Deleted account access was not revoked" >&2; exit 1; }
echo "HTTP register/auth/tenant gate/delete API smoke: OK"
