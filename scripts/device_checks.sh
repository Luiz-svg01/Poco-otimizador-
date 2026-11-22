#!/usr/bin/env bash
set -euo pipefail
mkdir -p diagnostics screenshots
capture() {
  adb logcat -d -v threadtime > diagnostics/full-logcat.txt || true
  adb logcat -b crash -d -v threadtime > diagnostics/crash-buffer.txt || true
  adb shell dumpsys activity activities > diagnostics/activities.txt || true
}
trap capture EXIT
python3 scripts/smoke_apk.py dist/Poco-Optimizer-0.1.1-test.apk diagnostics/distribution | tee diagnostics/distribution-smoke.txt
./gradlew --no-daemon :app:connectedValidationAndroidTest
for screen in dashboard cleaner security; do
  adb exec-out run-as br.com.pocooptimizer.validation cat "files/screenshots/$screen.png" > "screenshots/$screen.png"
done
