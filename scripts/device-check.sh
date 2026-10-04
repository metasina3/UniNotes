#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-evidence
app_apk=$(find apks -name app-debug.apk -print -quit)
test_apk=$(find apks -name '*androidTest.apk' -print -quit)
adb install -r "$app_apk"
adb install -r "$test_apk"
for mode in threebutton gestural; do
  adb shell cmd overlay enable --exclusive --category "com.android.internal.systemui.navbar.$mode"
  adb shell am instrument -w com.sina.uninotes.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "device-evidence/instrumentation-$mode.txt"
  grep -Eq 'OK \([1-9][0-9]* tests?\)' "device-evidence/instrumentation-$mode.txt"
  adb pull /sdcard/Android/data/com.sina.uninotes.debug/files/screenshots "device-evidence/$mode"
done
adb shell dumpsys package com.sina.uninotes.debug > device-evidence/package.txt
adb logcat -d -s AndroidRuntime > device-evidence/crashes.txt
if grep -q 'FATAL EXCEPTION' device-evidence/crashes.txt; then exit 1; fi
