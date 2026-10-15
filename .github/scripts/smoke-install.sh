#!/usr/bin/env bash
set -euo pipefail

apk=${1:?Signed Memoly APK path is required}
if [[ -n "${EXPECTED_ANDROID_API:-}" ]]; then
  actual_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
  [[ "$actual_api" == "$EXPECTED_ANDROID_API" ]] || {
    echo "Expected Android API $EXPECTED_ANDROID_API, got $actual_api" >&2
    exit 1
  }
fi
# Preserve PackageManager's actual INSTALL_FAILED_* response in the job log.
adb install --no-streaming "$apk"
adb shell pm path im.memoly.app | grep -q '^package:'
adb logcat -c
component=$(adb shell cmd package resolve-activity --brief --query-flags 0 \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p im.memoly.app | tr -d '\r' | tail -n 1)
[[ "$component" == im.memoly.app/* ]] || { adb shell dumpsys package im.memoly.app; exit 1; }
# Launchers resolve first, then start the explicit component. An implicit start
# incorrectly requires CATEGORY_DEFAULT, which launcher-only filters do not have.
adb shell am start -W -n "$component" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
sleep 10
if ! adb shell pidof im.memoly.app; then
  adb logcat -d -b crash
  echo 'Memoly process did not survive first launch.' >&2
  exit 1
fi
adb install --no-streaming -r "$apk"
echo 'Fresh install, launcher startup and same-version reinstall passed.'
