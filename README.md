# Molly

[![Android CI](https://github.com/mollyim/mollyim-android/workflows/Android%20CI/badge.svg)](https://github.com/mollyim/mollyim-android/actions)
[![Total alerts](https://img.shields.io/lgtm/alerts/g/mollyim/mollyim-android.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/mollyim/mollyim-android/alerts/)
[![Translation status](https://hosted.weblate.org/widgets/molly-instant-messenger/-/svg-badge.svg)](https://hosted.weblate.org/engage/molly-instant-messenger/?utm_source=widget)

> Molly is a hardened version of [Signal](https://github.com/signalapp/Signal-Android) for Android, the fast simple yet secure messaging app by [Signal Foundation](https://signal.org).

## Introduction

Back in 2018, Signal allowed the user to set a passphrase to secure the local message database. But this option was removed with the introduction of full-disk encryption on Android. Molly brings it back again with additional security features.

Molly is updated every two weeks to include the latest Signal changes and bug fixes.

## Download

You can download the app from GitHub's [Releases](https://github.com/mollyim/mollyim-android/releases/latest) page or install it from F-Droid.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://molly.im/fdroid/)

There are two flavors to choose from download: **Molly** or **Molly-FOSS**. Learn the differences [below](#free-and-open-source) and download the right one for you.

## Features

Molly has unique features compared to Signal:

- Protects database with [passphrase encryption](https://github.com/mollyim/mollyim-android/wiki/Data-Encryption-At-Rest)
- Locks down the app automatically after you go a certain time without unlocking your device
- Securely shreds sensitive data from RAM
- Allows you to delete contacts and stop sharing your profile
- Clears call notifications together with expiring messages
- Disables debug logs

Besides that, you will find all the features of Signal plus some minor tweaks and improvements. The only exception is the SMS integration, that is incompatible with Molly security enhancements.

## Free and Open-Source

Molly is open-source just like Signal. But the app uses Google's proprietary code to support some key features. Molly-FOSS is the community effort to make it 100% free and open-source.

You can install the Molly flavor of your choice at any time, and it will replace any previously installed version. The data and settings will be preserved so that you do not have to re-register.

This table lists the current status of the dependencies:

| Feature                                | Signal          | Molly            | Molly-FOSS |
| -------------------------------------- | --------------- | ---------------- |------------|
| Push notifications <sup>(1)</sup>      | ⚠ (FCM)         | ⚠️ (FCM)         | ✘          |
| Websocket notifications <sup>(2)</sup> | ✔️               | ✔️               | ✔️          |
| Location provider                      | ⚠ (Google Maps) | ⚠ (Google Maps) | ✘          |
| Automatic face blurring                | ⚠ (Firebase)    | ⚠ (Firebase)    | ✘          |

<sup>(1)</sup> If you are running a custom ROM and the app fails to register with Play Services, try the FOSS flavor.<br/>
<sup>(2)</sup> You may need to disable the system battery optimizations to receive notifications in background.

## Compatibility with Signal

Molly and Signal can be installed on the same device. If you need a 2nd number to chat, you can use Molly along with Signal.

However, you cannot use the same phone number on both apps at the same time. Only the last app to register will remain active, and the other will go offline. Remember that you are not limited to use only your main phone number, but also any number on you can receive SMS or phone calls at registration.

## Backups

Backups are fully compatible. Signal [backups](https://support.signal.org/hc/en-us/articles/360007059752-Backup-and-Restore-Messages) can be restored in Molly and the other way around. To do that, simply rename the backup file and copy it into the expected path, so the app can find the backup to restore during installation.

The path within internal storage where backups are written by Signal:
- `Signal/Backups/Signal-year-month-date-time.backup`

And Molly:
- `Molly/Backups/Molly-year-month-date-time.backup`

## Feedback

- Ask a question on the forum [community.signalusers.org](https://community.signalusers.org/)
- [Submit bugs and feature requests](https://github.com/mollyim/mollyim-android/issues)

## Reproducible Builds

Molly supports reproducible builds, so that anyone can run the build process again and reproduce the same APK as the original release.

Please check the guide in the [reproducible-builds](https://github.com/mollyim/mollyim-android/blob/master/reproducible-builds) directory.

## Changelog

See the [Changelog](https://github.com/mollyim/mollyim-android/wiki/Changelog) to view recent changes.

## License

License and legal notices in the original [README](README-ORIG.md).

## Disclaimer

This project is *NOT* sponsored by Signal Messenger LLC or Signal Foundation.

The software is produced independently of Signal and carries no guarantee about quality, security or anything else. Use at your own risk.
