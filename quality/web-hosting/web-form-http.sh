#!/usr/bin/env bash
set -euo pipefail
port=18090
FAKTURIVA_TEST_HTTP=1 php -S "127.0.0.1:$port" -t web >/tmp/fakturiva-web-http.log 2>&1 &
pid=$!
temp="$(mktemp -d)"
trap 'kill "$pid" 2>/dev/null || true; rm -rf "$temp"' EXIT
base="http://127.0.0.1:$port"
ready=false
for _ in $(seq 1 25); do
  if curl -fsS "$base/register.php" >/dev/null 2>&1; then ready=true;break;fi
  sleep 1
done
test "$ready" = true || { echo "Web UI server failed";exit 1; }
jar="$temp/cookies"
other="$temp/other"
extract_csrf(){
  grep -o 'name="csrf" value="[a-f0-9]\{64\}"' "$1" | head -1 | cut -d '"' -f 4
}
username="ci_web_$(date +%s)_$RANDOM@example.test"
pin=654321
passphrase="Fakturiva-ci-registration-$RANDOM-example"
curl -fsS -b "$jar" -c "$jar" "$base/register.php" > "$temp/register.html"
grep -Fq 'Kreirajte novi račun i započnite s poslovanjem.' "$temp/register.html"
! grep -Fq 'name="pin"' "$temp/register.html"
grep -Fq 'name="passphrase"' "$temp/register.html"
grep -Fq 'name="confirm_passphrase"' "$temp/register.html"
csrf="$(extract_csrf "$temp/register.html")"
test "$(printf '%s' "$csrf" | wc -c)" -eq 64
status="$(curl -sS -o /dev/null -D "$temp/headers" -w '%{http_code}' \
  -b "$jar" -c "$jar" -X POST "$base/auth.php" \
  --data-urlencode "csrf=$csrf" --data-urlencode "action=register" \
  --data-urlencode "username=$username" \
  --data-urlencode "passphrase=$passphrase")"
test "$status" = 303
grep -iq 'location: dashboard.php' "$temp/headers"
curl -fsS -b "$jar" -c "$jar" "$base/dashboard.php" > "$temp/dashboard.html"
grep -Fq "Moj korisnički račun" "$temp/dashboard.html"
grep -Fq "$username" "$temp/dashboard.html"
grep -Fq 'Lozinka za Android sinkronizaciju' "$temp/dashboard.html"

# Browser-created identity must be the very same tenant used by Android's
# HTTPS JSON API; no separate account or PIN should be required.
api_login_payload="$(F_EMAIL="$username" F_PASS="$passphrase" python3 -c \
  'import json,os;print(json.dumps({"action":"login","username":os.environ["F_EMAIL"],"passphrase":os.environ["F_PASS"]}))')"
api_login="$(curl -fsS -H 'Content-Type: application/json' \
  --data "$api_login_payload" "$base/api.php")"
api_token="$(printf '%s' "$api_login" | python3 -c \
  'import sys,json;d=json.load(sys.stdin);assert d["ok"];print(d["data"]["token"])')"
api_snapshot="$(curl -fsS -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $api_token" \
  --data '{"action":"load"}' "$base/api.php")"
printf '%s' "$api_snapshot" | python3 -c \
  'import sys,json;d=json.load(sys.stdin);assert d["ok"];assert d["data"]["revision"]==0'
# Simulate an Android FAK1 encrypted sync, then prove that the very
# same authenticated web session can unlock its persisted invoice.
backup_b64="$(FAKTURIVA_FIXTURE_PASS="$passphrase" php quality/web-hosting/fak1-fixture.php)"
api_save_payload="$(FAK1_B64="$backup_b64" python3 -c \
  'import json,os;print(json.dumps({"action":"save","expected_revision":0,"backup_b64":os.environ["FAK1_B64"]}))')"
api_saved="$(curl -fsS -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $api_token" \
  --data "$api_save_payload" "$base/api.php")"
printf '%s' "$api_saved" | python3 -c \
  'import sys,json;d=json.load(sys.stdin);assert d["ok"];assert d["data"]["revision"]==1'
