# Changelog

All notable changes to Signal will be documented in this file.

<!-- ## Types of changes
- Added: for new features.
- Changed: for changes in existing functionality.
- Deprecated: for soon-to-be removed features.
- Removed: for now removed features.
- Fixed: for any bug fixes.
- Merged: for code merged from upstream. -->

[v4.55.8-1]: https://github.com/mollyim/mollyim-android/compare/v4.55.8...HEAD
## [v4.55.8-1]

### Added

- Passphrase lock.
- Passphrase strength estimator library.
- Argon2 benchmark function.
- Encrypted shared preferences.
- Associated data to MasterCipher encryption.
- RAM wipe service to clear sensitive data on lock.
- Early redact log messages.
- Option in advanced preferences to disable logs.
- Gradle build scan plugin.

### Changed

- App renamed to Molly.
- Purple color palette.
- Local storage encryption upgraded to 256-bits.
- Apkdiff tool to support multiple signature files.
- Shrink resources on release build.
- Use flavor dimension for staging build.

### Removed

- SMS/MMS integration.
- Intel x86 32-bit support.
- Google Play flavor.
- Google Play app rating prompt.
- Build expiration age check.
- Automatic APK updates.
- Deprecated classic encryption and legacy code.

### Fixed

- Race condition at KeyStore creation.
- Fix lock alarm in low-power idle mode.

### Merged

- Signal v4.55.8

