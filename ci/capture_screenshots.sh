#!/usr/bin/env bash
set -euo pipefail
APP="com.brendigo.fakturiva"
# The original reference profile remains the default; responsive CI overrides
# these variables and writes separate, genuine emulator screenshots.
QA_WIDTH="${QA_WIDTH:-1179}"
QA_HEIGHT="${QA_HEIGHT:-2556}"
QA_DENSITY="${QA_DENSITY:-480}"
QA_FONT_SCALE="${QA_FONT_SCALE:-1.0}"
QA_OUTPUT_DIR="${QA_OUTPUT_DIR:-docs/screenshots}"
QA_ERROR_LOG="${QA_ERROR_LOG:-build/screenshot-errors.log}"
timeout 90s adb wait-for-device ||
  { echo "::error::Android emulator did not become available"; exit 1; }
timeout 90s adb install -r app/build/outputs/apk/debug/app-debug.apk ||
  { echo "::error::APK installation failed"; exit 1; }
timeout 12s adb shell wm size "${QA_WIDTH}x${QA_HEIGHT}"
timeout 12s adb shell wm density "$QA_DENSITY"
timeout 12s adb shell settings put system font_scale "$QA_FONT_SCALE"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
mkdir -p "$QA_OUTPUT_DIR" "$(dirname "$QA_ERROR_LOG")"
# The requested profile's images must never be satisfied by repo-stored PNGs.
rm -f "$QA_OUTPUT_DIR"/*.png
names=(
  pocetni_zaslon dobrodosli prijava pocetna racuni novi_racun stavke_racuna pregled_racuna
  ponude nova_ponuda pregled_ponude klijenti detalji_klijenta artikli_usluge izvjestaji
  naplata eracun_integracija postavke profil_tvrtke tamna_tema
)
capture_failure(){
  local reason="$1"
  echo "::error::$reason"
  # Keep the crash or ANR report even when one adb request times out.
  timeout 8s adb logcat -d -v time -t 500 > "$QA_ERROR_LOG" || true
  exit 1
}
for index in "${!names[@]}"; do
  number=$(printf '%02d' $((index+1)))
  timeout 8s adb shell run-as "$APP" rm -f files/qa-screen-ready.txt ||
    capture_failure "Cannot reset screen-ready marker $number"
  timeout 12s adb shell am force-stop "$APP" ||
    capture_failure "Cannot stop previous screen $number"
  # -W blocks until Activity.onCreate returns. That can deadlock the emulator
  # on API 36; launch asynchronously and wait for our own screen-ready log.
  timeout 20s adb shell am start -n "$APP/.MainActivity" -a "$APP.SCREENSHOT" --es screen "$number" ||
    capture_failure "Android failed to launch requested screen $number"
  ready=false
  for attempt in $(seq 1 20); do
    # A debug-only file is much cheaper and safer than repeatedly dumping
    # full logcat buffers, especially on constrained GitHub emulators.
    signaled="$(timeout 4s adb shell run-as "$APP" cat files/qa-screen-ready.txt 2>/dev/null || true)"
    if [ "$signaled" = "$number" ]; then
      ready=true
      break
    fi
    sleep 1
  done
  if [ "$ready" != true ]; then
    capture_failure "Screenshot harness did not render requested screen $number"
  fi
  # The readiness marker is written AFTER Activity pre-draw, but Android
  # SplashScreen can still remain in the compositor for a short time.
  # An identical frame for a different screen is never accepted as evidence.
  output="$QA_OUTPUT_DIR/${number}_${names[$index]}.png"
  unique=false
  for retry in 1 2 3 4; do
    sleep 1
    timeout 18s adb exec-out screencap -p > "$output" ||
      capture_failure "Screen capture timed out on screen $number (attempt $retry)"
    test "$(stat -c%s "$output")" -gt 14000 ||
      capture_failure "Truncated screenshot for screen $number"
    repeated=false
    for previous in "$QA_OUTPUT_DIR"/*.png; do
      [ -f "$previous" ] || continue
      [ "$previous" = "$output" ] && continue
      if cmp -s "$previous" "$output"; then
        repeated=true
        break
      fi
    done
    if [ "$repeated" = false ]; then
      unique=true
      break
    fi
    echo "Screen $number still displays a prior frame (attempt $retry); waiting for actual rendering"
  done
  if [ "$unique" != true ]; then
    capture_failure "Screen $number never rendered a distinct frame after four captures"
  fi
  echo "Captured distinct $output"
  # For the responsive matrix also collect the *actual accessibility tree*.
  # Screenshot dimensions alone cannot reveal clipped or missing controls.
  if [ "${QA_CAPTURE_ACCESSIBILITY:-0}" = "1" ]; then
    xml="${output%.png}.xml"
    xml_ready=false
    for try in 1 2; do
      if timeout 25s adb shell uiautomator dump /sdcard/fakturiva-qa.xml >/dev/null 2>&1 &&
         timeout 12s adb exec-out cat /sdcard/fakturiva-qa.xml > "$xml" &&
         grep -Fq "<hierarchy" "$xml"; then
        xml_ready=true
        break
      fi
      sleep 1
    done
    if [ "$xml_ready" != true ]; then
      capture_failure "Missing accessibility hierarchy for screen $number"
    fi
  fi
done
# A successful run must have twenty distinct rendered screens. Previous runs
# silently captured onboarding across multiple screen IDs and were not valid.
unique_count="$(sha256sum "$QA_OUTPUT_DIR"/*.png | awk '{print $1}' | sort -u | wc -l)"
if [ "$unique_count" -ne "${#names[@]}" ]; then
  echo "::error::Only $unique_count unique screenshots for ${#names[@]} requested screens."
  exit 1
fi
# Archive UI errors for QA inspection (screenshots are genuine Android captures).
timeout 10s adb logcat -d -t 800 |
  grep -E "FATAL EXCEPTION|Emulator screenshot harness failed" > "$QA_ERROR_LOG" || true
if test -s "$QA_ERROR_LOG"; then cat "$QA_ERROR_LOG"; exit 1; fi