curl -fsS -b "$jar" -c "$jar" "$base/finance.php" > "$temp/finance-form.html"
finance_csrf="$(extract_csrf "$temp/finance-form.html")"
curl -fsS -b "$jar" -c "$jar" -X POST "$base/finance.php" \
  --data-urlencode "csrf=$finance_csrf" \
  --data-urlencode "passphrase=$passphrase" \
  --data-urlencode "from=2026-01-01" \
  --data-urlencode "to=2026-12-31" > "$temp/finance-unlocked.html"
grep -Fq 'R-2026-WEB-E2E' "$temp/finance-unlocked.html"
grep -Fq 'Brendigo HTTP E2E Klijent' "$temp/finance-unlocked.html"
curl -fsS -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $api_token" \
  --data '{"action":"logout"}' "$base/api.php" >/dev/null

status="$(curl -sS -o /dev/null -w '%{http_code}' -b "$other" -c "$other" "$base/dashboard.php")"
test "$status" = 303
csrf="$(extract_csrf "$temp/dashboard.html")"
status="$(curl -sS -o /dev/null -D "$temp/headers" -w '%{http_code}' \
  -b "$jar" -c "$jar" -X POST "$base/auth.php" \
  --data-urlencode "csrf=$csrf" --data-urlencode "action=logout")"
test "$status" = 303
curl -fsS -b "$jar" -c "$jar" "$base/login.php" > "$temp/password-login.html"
grep -Fq 'Dobro došli natrag' "$temp/password-login.html"
grep -Fq 'name="passphrase"' "$temp/password-login.html"
csrf="$(extract_csrf "$temp/password-login.html")"
status="$(curl -sS -o /dev/null -D "$temp/headers" -w '%{http_code}' \
  -b "$jar" -c "$jar" -X POST "$base/auth.php" \
  --data-urlencode "csrf=$csrf" --data-urlencode "action=login-password" \
  --data-urlencode "username=$username" --data-urlencode "passphrase=$passphrase" \
  --data-urlencode "remember=yes")"
test "$status" = 303
curl -fsS -b "$jar" -c "$jar" "$base/dashboard.php" > "$temp/password-dashboard.html"
grep -Fq "$username" "$temp/password-dashboard.html"
csrf="$(extract_csrf "$temp/password-dashboard.html")"
curl -sS -o /dev/null -b "$jar" -c "$jar" -X POST "$base/auth.php" \
  --data-urlencode "csrf=$csrf" --data-urlencode "action=logout"
# New browser accounts have no web PIN. Legacy PIN access is tested
# separately by web-auth-smoke.php; never silently create a default PIN.
curl -fsS -b "$jar" -c "$jar" "$base/login.php" > "$temp/login-again.html"
csrf="$(extract_csrf "$temp/login-again.html")"
status="$(curl -sS -o /dev/null -D "$temp/headers" -w '%{http_code}' \
  -b "$jar" -c "$jar" -X POST "$base/auth.php" \
  --data-urlencode "csrf=$csrf" --data-urlencode "action=login-password" \
  --data-urlencode "username=$username" --data-urlencode "passphrase=$passphrase")"
test "$status" = 303
grep -iq 'location: dashboard.php' "$temp/headers"
curl -fsS -b "$jar" -c "$jar" "$base/delete-account.php" > "$temp/delete.html"
grep -Fq 'Lozinka računa za potvrdu brisanja' "$temp/delete.html"
csrf="$(extract_csrf "$temp/delete.html")"
status="$(curl -sS -o "$temp/delete-result.html" -w '%{http_code}' \
  -b "$jar" -c "$jar" -X POST "$base/delete-account.php" \
  --data-urlencode "csrf=$csrf" --data-urlencode "pin=$passphrase" \
  --data-urlencode "confirm=DELETE")"
test "$status" = 200
grep -Fq 'trajno su uklonjeni' "$temp/delete-result.html"
status="$(curl -sS -o /dev/null -w '%{http_code}' -b "$jar" -c "$jar" "$base/dashboard.php")"
test "$status" = 303
echo "Real PHP email/password registration/login, CSRF, session isolation and password-verified deletion: OK"
