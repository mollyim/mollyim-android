# Signal-LTS

Signal-LTS is an unofficial Long Term Support version of Signal.

The goal is to offer greater stability to signal users.

Due to signals making sometimes quick, controversial, and generally unwanted updates. This fork will allow users who dislike these updates a refuge, while we take the time to listen to the community to find out what is truly wanted for the app.

## Features

Signal-LTS has far more features than signal too!

Signal-LTS is actually a fork of [Molly FOSS](https://molly.im), therefore inherits all its features.

### Inherited from Molly.im

- **Data encryption at rest** - Protect the database with [passphrase encryption](https://github.com/mollyim/mollyim-android/wiki/Data-Encryption-At-Rest)
- **Secure RAM wiper** - Securely shred sensitive data from device memory
- **Automatic lock** - Lock the app automatically under certain conditions
- **Block unknown contacts** - Block messages and calls from unknown senders for security and anti-spam
- **Contact deletion** - Allows you to delete contacts and stop sharing your profile
- **Disappearing call history** - Clear call notifications together with expiring messages
- **Debug logs are optional** - Android logging can be disabled
- **Custom backup scheduling** - Choose between daily or weekly interval and the number of backups to retain
- **SOCKS proxy and Tor support** - Tunnel app network traffic via proxy and Orbot

### Features added on from Molly.im / Signal

- **Fully FOSS**

  [Molly](https://molly.im) offers two versions of Signal, one without any proprietary dependencies at all (100% FOSS), and one with proprietary dependencies. Signal-LTS is always built of Molly FOSS, meaning it is 100% Free Software.

- **LTS**

  This fork will be recompiled and a new release added every 6 months, or when there is an update that would make Signal-LTS incompatible with Signal. This will offer Signal-LTS users far more stability, and allow us time to think about issues e.g. Signal randomly changing the chat message colours, and if we can revert them. The source code to this fork will be updated more than every 6 months, it is only the release that will occur every 6 months.

- **Deleting messages time extension**

  Messages must be deleted within 3 hours of sending on Signal and Molly. Signal-LTS has modified this to 24 hours (the limit the recipient will allow deleted messages up to).

- **Does not compress images or video**

  This fork is modified to not compress messages or video. This means that you can now easily send images and video to friends etc, without worrying about extreme compression reducing the quality. We did however make a security trade off here. Images sent via Signal-LTS **DO** contain their original metadata. It is highly recommended to send images and video **ONLY** to people you trust, as the metadata could give away things such as your location when taking the image and the time you took it, phone model, etc etc.

- **Refuses to send read receipts**

  This fork has been modified to not send read receipts at all. Meaning you may be able to see someone else has read your message, but not vice versa.

- **Refuses to send typing indicators**

  Same as above but for typing indicators.

- **Removed Gif's**

  This fork has removed the Gif sending functionality from signal. Gif's were supplied by Giphy. Giphy is owned by Facebook. Signal has managed to implement the retrieval of gif's in a private way, however I personally do not want my Signal client to be fetching content from Facebook at all, in a private manor, or not.

- **Removed animations**

  Signal has many animations which as far as I am aware do not turn off even if you turn animations system wide. These animations can be slow and annoying. In Signal-LTS, most of the animations are removed, and those which remain are significantly sped up to save you time.

- **Remove unnecessary settings**

  Some signal settings which offer little utility and simply make the UI more complex have been removed.

- **Colour Scheme**

  Signal-LTS follows a green colour scheme rather than purple like Molly, or blue like Signal.

## Upstream

Signal --> Molly.im --> Signal-LTS

### Why a fork of Molly not Signal?

I personally think that [Molly](https://molly.im) offers some great extensions upon Signal. Molly has not made any changes which I disapprove of. Therefore, it felt natural to simply fork Molly and build upon it, rather than signal, due to it already having made some brilliant changes. I highly recommend [Molly](https://molly.im) as a Signal client.


# License & Legal

License and legal notices in the original [README](README-ORIG.md).
