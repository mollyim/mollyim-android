# Changelog

All notable changes to Signal will be documented in this file.

<!-- ## Types of changes
- Added: for new features.
- Changed: for changes in existing functionality.
- Deprecated: for soon-to-be removed features.
- Removed: for now removed features.
- Fixed: for any bug fixes.
- Merged: for code merged from upstream. -->

## [Unreleased]

### Added

- Option to not show v2 PIN reminders. (#5)

## [v4.55.8-2]

### Fixed

- Megaphones not displayed after unlocking.

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
- Hidden notifications, screen security, and incognito keyboard enabled by default.
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

- Race condition at KeyStore creation. ([#9019a2e])
- Fix lock alarm in low-power idle mode. ([#7e0954d])

### Merged

- Signal v4.55.8

[Unreleased]: https://github.com/mollyim/mollyim-android/compare/v4.55.8-2...HEAD
[v4.55.8-2]: https://github.com/mollyim/mollyim-android/compare/v4.55.8-1...v4.55.8-2
[v4.55.8-1]: https://github.com/mollyim/mollyim-android/compare/v4.55.8...v4.55.8-1

[#9019a2e]: https://github.com/mollyim/mollyim-android/commit/9019a2e931779b06a241768836ce11031bd043de
[#7e0954d]: https://github.com/mollyim/mollyim-android/commit/7e0954d967c4210ae002bc0bbec83717a0ad8607
