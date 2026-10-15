#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_HOME:?Android SDK path is required}"
: "${BUILD_TOOLS_VERSION:?Build tools version is required}"
: "${EXPECTED_CERT_SHA256:?Expected signing certificate SHA-256 is required}"

new_apk=${1:?New APK path is required}
old_apk=${2:-}
tools="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"
expected=$(printf '%s' "$EXPECTED_CERT_SHA256" | tr '[:upper:]' '[:lower:]')
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo 'Invalid expected certificate digest.' >&2; exit 1; }

metadata() {
  local apk=$1 badging signature package version certificate
  signature=$("$tools/apksigner" verify --print-certs "$apk") || {
    echo "APK signature verification failed: $apk" >&2
    return 1
  }
  certificate=$(printf '%s\n' "$signature" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | tr '[:upper:]' '[:lower:]')
  [[ "$certificate" =~ ^[0-9a-f]{64}$ && "$certificate" == "$expected" ]] || {
    echo 'APK was not signed with the configured personal key.' >&2
    return 1
  }
  if [[ "$signature" == *'Signer #2 certificate SHA-256 digest:'* ]]; then
    echo 'Expected one APK signer.' >&2
    return 1
  fi
  badging=$("$tools/aapt" dump badging "$apk")
  package=$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
  version=$(printf '%s\n' "$badging" | sed -n "s/^package:.* versionCode='\([0-9]*\)'.*/\1/p")
  [[ "$package" == im.memoly.app && "$version" =~ ^[1-9][0-9]*$ ]] || {
    echo 'Expected im.memoly.app with a valid versionCode.' >&2
    return 1
  }
  if [[ "$badging" == *application-debuggable* ]]; then
    echo 'Refusing to release a debuggable APK.' >&2
    return 1
  fi
  if ! printf '%s\n' "$badging" | grep -qx "application-label:'Memoly'"; then
    echo 'Expected Memoly application label.' >&2
    return 1
  fi
  printf '%s\n' "$version"
}

new_version=$(metadata "$new_apk")
if [[ -n "$old_apk" ]]; then
  old_version=$(metadata "$old_apk")
  if (( new_version <= old_version )); then
    echo "versionCode must increase: previous=$old_version new=$new_version. Update currentHotfixVersion or the upstream version." >&2
    exit 1
  fi
fi
printf 'Verified personal release: package=im.memoly.app versionCode=%s certificate=%s\n' "$new_version" "$expected"
