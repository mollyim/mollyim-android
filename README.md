# Molly

[![Test](https://github.com/mollyim/mollyim-android/workflows/Test/badge.svg)](https://github.com/mollyim/mollyim-android/actions)
[![Translation status](https://hosted.weblate.org/widgets/molly-instant-messenger/-/svg-badge.svg)](https://hosted.weblate.org/engage/molly-instant-messenger/?utm_source=widget)
[![Financial contributors](https://opencollective.com/mollyim/tiers/badge.svg)](https://opencollective.com/mollyim#category-CONTRIBUTE)

Molly is a hardened version of [Signal](https://github.com/signalapp/Signal-Android) for Android, the fast simple yet secure messaging app by [Signal Foundation](https://signal.org).

## Introduction

Back in 2018, Signal allowed the user to set a passphrase to secure the local message database. But this option was removed with the introduction of file-based encryption on Android. Molly brings it back again with additional security features.

Molly connects to the Signal server, so you can chat with your Signal contacts seamlessly. Please remember to review the [Signal Terms & Privacy Policy](https://signal.org/legal/) before signing up.

We update Molly every two weeks to include the latest features and bug fixes from Signal. The exceptions are security issues, which are patched as soon as fixes become available.

## Download

You can download the app from GitHub's [Releases](https://github.com/mollyim/mollyim-android/releases/latest) page or install it from the [Molly F-Droid Repo](https://molly.im/fdroid/):

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://molly.im/fdroid/)

There are two flavors to choose from download: **Molly** or **Molly-FOSS**. Learn the differences [below](#free-and-open-source) and download the right one for you.

You can also download **Molly-FOSS** from [Accrescent](https://accrescent.app/):

<a href="https://accrescent.app/app/im.molly.app">
   <img alt="Get it on Accrescent"
      src="https://accrescent.app/badges/get-it-on.png"
      height="80">
</a>

Signing certificate fingerprints to [verify](https://developer.android.com/studio/command-line/apksigner#usage-verify) the APK:
```
SHA-256: 6aa80fdf4a8cc13737cfb434fc0cde486f09cf8fcda21a67bea5ee1ca2700886
SHA-1: 49ce310cdd0c09c8c34eb31a8005c6bf13f5a4f1
```

To explore latest experimental features, consider **Molly Insider**, our dedicated app for beta testing. Find out more here: [Molly Insider on GitHub](https://github.com/mollyim/mollyim-insider-android).

## Features

Molly has unique features compared to Signal:

- **Data encryption at rest** - Protect the database with [passphrase encryption](https://github.com/mollyim/mollyim-android/wiki/Data-Encryption-At-Rest)
- **Secure RAM wiper** - Securely shred sensitive data from device memory
- **Automatic lock** - Lock the app automatically under certain conditions
- **Multi-device support** -- Link multiple devices, including Android tablets, to a single account
- **Block unknown contacts** - Block messages and calls from unknown senders for security and anti-spam
- **Disappearing call history** - Clear call notifications together with expiring messages
- **Debug logs are optional** - Android logging can be disabled
- **Custom backup scheduling** - Choose between a daily or weekly interval and the number of backups to retain
- **SOCKS proxy and Tor support** - Tunnel app network traffic via proxy and Orbot

For the [UnifiedPush](#unifiedpush) version of Molly, the following features are additionally available:

- **UnifiedPush support**: Receive push notifications through the UnifiedPush protocol

Besides that, you will find all the features of Signal plus some minor tweaks and improvements. As with Signal, SMS is not supported.  

## Free and Open-Source

Molly is open-source just like Signal. But Signal uses Google's proprietary software to provide some key features.

To support a 100% free and auditable app, Molly comes in two flavors: one with proprietary blobs like Signal and one without. They are called Molly and Molly-FOSS, respectively. You can install the flavor of your choice at any time, and it will replace any previously installed version. The data and settings will be preserved so that you do not have to re-register.

### UnifiedPush

[Molly-UP](https://github.com/mollyim/mollyim-android-unifiedpush) is a separate app based on Molly-FOSS. It incorporates the ability to receive notifications through a UnifiedPush provider.

> [!IMPORTANT]
> Molly-UP **requires** an instance of [mollysocket](https://github.com/mollyim/mollysocket) to work with a UnifiedPush provider. This can be done on a machine you control.

If Molly-UP is set up as a secondary linked device, UnifiedPush notifications will not be available.

### Dependency Comparison

This table lists the current status of the dependencies:

| Feature                               | Molly-FOSS       | Molly-UP        | Molly                | Signal               |
| ------------------------------------- | ---------------- | --------------- | -------------------- | -------------------- |
| Push notifications <sup>(1) (2)</sup> | ✔ Websocket | ✔ UnifiedPush<br>✔ Websocket | ⚠ FCM<br>✔ Websocket | ⚠ FCM<br>✔ Websocket |
| Location provider                     | ✔ OpenStreetMap | ✔ OpenStreetMap | ⚠ Google Maps        | ⚠ Google Maps        |

<sup>(1)</sup> You may need to disable the system battery optimizations to receive Websocket-based push notifications in background.<br>
<sup>(2)</sup> If you are running a custom operating system and the app fails to register with Play Services (FCM) try the FOSS flavor.

## Compatibility with Signal

Molly and Signal apps can be installed on the same device. If you need a 2nd number to chat, you can use Molly along with Signal.

However, you cannot use the same phone number on both apps at the same time. Only the last app registered will remain active, and the other will go offline. Remember that you are not limited to use only your main phone number - you can use any number on which you can receive SMS or phone calls at registration.

If you are currently a Signal user and want to use Molly instead of Signal
(with the same phone number), see [Migrating From
Signal](https://github.com/mollyim/mollyim-android/wiki/Migrating-From-Signal)
on the wiki.

## Backups

Backups are fully compatible. Signal [backups](https://support.signal.org/hc/en-us/articles/360007059752-Backup-and-Restore-Messages) can be restored in Molly, and the other way around, simply by choosing the backup folder and file. However, to import a backup from Signal you must use a matching or newer version of Molly.

For older releases of Android, you might need to rename the backup file and copy it into the expected path, so the app can find the backup to restore during installation. These are the locations within internal storage where backups are written by default:
- `Signal/Backups/Signal-year-month-date-time.backup`
- `Molly/Backups/Molly-year-month-date-time.backup`

## Feedback

- [Submit bugs and feature requests](https://github.com/mollyim/mollyim-android/issues)
- Join us at [#mollyim:matrix.org](https://matrix.to/#/#mollyim:matrix.org) on Matrix
- For news, tips and tricks follow [@mollyim](https://fosstodon.org/@mollyim) on Mastodon

## Reproducible Builds

Molly supports reproducible builds, so that anyone can run the build process to reproduce the same APK as the original release.

Please check the guide in the [reproducible-builds](https://github.com/mollyim/mollyim-android/blob/master/reproducible-builds) directory.

## Changelog

See the [Changelog](https://github.com/mollyim/mollyim-android/wiki/Changelog) to view recent changes.

## License

License and legal notices in the original [README](README-ORIG.md).

## Disclaimer

This project is *NOT* sponsored by Signal Messenger or Signal Foundation.

The software is produced independently of Signal and carries no guarantee about quality, security or anything else. Use at your own risk.
