# Building and Self-Signing Molly

## Overview

This guide provides detailed steps for building and self-signing the Molly app. It is intended for developers and experienced users familiar with compiling software and managing signing keys.

⚠️ **Warning**: Mishandling signing keys can result in security vulnerabilities and compromise the app's integrity. Ensure you protect your keys and understand the implications of self-signing your app.

## Security Considerations

- **In-App Updater**: Self-signed apps cannot update automatically via the integrated updater because of differing signatures. You'll need to manually build and install app updates, or set up your own private F-Droid repository (beyond this guide's scope).

- **Clean Environment**: Building the app requires a clean and secure environment. Using non-dedicated computers or the cloud is discouraged as it increases the risk of attackers injecting malicious code during the build process. Running Reproducible Builds on the same environment used to build the app only verifies the deterministic nature of the build process, but does not validate the integrity or security of the build.

- **Offline Signing**: Offline APKs signing using the Android SDK is recommended. For better security, consider using a smartcard to store your signing key and perform the signature of the APKs.

## Prerequisites

### Install JDK

Ensure the Java Development Kit (JDK) is installed for generating your signing key using `keytool`.

### Generate Your Private Key

Generate a signing private key using:

```sh
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 4096 -validity 10000 -alias my-alias
```

## Building Using GitHub Actions

You can build Molly using GitHub Actions, either with GitHub-hosted public runners or [self-hosted runners](https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/about-self-hosted-runners).

### Steps

1. **Fork the Repository**: Fork the [Molly repository](https://github.com/mollyim/mollyim-android) in GitHub. You can keep your fork private if preferred, but public repositories get GitHub Actions for free, while private repositories have limited free storage and minutes. For details, refer to [GitHub's billing information](https://docs.github.com/en/billing/managing-billing-for-github-actions/about-billing-for-github-actions).

2. **Configure Repository Variables**: Customize your build via `Settings > Secrets and Variables > Actions > Variables > Repository variables`. Check the table below for available options.

3. **Run Workflow**: In Actions, select `Tag Commit for Release` and trigger `Run workflow`.

4. **Monitor Build Progress**: The build typically takes around 45 minutes.

5. **Download APKs**: Once the workflow finishes, go to Releases in your repository and download the APKs listed under "Assets".

6. **Publish Release**: Optionally, publish the release draft to trigger the Reproducible Build workflow.

7. **Sign the APKs**: Follow the instructions below on how to [sign the APKs](#signing-the-apks) before installation.

8. **Install the APKs**: After signing, the APKs are ready for installation on Android.

9. **Keep Your Fork Updated**: Periodically sync your repository and then proceed to step 3 again. Follow GitHub's documentation on [syncing a fork](https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/syncing-a-fork). This step is essential to keep your app updated.

## Building Using the CLI

If you prefer building Molly locally on your computer, you can follow these steps, which are essentially the same procedure as in the [Reproducible Build guide](reproducible-builds/README.md). You have the option to customize the build by exporting environment variables or saving them in a `.env` file before running `docker compose`.

### Steps

```sh
# Set the release version you want to build
export VERSION=v7.8.1-2

# Clone the source code repository
git clone https://github.com/mollyim/mollyim-android.git

# Navigate to the reproducible builds directory
cd mollyim-android/reproducible-builds

# Checkout the specific release tag
git checkout $VERSION

# Customize your build by exporting environment variables if needed
export CI_APP_TITLE="Molly"
export CI_PACKAGE_ID="im.molly.app"

# Build the APK using Docker environment
docker compose up --build

# Optionally, save environment variables in a .env file for future builds
echo "CI_APP_TITLE=Molly" >> .env
echo "CI_PACKAGE_ID=im.molly.app" >> .env

# Shut down the Docker environment after use
docker compose down
```

The built APKs will be available in the `output/apk` directory. Make sure to [sign the APKs](#signing-the-apks) before installation.

## Build Customization

| Environment Variable  | Default Value       | Description                                      |
|-----------------------|---------------------|--------------------------------------------------|
| `CI_APP_TITLE`        | Molly               | App title as shown in the UI                     |
| `CI_APP_FILENAME`     | Molly               | Base filename for APKs and backups               |
| `CI_PACKAGE_ID`       | im.molly.app        | Application ID (change as needed)                |
| `CI_BUILD_VARIANTS`   | prod(Gms\|Foss)     | Regex pattern for building different flavors (must match one of the build flavors) |
| `CI_FORCE_INTERNAL_USER_FLAG` | false       | Enable internal testing extensions               |
| `CI_MAPS_API_KEY`     | AIza...ftB4         | Google Maps API key (use your own)               |

## Build Flavors

- `prodGmsWebsiteRelease`: Production version of Molly
- `prodFossWebsiteRelease`: Production version of Molly-FOSS
- `prodFossStoreRelease`: Production version of Molly-FOSS but without in-app updater
- `stagingGmsWebsiteRelease`: Testing version of Molly for Signal staging network
- `stagingFossWebsiteRelease`: Testing version of Molly-FOSS for Signal staging network

## Signing the APKs

### Offline Signing

To sign the APKs offline, install the Android SDK and use the `apksigner` tool:

```sh
apksigner sign --ks my-release-key.jks --out Molly-$VERSION.apk Molly-unsigned-$VERSION.apk
```

### Automatic Signing via GitHub Actions

Configure automatic signing if you trust GitHub to safeguard your private key.

1. **Encode Keystore File**

   ```sh
   base64 my-release-key.jks
   ```

2. **Add Secrets to GitHub**

   Go to your repository's `Settings > Secrets > Actions` and add the following secrets:
   - `SECRET_KEYSTORE`: Paste the base64 encoded content of your keystore file.
   - `SECRET_KEYSTORE_ALIAS`: Your key alias (e.g., `my-alias`).
   - `SECRET_KEYSTORE_PASSWORD`: Your keystore password.

