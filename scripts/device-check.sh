#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-evidence
collect_logs() {
  adb shell dumpsys package com.sina.uninotes.debug > device-evidence/package.txt || true
  adb shell dumpsys package com.sina.uninotes > device-evidence/release-package.txt || true
  adb logcat -d -s AndroidRuntime > device-evidence/crashes.txt || true
}
trap collect_logs EXIT
app_apk=$(find apks -name app-debug.apk -print -quit)
test_apk=$(find apks -name '*androidTest.apk' -print -quit)
adb install -r "$app_apk"
adb install -r "$test_apk"
for mode in threebutton gestural; do
  adb shell cmd overlay enable-exclusive --category "com.android.internal.systemui.navbar.$mode"
  adb shell am instrument -w com.sina.uninotes.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "device-evidence/instrumentation-$mode.txt"
  adb pull /sdcard/Android/data/com.sina.uninotes.debug/files/screenshots "device-evidence/$mode"
  grep -Eq 'OK \([1-9][0-9]* tests?\)' "device-evidence/instrumentation-$mode.txt"
done
if [ -d release-apks ]; then
  release_apk=$(find release-apks -name '*.apk' -print -quit)
  test -n "$release_apk"
  adb install -r "$release_apk" | tee device-evidence/release-install.txt
  adb shell am start -W -n com.sina.uninotes/com.sina.uninotes.MainActivity | tee device-evidence/release-launch.txt
  grep -q 'Status: ok' device-evidence/release-launch.txt
  adb shell pidof com.sina.uninotes > device-evidence/release-pid.txt
  adb exec-out screencap -p > device-evidence/release-home.png
fi
collect_logs
python3 - <<'PY'
from pathlib import Path
import re
log = Path('device-evidence/crashes.txt').read_text()
fatal_blocks = log.split('FATAL EXCEPTION:')[1:]
if any(re.search(r'Process: com\.sina\.uninotes(?:\.debug)?(?=[,:\s])', block) for block in fatal_blocks):
    raise SystemExit('UniNotes crashed; see device-evidence/crashes.txt')
PY
