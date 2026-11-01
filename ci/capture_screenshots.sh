#!/usr/bin/env bash
set -euo pipefail
APP="com.brendigo.fakturiva"
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
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
for index in "${!names[@]}"; do
  number=$(printf '%02d' $((index+1)))
  adb logcat -c
  adb shell am force-stop "$APP"
  adb shell am start -W -n "$APP/.MainActivity" -a "$APP.SCREENSHOT" --es screen "$number"
  # A completed Activity launch is NOT proof that the requested screen was
  # rendered. The debug-only harness announces the exact screen when ready.
  ready=false
  for attempt in $(seq 1 15); do
    if adb logcat -d -s FakturivaQA:I '*:S' | grep -Fq "QA_SCREEN_READY $number"; then
      ready=true
      break
    fi
    sleep 1
  done
  if [ "$ready" != true ]; then
    echo "::error::Screenshot harness did not render requested screen $number"
    adb logcat -d -t 300 > build/screenshot-errors.log
    exit 1
  fi
  output="docs/screenshots/${number}_${names[$index]}.png"
  adb exec-out screencap -p > "$output"
  test "$(stat -c%s "$output")" -gt 14000
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
