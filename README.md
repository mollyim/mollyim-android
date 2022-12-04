# Molly

[![Test](https://github.com/mollyim/mollyim-android/workflows/Test/badge.svg)](https://github.com/mollyim/mollyim-android/actions)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/mollyim/mollyim-android.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/mollyim/mollyim-android/alerts/)
[![Translation status](https://hosted.weblate.org/widgets/molly-instant-messenger/-/svg-badge.svg)](https://hosted.weblate.org/engage/molly-instant-messenger/?utm_source=widget)
[![Financial contributors](https://opencollective.com/mollyim/tiers/badge.svg)](https://opencollective.com/mollyim#category-CONTRIBUTE)

> Molly is a hardened version of [Signal](https://github.com/signalapp/Signal-Android) for Android, the fast simple yet secure messaging app by [Signal Foundation](https://signal.org).

## Introduction

Back in 2018, Signal allowed the user to set a passphrase to secure the local message database. But this option was removed with the introduction of file-based encryption on Android. Molly brings it back again with additional security features.

Molly connects to the Signal server, so you can chat with your Signal contacts seamlessly. Please remember to review their [Signal Terms & Privacy Policy](https://signal.org/legal/) before signing up.

We update Molly every two weeks to include the latest features and bug fixes from Signal. The exception are security issues, that are patched as soon as the fix become available.

## Download

You can download the app from GitHub's [Releases](https://github.com/mollyim/mollyim-android/releases/latest) page or install it from the [Molly F-Droid Repo](https://molly.im/fdroid/):

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://molly.im/fdroid/)

There are two flavors to choose from download: **Molly** or **Molly-FOSS**. Learn the differences [below](#free-and-open-source) and download the right one for you.

Signing certificate fingerprints to [verify](https://developer.android.com/studio/command-line/apksigner#usage-verify) the APK:
```
SHA-256: 6aa80fdf4a8cc13737cfb434fc0cde486f09cf8fcda21a67bea5ee1ca2700886
SHA-1: 49ce310cdd0c09c8c34eb31a8005c6bf13f5a4f1
```

Pre-releases are also available to download for development or beta testing.

## Features

Molly has unique features compared to Signal:

- **Data encryption at rest** - Protect the database with [passphrase encryption](https://github.com/mollyim/mollyim-android/wiki/Data-Encryption-At-Rest)
- **Secure RAM wiper** - Securely shred sensitive data from device memory
- **Automatic lock** - Lock the app automatically under certain conditions
- **Block unknown contacts** - Block messages and calls from unknown senders for security and anti-spam
- **Contact deletion** - Allows you to delete contacts and stop sharing your profile
- **Disappearing call history** - Clear call notifications together with expiring messages
- **Debug logs are optional** - Android logging can be disabled
- **Custom backup scheduling** - Choose between daily or weekly interval and the number of backups to retain
- **SOCKS proxy and Tor support** - Tunnel app network traffic via proxy and Orbot

Besides that, you will find all the features of Signal plus some minor tweaks and improvements. The only exception is the SMS integration, that is incompatible with Molly security enhancements.

## Free and Open-Source

Molly is open-source just like Signal. But Signal uses Google's proprietary software to provide some key features.

To support a 100% free and auditable app, Molly comes in two flavors: one with proprietary blobs as Signal and one without. They are called Molly and Molly-FOSS respectively. You can install the flavor of your choice at any time, and it will replace any previously installed version. The data and settings will be preserved so that you do not have to re-register.

This table lists the current status of the dependencies:

| Feature                               | Molly-FOSS  | Molly                | Signal               |
| ------------------------------------- | ----------- | -------------------- | -------------------- |
| Push notifications <sup>(1) (2)</sup> | ✔️ Websocket | ⚠️ FCM<br>✔️ Websocket | ⚠️ FCM<br>✔️ Websocket |
| Location provider                     | ✘           | ⚠️ Google Maps        | ⚠️️️ Google Maps        |

<sup>(1)</sup> You may need to disable the system battery optimizations to receive Websocket-based  push notifications in background.<br>
<sup>(2)</sup> If you are running a custom ROM and the app fails to register with Play Services (FCM) try the FOSS flavor.

## Compatibility with Signal

Molly and Signal apps can be installed on the same device. If you need a 2nd number to chat, you can use Molly along with Signal.

However, you cannot use the same phone number on both apps at the same time. Only the last app to register will remain active, and the other will go offline. Remember that you are not limited to use only your main phone number, but also any number on which you can receive SMS or phone calls at registration.

## Backups

Backups are fully compatible. Signal [backups](https://support.signal.org/hc/en-us/articles/360007059752-Backup-and-Restore-Messages) can be restored in Molly, and the other way around, simply by choosing the backup folder and file. To import from Signal use a matching or newer version of Molly.

For older releases of Android, you might need to rename the backup file and copy it into the expected path, so the app can find the backup to restore during installation. Those are the locations within internal storage where backups are written by default:
- `Signal/Backups/Signal-year-month-date-time.backup`
- `Molly/Backups/Molly-year-month-date-time.backup`

## Migrating from Signal

If you are currently using Signal and want to use Molly instead (with the same
phone number), follow these steps.

Note, the migration should be done when the available Molly version is equal to
or later than the currently installed Signal app version.

1. Install the [F-Droid](https://f-droid.org/) app, if it isn't already. Add
   one or both of the [Molly app
   repositories](https://molly.im/download/fdroid/) to F-Droid.
2. Verify your Signal backup passphrase. In the Signal app: **Settings** >
   **Chats** > **Chat backups** > **Verify backup passphrase**.
3. Optionally, put your phone offline (enable airplane mode or disable data
   services) until after Signal is uninstalled in step 5. This will prevent the
   possibility of losing any Signal messages that are received during or after
   the backup is created.
4. Create a Signal backup. In the Signal app, go to **Settings** > **Chats** >
   **Chat backups** > **Create backup**.
5. Uninstall the Signal app. Now you can put your phone back online (disable
   airplane mode or re-enable data services).
6. Install the Molly or Molly-FOSS app using F-Droid.
7. Open the Molly app. Enable database encryption if desired. As soon as the
   option is given, tap **Transfer or restore account**. Answer any permissions
   questions.
8. Choose to **Restore from backup** and tap **Choose backup**. Navigate to
   your Signal backup location (`Signal/Backups/`, by default) and choose the
   backup that was created in step 3.
9. Check the backup details and then tap **Restore backup** to confirm. Enter
   the backup passphrase when requested.
10. If asked, choose a new folder for backup storage. Or choose **Not Now** and
    do it later.

Consider also:

- Any previously linked devices will need to be re-linked. Go to **Settings** >
  **Linked devices** in the Molly app. If Signal Desktop is not detecting that
  it is no longer linked, try restarting it.
- Verify your Molly backup settings and passphrase at **Settings** >
  **Chats** > **Chat backups** (to change the backup folder, disable and then
  enable backups). Tap **Create backup** to create your first Molly backup.
- When you are satisfied that Molly is working, you may want to delete the old
  Signal backups (in `Signal/Backups`, by default).

## Feedback

- [Submit bugs and feature requests](https://github.com/mollyim/mollyim-android/issues)
- Join us at [#mollyim:matrix.org](https://matrix.to/#/#mollyim:matrix.org) on Matrix
- For news and tips & tricks follow [@mollyimapp](https://twitter.com/mollyimapp) on Twitter

## Reproducible Builds

Molly supports reproducible builds, so that anyone can run the build process again to reproduce the same APK as the original release.

Please check the guide in the [reproducible-builds](https://github.com/mollyim/mollyim-android/blob/master/reproducible-builds) directory.

## Changelog

See the [Changelog](https://github.com/mollyim/mollyim-android/wiki/Changelog) to view recent changes.

## License

License and legal notices in the original [README](README-ORIG.md).

## Disclaimer

This project is *NOT* sponsored by Signal Messenger or Signal Foundation.

The software is produced independently of Signal and carries no guarantee about quality, security or anything else. Use at your own risk.
