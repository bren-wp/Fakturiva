#!/usr/bin/env bash
# Run from a trusted workstation with USB debugging enabled on test-only phones.
set -euo pipefail
command -v adb >/dev/null || { echo "Android SDK adb is not installed"; exit 2; }
command -v gradle >/dev/null || { echo "Gradle is not installed"; exit 2; }
gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
mapfile -t SERIALS < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [ "${#SERIALS[@]}" -eq 0 ]; then
  echo "No authorized physical Android device detected. Physical validation NOT performed." >&2
  exit 3
fi
n=0
for serial in "${SERIALS[@]}"; do
  emulator="$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')"
  if [ "$emulator" = "1" ]; then
    echo "Skipping emulator. This script requires actual physical devices."
    continue
  fi
  api="$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
  if [ "$api" -lt 24 ]; then
    echo "Skipping unsupported API level $api";continue
  fi
  echo "Running isolated device-side regression tests on physical Android API $api"
  adb -s "$serial" install -r -t app/build/outputs/apk/debug/app-debug.apk
  adb -s "$serial" install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb -s "$serial" shell am instrument -w -r \
    com.brendigo.fakturiva.test/android.test.InstrumentationTestRunner
  n=$((n+1))
done
if [ "$n" -eq 0 ]; then
  echo "No physical Android test was executed." >&2
  exit 4
fi
echo "$n physical Android device(s) passed the isolated regression suite."
