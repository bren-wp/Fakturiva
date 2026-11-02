#!/usr/bin/env bash
set -euo pipefail
# android-emulator-runner@v2 runs YAML lines as separate shell processes.
# One real Bash entrypoint preserves all QA exit codes.
mkdir -p build docs/screenshots
# Clear stale screenshots only in the ephemeral GitHub Actions checkout.
rm -f docs/screenshots/*.png

# Capture actual APK UI *before* the intensive integration tests.
# The PDF/backup tests can exhaust the emulator process or leave ADB unstable.
# This order preserves evidence even if instrumentation subsequently fails.
set +e
bash ci/capture_screenshots.sh
capture_status=$?
set -e

# Reset logcat so test crashes remain distinct from screen-capture errors.
adb logcat -c || true
set +e
gradle :app:connectedDebugAndroidTest --rerun-tasks --stacktrace
gradle_status=$?
set -e
adb logcat -d -v time > build/instrumentation.log || true

report_status=1
if [ "$gradle_status" -eq 0 ]; then
  set +e
  python3 ci/verify_instrumentation.py
  report_status=$?
  set -e
else
  echo "::error::Instrumentation Gradle task exited with $gradle_status"
fi

if [ "$capture_status" -ne 0 ]; then
  echo "::error::Twenty genuine Android screenshots were not captured"
fi
if [ "$report_status" -ne 0 ]; then
  echo "::error::Instrumented regression tests did not pass"
fi
if [ "$capture_status" -ne 0 ] || [ "$report_status" -ne 0 ]; then
  exit 1
fi
echo "Verified: 20 real screenshots and all Android instrumentation tests"
