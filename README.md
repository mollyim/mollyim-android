# Molly

[![Android CI](https://github.com/mollyim/mollyim-android/workflows/Android%20CI/badge.svg)](https://github.com/mollyim/mollyim-android/actions)

> Molly is a hardened version of [Signal](https://github.com/signalapp/Signal-Android) for Android, the fast simple yet secure messaging app by [Signal Foundation](https://signal.org).

## Introduction

Back in 2018, Signal allowed the user to set a passphrase to secure the local message database. But this option was removed with the introduction of full-disk encryption on Android. Molly bring it back again with some additional security features.

Molly is updated every two weeks to include the latest Signal changes and bug fixes. You can download it from GitHub's [Releases](https://github.com/mollyim/mollyim-android/releases/latest) page.

## Features

Molly has unique features compared to Signal:

- Protects database with passphrase encryption
- Locks down the app automatically after a set time of inactivity
- Securely wipes sensitive data from RAM
- Disables logging debug messages

Besides that, you will find all the features of Signal on Molly with the sole exception of the SMS/MMS integration.

## Compatibility with Signal

Molly and Signal can be installed on the same device.

However, you cannot use your phone number on both apps at the same time. Only the last app to register will remain active, and the other will go offline. To overcome this, remember that you are not limited to use only your SIM card number, but also any number on you can receive SMS or phone calls at registration.

## Feedback

- Ask a question on the forum [community.signalusers.org](https://community.signalusers.org/)
- [Submit bugs and feature requests](https://github.com/mollyim/mollyim-android/issues)

## Reproducible Builds

Molly supports reproducible builds, so that anyone can run the build process again and reproduce the same APK as the original release.

Please read the [Reproducible Builds](https://github.com/mollyim/mollyim-android/wiki/Reproducible-Builds) page in our [wiki](https://github.com/mollyim/mollyim-android/wiki).

## Changelog

See the [CHANGELOG](CHANGELOG.md) to view recent changes.

## License

License and legal notices in the original [README](README-ORIG.md).

## Disclaimer

This project is *NOT* sponsored by Signal Messenger LLC. or Signal Foundation.

The software is produced independently of Signal Messenger and carries no guarantee about quality, security or anything else. Use at your own risk.
