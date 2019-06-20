# sig4a

[![Build Status](https://travis-ci.com/sigx/sig4a.svg?branch=master)](https://travis-ci.com/sigx/sig4a)

> sig4a is a staging-version of [Signal](https://github.com/signalapp/Signal-Android) for Android, the fast simple secure private-messenger app by [Signal Foundation](https://signal.org).

## About

This project is built by friendly volunteer quality-assurance and collaborative [loose-team](https://en.wikipedia.org/wiki/History_of_Wikipedia#Formulation_of_the_concept) skunkworks. It is an unofficial third-party effort aimed to create complete and well-tested solutions ready to submit upstream to Signal Foundation.

**Here be dragons**! Although we believe they are super-awesome, contributors to sigX are not necessarily leading experts in their field, and are often enough anonymous people on the internet. This sig4a repository, sigX, and sigGesT alpha-testing-binaries *SHOULD NOT* be regarded as secure. 🐉🐉🐉

*WIP: the following sections are work-in-progress*

## Get Involved

The best way to get started in this early-phase is reading our [FAQ](https://github.com/sigx/sigX.github.io/wiki/FAQ) and participating in the unofficial forum https://community.signalusers.org.

## Continuous Integration

This version is CI-ready. Sign up in travis-ci.com to enable automatic builds for sig4a in your personal repo.

### Automatic Releases

Tagged commits triggers a new release job in Travis CI. This job will build the APK and deploy it automatically to GitHub. The APK will be identified by the tag name, ready to be downloaded by you or your fellow testers, in the release page.

This way it is not necessary anymore to install Android Studio or run gradle to start developing a new feature or bugfix.

Be sure to be set `GITHUB_TOKEN`  environment variable in Travis CI to grant write permissions to your repo to Travis.

### Gradle Scans

To enable [build scans](https://scans.gradle.com/) for your builds, set `BUILD_SCAN=1` as an environment variable in Travis CI.

## Bugs

Report bugs in sig4a itself to [Issues](https://github.com/sigx/sig4a/issues).

If you are reporting a bug for an experimental branch, written by one of the sigX participants and included in the sigGesT release, please try to read the appropriate thread in https://community.signalUsers.org forum where the participants discuss their branch.

If in doubt, the forum is the correct place to try first.

## Disclaimer

This project is *NOT* sponsored by Signal Messenger LLC. or Signal Foundation.

The software is produced independently of Signal Messenger and carries no guarantee about quality, security or anything else. Use at your own risk.
