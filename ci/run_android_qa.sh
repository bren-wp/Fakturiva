#!/usr/bin/env bash
set -euo pipefail
# emulator-runner@v2 runs each YAML line in a separate sh process. This
# real Bash entrypoint preserves exit statuses across all QA phases.
mkdir -p build docs/screenshots
# Never upload stale screenshots checked into the repository if capture
# was skipped or failed. This only affects the runner's disposable checkout.
rm -f docs/screenshots/*.png

set +e
gradle :app:connectedDebugAndroidTest --rerun-tasks --stacktrace
gradle_status=$?
set -e

# Capture test-process crash details before screen capture clears logcat.
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

# Even when instrumented tests fail, attempt genuine screenshot capture.
# The job remains RED; the separate artifact supports iterative pixel QA.
set +e
bash ci/capture_screenshots.sh
capture_status=$?
set -e

if [ "$report_status" -ne 0 ]; then
  echo "::error::Instrumentation tests did not pass"
fi
if [ "$capture_status" -ne 0 ]; then
  echo "::error::Twenty genuine screenshots were not captured"
fi
if [ "$report_status" -ne 0 ] || [ "$capture_status" -ne 0 ]; then
  exit 1
fi
