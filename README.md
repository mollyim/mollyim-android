# Polly

This is a minimalist flavour of [Molly](https://github.com/mollyim/mollyim-android). Its aim is to provide a better experience by strictly removing features instead of adding new ones. This flavour will not be published to any app stores and its users are expected to use [Obtanium](https://github.com/ImranR98/Obtainium) or similar to keep it updated.

## Features

- remove donation, auto-update, help links and stories from the Molly UI
- different Android package id to allow running it side-by-side with Molly
- remove FCM for the list of available notification services
- only release the arm64-v8a ABI variant for modern devices (reduces APK size by ~40MB)
- raising the SDK minimum to Android 14 (API 34) for security and to eliminate legacy code paths

### Why?

- A more minimalistic UI.
- Users that run Signal + Molly in different profiles could use this fork instead of Signal for the advantages of having Molly.
- FCM was removed in favor of privacy-focused alternatives. For those who prefer FCM, the official Signal app may be a better fit. UnifiedPush provides the most privacy-respecting push notification solution while remaining battery-efficient.
- arm64-v8a is the standard CPU architecture for modern devices. Focusing on this single architecture reduces the APK size from ~86MB to ~66MB.
- Raising the minimum SDK to Android 14 helps remove legacy compatibility code from the upstream Signal codebase.

## Releases

This fork checks for new Molly releases every 6 hours. If it finds a new release it automatically rebases this fork's code and creates a pull request. In the rare event that there is a rebase conflict, the workflow fails and requires manual intervention.

Immutable releases are enabled for this repo meaning that any released .apks cannot be modified.

### APK Signature Verification

All releases are signed with the same key. Verify the signature fingerprints:

```text
SHA-256: EE:A9:BD:41:4B:59:57:3D:EF:9A:5D:41:41:E3:8A:61:BC:54:B4:02:FA:89:3A:61:53:A5:48:67:A7:18:EC:2B
```
