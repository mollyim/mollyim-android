#!/usr/bin/env bash
set -euo pipefail

apk=${1:?Signed Memoly APK path is required}
# Preserve PackageManager's actual INSTALL_FAILED_* response in the job log.
adb install --no-streaming "$apk"
adb shell pm path im.memoly.app | grep -q '^package:'
adb logcat -c
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p im.memoly.app
sleep 10
if ! adb shell pidof im.memoly.app; then
  adb logcat -d -b crash
  echo 'Memoly process did not survive first launch.' >&2
  exit 1
fi
adb install --no-streaming -r "$apk"
echo 'Fresh install, launcher startup and same-version reinstall passed.'
