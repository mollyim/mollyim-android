# Changelog

All notable changes to Signal will be documented in this file.

<!-- ## Types of changes
- Added: for new features.
- Changed: for changes in existing functionality.
- Deprecated: for soon-to-be removed features.
- Removed: for now removed features.
- Fixed: for any bug fixes.
- Merged: for code merged from upstream. -->

[Unreleased]: https://github.com/mollyim/mollyim-android/compare/v4.53.7...HEAD
## [Unreleased]

### Added

- Passphrase lock.
- Encrypted shared preferences.
- Associated data to MasterCipher's authenticated encryption.
- Wipe free memory on lock to clear secrets.
- Option to disable logging in advanced settings.
- Early redact debug logs.
- Gradle build scan plugin.

### Changed

- App renamed to Molly.
- Purple color palette.
- PBKDF2 key derivation function by Argon2.
- Local storage encryption to 256-bits.
- Apkdiff tool to support multiple signature files.
- Shrink resources on release build.
- Use flavor dimension for staging build.

### Removed

- SMS/MMS integration.
- Google Play flavor.
- Google Play app rating prompt.
- Build expiration age check.
- Automatic APK updates.
- Deprecated classic encryption and legacy code.

### Fixed

- Race condition at KeyStore creation.
- Fix lock alarm in low-power idle mode.

### Merged

- Signal v4.53.7

