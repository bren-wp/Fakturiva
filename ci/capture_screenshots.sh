#!/usr/bin/env bash
set -euo pipefail
APP="com.brendigo.fakturiva"
timeout 90s adb wait-for-device ||
  { echo "::error::Android emulator did not become available"; exit 1; }
timeout 90s adb install -r app/build/outputs/apk/debug/app-debug.apk ||
  { echo "::error::APK installation failed"; exit 1; }
adb shell wm size 1179x2556
adb shell wm density 480
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
mkdir -p docs/screenshots
names=(
  pocetni_zaslon dobrodosli prijava pocetna racuni novi_racun stavke_racuna pregled_racuna
  ponude nova_ponuda pregled_ponude klijenti detalji_klijenta artikli_usluge izvjestaji
  naplata eracun_integracija postavke profil_tvrtke tamna_tema
)
capture_failure(){
  local reason="$1"
  echo "::error::$reason"
  # Keep the crash or ANR report even when one adb request times out.
  timeout 8s adb logcat -d -v time -t 500 > build/screenshot-errors.log || true
  exit 1
}
for index in "${!names[@]}"; do
  number=$(printf '%02d' $((index+1)))
  timeout 10s adb logcat -c || capture_failure "ADB logcat unavailable for screen $number"
  timeout 12s adb shell am force-stop "$APP" ||
    capture_failure "Cannot stop previous screen $number"
  # -W blocks until Activity.onCreate returns. That can deadlock the emulator
  # on API 36; launch asynchronously and wait for our own screen-ready log.
  timeout 20s adb shell am start -n "$APP/.MainActivity" -a "$APP.SCREENSHOT" --es screen "$number" ||
    capture_failure "Android failed to launch requested screen $number"
  ready=false
  for attempt in $(seq 1 25); do
    # Do not combine grep -q with a pipe under pipefail: grep may close
    # stdout early and cause adb to return SIGPIPE despite a correct match.
    recent_log="$(timeout 5s adb logcat -d -s FakturivaQA:I '*:S' || true)"
    if grep -Fq "QA_SCREEN_READY $number" <<< "$recent_log"; then
      ready=true
      break
    fi
    sleep 1
  done
  if [ "$ready" != true ]; then
    capture_failure "Screenshot harness did not render requested screen $number"
  fi
  # Let the view hierarchy finish one real frame after rendering the page.
  sleep 0.4
  output="docs/screenshots/${number}_${names[$index]}.png"
  timeout 15s adb exec-out screencap -p > "$output" ||
    capture_failure "Screen capture timed out on screen $number"
  test "$(stat -c%s "$output")" -gt 14000 ||
    capture_failure "Truncated screenshot for screen $number"
  echo "Captured $output"
done
# A successful run must have twenty distinct rendered screens. Previous runs
# silently captured onboarding across multiple screen IDs and were not valid.
unique_count="$(sha256sum docs/screenshots/*.png | awk '{print $1}' | sort -u | wc -l)"
if [ "$unique_count" -ne "${#names[@]}" ]; then
  echo "::error::Only $unique_count unique screenshots for ${#names[@]} requested screens."
  exit 1
fi
# Archive UI errors for QA inspection (screenshots are genuine Android captures).
adb logcat -d -t 800 | grep -E "FATAL EXCEPTION|Emulator screenshot harness failed" > build/screenshot-errors.log || true
if test -s build/screenshot-errors.log; then cat build/screenshot-errors.log; exit 1; fi
