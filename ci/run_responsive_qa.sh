#!/usr/bin/env bash
set -euo pipefail
# Independent emulator matrix profile; no private reference PNGs required.
# Usage: bash ci/run_responsive_qa.sh NAME WIDTH_PX HEIGHT_PX DENSITY FONT_SCALE
if [ "$#" -ne 5 ]; then
  echo "Usage: $0 profile width height density font_scale" >&2
  exit 2
fi
profile="$1"
width="$2"
height="$3"
density="$4"
font="$5"
if ! [[ "$profile" =~ ^[a-z0-9-]+$ &&
       "$width" =~ ^[0-9]+$ && "$height" =~ ^[0-9]+$ &&
       "$density" =~ ^[0-9]+$ && "$font" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
  echo "::error::Invalid responsive matrix parameters"
  exit 2
fi

root="build/responsive/$profile"
mkdir -p "$root/screenshots"
export QA_WIDTH="$width"
export QA_HEIGHT="$height"
export QA_DENSITY="$density"
export QA_FONT_SCALE="$font"
export QA_OUTPUT_DIR="$root/screenshots"
export QA_ERROR_LOG="$root/screenshot-errors.log"
export QA_CAPTURE_ACCESSIBILITY=1

# Capture continuous logcat before the first screen to distinguish renderer,
# system_server and low-memory crashes from UI layout failures.
( adb logcat -v threadtime || true ) > "$root/live-logcat.txt" 2>&1 &
live_log_pid=$!
trap 'kill "$live_log_pid" 2>/dev/null || true' EXIT

# A real emulator takes the requested px/density and font scale. Screens
# 01–20 must each launch and produce a unique screenshot plus UI hierarchy.
set +e
bash ci/capture_screenshots.sh 2>&1 | tee "$root/capture-first-attempt.log"
capture_status=${PIPESTATUS[0]}
# GitHub AVDs have intermittently lost ADB before the *first* capture on
# landscape-640. Permit one genuine reboot-of-ADB retry only when there are
# no screenshots to validate. A failed app/UI screen remains a hard failure
# if the retry does not produce all twenty real accessibility-checked frames.
captured_count="$(find "$QA_OUTPUT_DIR" -maxdepth 1 -name '*.png' | wc -l)"
if [ "$capture_status" -ne 0 ] && [ "$captured_count" -eq 0 ]; then
  echo "::warning::Emulator startup failed before screen 01; restarting ADB once"
  timeout 10s adb kill-server || true
  timeout 15s adb start-server || true
  timeout 45s adb wait-for-device || true
  sleep 8
  bash ci/capture_screenshots.sh 2>&1 | tee "$root/capture-retry.log"
  capture_status=${PIPESTATUS[0]}
fi
python3 ci/verify_responsive_capture.py \
  "$QA_OUTPUT_DIR" "$profile" "$width" "$height" "$density" "$font"
verify_status=$?
set -e
timeout 12s adb shell wm size > "$root/actual-size.txt" || true
timeout 12s adb shell wm density > "$root/actual-density.txt" || true
timeout 12s adb shell settings get system font_scale > "$root/actual-font.txt" || true
timeout 12s adb logcat -d -v time -t 1000 > "$root/android-log.txt" || true
if [ "$capture_status" -ne 0 ] || [ "$verify_status" -ne 0 ]; then
  echo "::error::Responsive QA failed for $profile: capture=$capture_status, "
  echo "::error::audit=$verify_status. See artifacts for genuine frames and logs."
  exit 1
fi
echo "Responsive QA passed: $profile, 20 genuinely rendered screens"
