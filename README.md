# Molly

[![Test](https://github.com/mollyim/mollyim-android/workflows/Test/badge.svg)](https://github.com/mollyim/mollyim-android/actions)
[![Reproducible build](https://github.com/mollyim/mollyim-android/actions/workflows/reprocheck.yml/badge.svg)](https://github.com/mollyim/mollyim-android/actions/workflows/reprocheck.yml)
[![Translation status](https://hosted.weblate.org/widgets/molly-instant-messenger/-/svg-badge.svg)](https://hosted.weblate.org/engage/molly-instant-messenger/?utm_source=widget)
[![Financial contributors](https://opencollective.com/mollyim/tiers/badge.svg)](https://opencollective.com/mollyim#category-CONTRIBUTE)

Molly is a hardened version of [Signal](https://github.com/signalapp/Signal-Android) for Android, the fast simple yet secure messaging app by [Signal Foundation](https://signal.org).

## Introduction

Back in 2018, Signal allowed the user to set a passphrase to secure the local message database. But this option was removed with the introduction of file-based encryption on Android. Molly brings it back again with additional security features.

Molly connects to Signal's servers, so you can chat with your Signal contacts seamlessly. Before signing up, please remember to review the [Signal Terms & Privacy Policy](https://signal.org/legal/).

We update Molly every two weeks to include the latest Signal features and fixes. The exceptions are security patches, which are applied as soon as they are available.

## Download

You can download the app from GitHub's [Releases](https://github.com/mollyim/mollyim-android/releases/latest) page or install it from the [Molly F-Droid Repo](https://molly.im/fdroid/):

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://molly.im/fdroid/)

There are two versions available: **Molly** or **Molly-FOSS**. Learn the differences [below](#free-and-open-source) and download the right one for you.

You can also get **Molly-FOSS** from [Accrescent](https://accrescent.app/):

<a href="https://accrescent.app/app/im.molly.app">
   <img alt="Get it on Accrescent"
      src="https://accrescent.app/badges/get-it-on.png"
      height="80">
</a>

To [verify](https://developer.android.com/studio/command-line/apksigner#usage-verify) the APK, use the following signing certificate fingerprints:
```
SHA-256: 6aa80fdf4a8cc13737cfb434fc0cde486f09cf8fcda21a67bea5ee1ca2700886
SHA-1: 49ce310cdd0c09c8c34eb31a8005c6bf13f5a4f1
```

## Features

Molly has unique features compared to Signal:

- **Data encryption at rest** - Protect your app database with [passphrase encryption](https://github.com/mollyim/mollyim-android/wiki/Data-Encryption-At-Rest)
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

Molly is open-source just like Signal. But Signal depends on proprietary Google software for some features.

To support a 100% free and auditable app, Molly comes in two versions: one with proprietary blobs like Signal, and one without. They are called Molly and Molly-FOSS, respectively. You can install the flavor of your choice at any time, and it will replace any previously installed version. The data and settings will be preserved so that you do not have to re-register.

### Feature Comparison

Here's how some key features work in different versions of the app:

| Feature                           | Molly-FOSS       | Molly                | Signal               |
| --------------------------------- | ---------------- | -------------------- | -------------------- |
| Push notifications <sup>(1)</sup> | ✔ WebSocket<br>✔ UnifiedPush | ⚠ FCM<br>✔ WebSocket<br>✔ UnifiedPush | ⚠ FCM<br>✔ WebSocket |
| Location sharing                 | ✔ OpenStreetMap  | ⚠ Google Maps        | ⚠ Google Maps        |

<sup>(1)</sup> You might need to turn off system-level battery restrictions for the app to receive messages when the app isn't open.

### UnifiedPush

[UnifiedPush](https://unifiedpush.org/) is an open standard for delivering push notifications, offering a privacy-friendly alternative to Google's proprietary FCM service. It allows users to choose their own notification distributor.

> [!IMPORTANT]
> To use UnifiedPush notifications, you need access to a [MollySocket](https://github.com/mollyim/mollysocket) server to link your Signal account to UnifiedPush. You can either run MollySocket on a server you control (strongly advised) or use a public instance.

Currently, UnifiedPush is unavailable for linked devices.

## Compatibility with Signal

Molly and Signal apps can be installed on the same device. If you need a second number for messaging, you can register Molly with a different number while keeping Signal active. Any phone number capable of receiving SMS or calls can be used during registration.

If you wish to use the same phone number for both Molly and Signal, you must register Molly as a linked device. Registering the same number independently on both apps will result in only the most recently registered app staying active, while the other will go offline.

For Signal users looking to switch to Molly without changing the phone number, please refer to the [Migrating From Signal](https://github.com/mollyim/mollyim-android/wiki/Migrating-From-Signal) guide on the wiki.

## Backups

Backups are fully compatible. Signal [backups](https://support.signal.org/hc/en-us/articles/360007059752-Backup-and-Restore-Messages) can be restored in Molly, and the other way around, simply by choosing the backup folder and file. However, to import a backup from Signal, you must use a matching or newer version of Molly.

## Feedback

- [Submit bugs and feature requests](https://github.com/mollyim/mollyim-android/issues) on GitHub
- Join us at [#mollyim:matrix.org](https://matrix.to/#/#mollyim:matrix.org) on Matrix
- For news, tips, and tricks, follow [@mollyim](https://fosstodon.org/@mollyim) on Mastodon

## Reproducible Builds

Molly supports reproducible builds, so that anyone can run the build process to reproduce the same APK as the original release.

Please check the guide in the [reproducible-builds](https://github.com/mollyim/mollyim-android/blob/master/reproducible-builds) directory.

## Changelog

See the [Changelog](https://github.com/mollyim/mollyim-android/wiki/Changelog) to view recent changes.

## License

Licensed under the [GNU AGPLv3](https://www.gnu.org/licenses/agpl-3.0.html).

Original license and export notices in the [original README](README-ORIG.md).

## Acknowledgements

Thanks to the following organizations for supporting the **Molly** project.

<div align="center">
<table>
<tr>
  <td>
    <a href="https://nlnet.nl/" target="_blank">
      <img src="https://nlnet.nl/logo/banner.svg" alt="NLnet logo" height="60" />
    </a>
  </td>
  <td>
    <a href="https://www.jetbrains.com/community/opensource/" target="_blank">
      <img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jetbrains.svg" alt="JetBrains logo" height="32" />
    </a>
  </td>
</tr>
</table>
</div>

## Legal Notice

This project is *NOT* affiliated with Signal Messenger or the Signal Foundation.

The software is developed independently and provided as-is, without warranties of any kind. Use at your own risk.
