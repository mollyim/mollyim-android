# Molly

[![Test](https://github.com/mollyim/mollyim-android/workflows/Test/badge.svg)](https://github.com/mollyim/mollyim-android/actions)
[![Translation status](https://hosted.weblate.org/widgets/molly-instant-messenger/-/svg-badge.svg)](https://hosted.weblate.org/engage/molly-instant-messenger/?utm_source=widget)
[![Financial contributors](https://opencollective.com/mollyim/tiers/badge.svg)](https://opencollective.com/mollyim#category-CONTRIBUTE)

Molly for Watch is a modified version of Molly that makes it to be usable on Smartwatches.

## Introduction

Molly connects to the Signal server, so you can chat with your Signal contacts seamlessly. Please remember to review the [Signal Terms & Privacy Policy](https://signal.org/legal/) before signing up.

I'll update Molly for Watch if i've fixed some features or a big update is made by Signal or Molly.


## Features

Molly has unique features compared to Signal:

- **Data encryption at rest** - Protect the database with [passphrase encryption](https://github.com/mollyim/mollyim-android/wiki/Data-Encryption-At-Rest)
- **Secure RAM wiper** - Securely shred sensitive data from device memory
- **Automatic lock** - Lock the app automatically under certain conditions
- **Multi-device support** -- Link multiple devices, including Android tablets, to a single account
- **Block unknown contacts** - Block messages and calls from unknown senders for security and anti-spam
- **Contact deletion** - Allows you to delete contacts and stop sharing your profile
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

## Feedback

- [Submit bugs and feature requests](https://github.com/mollyim/mollyim-android/issues)
- Join us at [#mollyim:matrix.org](https://matrix.to/#/#mollyim:matrix.org) on Matrix
- For news, tips and tricks follow [@mollyim](https://fosstodon.org/@mollyim) on Mastodon

## Changelog

See the [Changelog](https://github.com/mollyim/mollyim-android/wiki/Changelog) to view recent changes.

## License

License and legal notices in the original [README](README-ORIG.md).

## Disclaimer

This project is *NOT* sponsored by Signal Messenger or Signal Foundation.

The software is produced independently of Signal and carries no guarantee about quality, security or anything else. Use at your own risk.
