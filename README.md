# Ghostly

[![Test](https://github.com/RedMSG/RedTech.molly/workflows/Test/badge.svg)](https://github.com/RedMSG/RedTech.molly/actions)
[![Reproducible build](https://github.com/RedMSG/RedTech.molly/actions/workflows/reprocheck.yml/badge.svg)](https://github.com/RedMSG/RedTech.molly/actions/workflows/reprocheck.yml)
[![Translation status](https://hosted.weblate.org/widgets/redtech-molly/-/svg-badge.svg)](https://hosted.weblate.org/engage/redtech-molly/?utm_source=widget)
[![Financial contributors](https://opencollective.com/redtech-molly/tiers/badge.svg)](https://opencollective.com/redtech-molly#category-CONTRIBUTE)
[![Cloudsmith](https://img.shields.io/badge/OSS%20hosting%20by-cloudsmith-blue?logo=cloudsmith&style=flat-square)](https://cloudsmith.com)

**Current release: v8.19.2-4 (versionCode 171904)**

Ghostly is a hardened version of [Signal](https://github.com/signalapp/Signal-Android) for Android, the fast simple yet secure messaging app by [Signal Foundation](https://signal.org).

## Introduction

Ghostly is a customized privacy hardend fork of Signal. 
Ghostly is integrated with end to end encryption and prioritize privacy with additional security features.

Ghostly connects to Signal's servers, so you can chat with your Signal contacts seamlessly. Before signing up, please remember to review the [Signal Terms & Privacy Policy](https://signal.org/legal/).

We update Ghostly App. regularly to include the latest Signal features and fixes. The exceptions are security patches, which are applied as soon as they are available.

## Download

You can download the app from GitHub's [Releases](https://github.com/RedMSG/RedTech.molly/releases/latest) page.


*Note*: **Ghostly** or **Ghostly-App**. can only be downloaded from Github.


To [verify](https://developer.android.com/studio/command-line/apksigner#usage-verify) the APK, use the following signing certificate fingerprints:
```
SHA-256: <REPLACE_WITH_GHOSTLY_SHA256_FINGERPRINT>
SHA-1: <REPLACE_WITH_GHOSTLY_SHA1_FINGERPRINT>
```

## Features

Ghostly has unique features compared to Signal:

- **Data encryption at rest** - Protect your app database with [passphrase encryption](https://github.com/RedMSG/RedTech.molly/wiki/Data-Encryption-At-Rest)
- **Secure RAM wiper** - Securely shred sensitive data from device memory
- **Automatic lock** - Lock the app automatically under user-defined conditions
- **Multi-device support** - Link multiple devices to a single Signal account, including Android tablets
- **UnifiedPush** - Receive push notifications without Google through the UnifiedPush protocol
- **Block unknown contacts** - Block messages and calls from unknown senders for security and anti-spam
- **Disappearing call history** - Clear call logs together with expiring messages
- **Custom backup scheduling** - Set daily or weekly interval and the number of backups to retain
- **SOCKS proxy and Tor support** - Tunnel app network traffic via proxy and Orbot
- **Debug logs are optional** - Android logging can be disabled

Additionally, you will find all the features of Signal, along with some minor tweaks and improvements.

## Free and Open-Source

Ghostly is open-source just like Signal. But Signal depends on proprietary Google software for some features.

To support a 100% free and auditable app, Ghostly comes in two versions: one with proprietary blobs like Signal, and one without. They are called Ghostly and Ghostly-FOSS, respectively. You can install the flavor of your choice at any time, and it will replace any previously installed version. The data and settings will be preserved so that you do not have to re-register.

### Feature Comparison

Here's how some key features work in different versions of the app:

| Feature                           | Ghostly-FOSS     | Ghostly               | Signal               |
| --------------------------------- | ---------------- | --------------------- | --------------------- |
| Push notifications <sup>(1)</sup> | ✔ WebSocket<br>✔ UnifiedPush | ⚠ FCM<br>✔ WebSocket<br>✔ UnifiedPush | ⚠ FCM<br>✔ WebSocket |
| Location sharing                 | ✔ OpenStreetMap  | ⚠ Google Maps        | ⚠ Google Maps        |

<sup>(1)</sup> You might need to turn off system-level battery restrictions for the app to receive messages when the app isn't open.

### UnifiedPush

[UnifiedPush](https://unifiedpush.org/) is an open standard for delivering push notifications, offering a privacy-friendly alternative to Google's proprietary FCM service. It allows users to choose their own notification distributor.

> [!IMPORTANT]
> To use UnifiedPush notifications, you need access to a [RedTechSocket](https://github.com/RedMSG/RedTechSocket) server to link your Signal account to UnifiedPush. You can either run RedTechSocket on a server you control (strongly advised) or use a public instance.

Currently, UnifiedPush is unavailable for linked devices.

## Compatibility with Signal

Ghostly and Signal apps can be installed on the same device. If you need a second number for messaging, you can register Ghostly with a different number while keeping Signal active. Any phone number capable of receiving SMS or calls can be used during registration.

If you wish to use the same phone number for both Ghostly and Signal, you must register Ghostly as a linked device. Registering the same number independently on both apps will result in only the most recently registered app staying active, while the other will go offline.

For Signal users looking to switch to Ghostly without changing the phone number, please refer to the [Migrating From Signal](https://github.com/RedMSG/RedTech.molly/wiki/Migrating-From-Signal) guide on the wiki.

## Backups

Backups are fully compatible. Signal [backups](https://support.signal.org/hc/en-us/articles/360007059752-Backup-and-Restore-Messages) can be restored in Ghostly, and the other way around, simply by choosing the backup folder and file. However, to import a backup from Signal, you must use a matching or newer version of Ghostly.

## Feedback

- [Submit bugs and feature requests](https://github.com/RedMSG/RedTech.molly/issues) on GitHub
- Join us at [#redtech-molly:matrix.org](https://matrix.to/#/#redtech-molly:matrix.org) on Matrix (via space: [#redtech-molly-space:matrix.org](https://matrix.to/#/#redtech-molly-space:matrix.org))
- For news, tips, and tricks, follow [@ghostly](https://fosstodon.org/@redtech) on Mastodon

## Reproducible Builds

Ghostly supports reproducible builds, so that anyone can run the build process to reproduce the same APK as the original release.

Please check the guide in the [reproducible-builds](https://github.com/RedMSG/RedTech.molly/blob/master/reproducible-builds) directory.

## Changelog

See the [Changelog](https://github.com/RedMSG/RedTech.molly/wiki/Changelog) to view recent changes.

**Latest version:** `8.19.2-4` &nbsp;·&nbsp; **versionCode:** `171904`

## License

Licensed under the GNU Affero General Public License, version 3 only
([`AGPL-3.0-only`](LICENSE)).

See [LEGAL.md](LEGAL.md) for legal and copyright information.

## Acknowledgements

Ghostly is an independent project built on code published by Signal and [Molly](https://github.com/mollyim/mollyim-android). We are deeply grateful to the Signal and Molly contributors for the work we build on.