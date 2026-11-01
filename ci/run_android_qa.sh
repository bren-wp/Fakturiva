#!/usr/bin/env bash
set -euo pipefail
# android-emulator-runner@v2 executes each YAML script line in a separate
# /usr/bin/sh -c process. Put control flow in one real Bash script.
mkdir -p build
set +e
gradle :app:connectedDebugAndroidTest --rerun-tasks --stacktrace
test_status=$?
set -e

# Preserve the test-process crash *before* screenshot capture clears logcat.
adb logcat -d -v time > build/instrumentation.log || true
if [ "$test_status" -ne 0 ]; then
  echo "::error::Android instrumentation Gradle task failed (exit $test_status)"
  exit "$test_status"
fi

# A green Gradle task is NOT sufficient: reject stale/missing test reports,
# crashes, failures, skipped tests and suspiciously few executed tests.
python3 ci/verify_instrumentation.py

# Only genuine passing tests allow capturing the twenty UI screens.
bash ci/capture_screenshots.sh
