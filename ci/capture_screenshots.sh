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
  adb shell am force-stop "$APP"
  adb shell am start -W -n "$APP/.MainActivity" -a "$APP.SCREENSHOT" --es screen "$number"
  sleep 2
  output="docs/screenshots/${number}_${names[$index]}.png"
  adb exec-out screencap -p > "$output"
  test "$(stat -c%s "$output")" -gt 14000
  echo "Captured $output"
done
# Archive UI errors for QA inspection (screenshots are genuine Android captures).
adb logcat -d -t 800 | grep -E "FATAL EXCEPTION|Emulator screenshot harness failed" > build/screenshot-errors.log || true
if test -s build/screenshot-errors.log; then cat build/screenshot-errors.log; exit 1; fi
