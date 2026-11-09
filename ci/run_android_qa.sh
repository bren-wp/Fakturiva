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
# Screenshot QA must use the same accessibility checks as the responsive
# matrix. Previously the API 36 workflow could report success while its first
# PNG contained Android's "Viewing full screen" tutorial over the Splash.
export QA_CAPTURE_ACCESSIBILITY=1
set +e
bash ci/capture_screenshots.sh
capture_status=$?
if [ "$capture_status" -eq 0 ]; then
  # A generated PNG alone is insufficient: verify the app's actual screen
  # headings, accessibility tree and clickable bounds for all 20 views.
  python3 ci/verify_responsive_capture.py \
    docs/screenshots "android-36-screens" 1179 2556 480 1.0
  capture_status=$?
fi
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
  # Print bounded failure summaries so a red PR shows the *actual* failing
  # Android instrumented test, not just a Gradle exit code. No customer data.
  find app/build/outputs/androidTest-results/connected -type f -name '*.xml' 2>/dev/null |
    while IFS= read -r report; do
      echo "Android test results: $report"
      grep -E '<testsuite |<testcase |<failure |<error ' "$report" | head -n 70 || true
    done
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
