# Build Instructions - Molly Privacy Enhanced

Complete instructions for building the privacy-enhanced Molly-FOSS APK from source.

## 📋 Prerequisites

### System Requirements
- **OS**: Ubuntu 22.04+ (other Linux distros should work)
- **RAM**: 8GB minimum, 16GB recommended
- **Storage**: 20GB free space
- **CPU**: Multi-core recommended (builds can take 10-30 minutes)

### Required Software
- Java 17 (OpenJDK)
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Android NDK 28.0.13004108
- Gradle 8.11.1

## 🛠️ Step 1: Install Dependencies

### Install Java 17
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk

# Verify installation
java -version
# Should show: openjdk version "17.x.x"
```

### Install Gradle 8.11.1
```bash
# Download and install Gradle
sudo wget https://services.gradle.org/distributions/gradle-8.11.1-all.zip -O /tmp/gradle.zip
sudo unzip /tmp/gradle.zip -d /opt/
sudo ln -sf /opt/gradle-8.11.1/bin/gradle /usr/local/bin/gradle

# Verify installation
gradle --version
# Should show: Gradle 8.11.1
```

### Install Android SDK Components
```bash
# Download Android command line tools
sudo wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip
sudo mkdir -p /opt/android-sdk/cmdline-tools
sudo unzip /tmp/cmdtools.zip -d /opt/android-sdk/cmdline-tools/
sudo mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest

# Set up environment
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Fix permissions
sudo chown -R $USER:$USER /opt/android-sdk
chmod 755 /opt/android-sdk/.android 2>/dev/null || mkdir -p /opt/android-sdk/.android

# Accept licenses and install components
yes | sdkmanager --sdk_root=/opt/android-sdk --licenses
sdkmanager --sdk_root=/opt/android-sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;28.0.13004108"
```

### Add to Shell Profile
```bash
# Add to ~/.bashrc or ~/.zshrc
echo 'export ANDROID_HOME=/opt/android-sdk' >> ~/.bashrc
echo 'export ANDROID_SDK_ROOT=/opt/android-sdk' >> ~/.bashrc  
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc

# Reload shell
source ~/.bashrc
```

## 📥 Step 2: Clone Repository

```bash
# Clone the privacy-enhanced repository
git clone [YOUR_REPOSITORY_URL] molly-privacy
cd molly-privacy

# Switch to privacy modifications branch
git checkout disable-gif-linkpreview

# Verify you're on the right branch
git branch
# Should show: * disable-gif-linkpreview
```

## 🏗️ Step 3: Build APK

### Debug Build (Recommended for Testing)
```bash
# Build debug APK
./gradlew assembleProdFossWebsiteDebug

# Build output location
ls -la app/build/outputs/apk/prodFossWebsite/debug/
# Should show: Molly-debug-7.53.5-1-FOSS.apk (~106MB)
```

### Release Build (For Distribution)
```bash
# Build release APK (requires signing setup)
./gradlew assembleProdFossWebsiteRelease

# Build output location  
ls -la app/build/outputs/apk/prodFossWebsite/release/
# Should show: Molly-release-7.53.5-1-FOSS.apk
```

### All Available Build Variants
```bash
# See all available build tasks
./gradlew tasks --group=build | grep assemble

# Available variants:
# - assembleProdFossWebsiteDebug     (Google-free debug)
# - assembleProdFossWebsiteRelease   (Google-free release) 
# - assembleProdGmsWebsiteDebug      (With Google services debug)
# - assembleProdGmsWebsiteRelease    (With Google services release)
# - assembleStagingFoss*             (Testing variants)
```

## 🔧 Step 4: Verify Build

### Check APK Details
```bash
# Verify APK was created
file app/build/outputs/apk/prodFossWebsite/debug/Molly-debug-7.53.5-1-FOSS.apk
# Should show: Zip archive data

# Check APK size
ls -lh app/build/outputs/apk/prodFossWebsite/debug/Molly-debug-7.53.5-1-FOSS.apk  
# Should show: ~106M
```

### Validate Privacy Modifications
```bash
# Check that modified files are included in build
grep -r "GIF search disabled" app/src/main/java/
grep -r "Disable link preview" app/src/main/java/

# Verify git modifications are in place
git log --oneline -5
# Should show recent commits about disabling GIF/link features
```

## 📱 Step 5: Install APK

### Via ADB (USB Debugging Required)
```bash
# Connect Android device with USB debugging enabled
adb devices
# Should show your device

# Install APK
adb install app/build/outputs/apk/prodFossWebsite/debug/Molly-debug-7.53.5-1-FOSS.apk
# Should show: Success
```

### Via File Transfer
```bash
# Copy APK to device storage
adb push app/build/outputs/apk/prodFossWebsite/debug/Molly-debug-7.53.5-1-FOSS.apk /sdcard/Download/

# Or copy manually via USB file transfer
cp app/build/outputs/apk/prodFossWebsite/debug/Molly-debug-7.53.5-1-FOSS.apk ~/Desktop/
```

## 🧪 Step 6: Test Privacy Features

After installation, verify the privacy modifications work:

### Test GIF Search Disabled
1. Open Molly app
2. Start a conversation  
3. Try to access GIF search
4. **Expected**: Toast message "GIF search disabled" appears

### Test Link Previews Disabled  
1. In a conversation, type: `https://github.com`
2. Send the message
3. **Expected**: URL appears as plain text with no preview

## 🚨 Troubleshooting

### Build Fails with "SDK not found"
```bash
# Verify SDK location
echo $ANDROID_HOME
ls $ANDROID_HOME/platforms/

# If empty, reinstall SDK components
sdkmanager --sdk_root=/opt/android-sdk "platforms;android-35" "build-tools;35.0.0"
```

### Build Fails with Java Version Error
```bash
# Check Java version
java -version

# If not Java 17, install and set default
sudo apt install openjdk-17-jdk
sudo update-alternatives --config java
# Select Java 17
```

### Build Fails with "Permission denied"
```bash
# Fix Gradle wrapper permissions
chmod +x gradlew

# Fix Android SDK permissions
sudo chown -R $USER:$USER /opt/android-sdk
```

### Build Fails with Network Errors
```bash
# Build with offline mode if dependencies are cached
./gradlew assembleProdFossWebsiteDebug --offline

# Or clear Gradle cache and retry
rm -rf ~/.gradle/caches/
./gradlew assembleProdFossWebsiteDebug
```

### APK Install Fails
```bash
# Check if device is connected
adb devices

# Enable "Install from Unknown Sources" on device
# Try installing again
adb install -r app/build/outputs/apk/prodFossWebsite/debug/Molly-debug-7.53.5-1-FOSS.apk
```

## 🔄 Automated Builds

### GitHub Actions (Included)
The repository includes a GitHub Actions workflow that:
- Runs monthly on the 1st
- Merges upstream Molly changes  
- Builds fresh APK automatically
- Uploads artifacts for download

### Manual Trigger
```bash
# Trigger GitHub Actions build manually
gh workflow run auto-merge-build.yml
```

## 📊 Build Performance

### Typical Build Times
- **First build**: 10-30 minutes (downloading dependencies)
- **Subsequent builds**: 2-10 minutes (incremental)
- **Clean builds**: 5-15 minutes

### Build Resource Usage
- **RAM**: 4-8GB during build
- **CPU**: High usage on all cores
- **Storage**: ~15GB for all dependencies and build artifacts

### Optimization Tips
```bash
# Use build cache for faster builds
echo "org.gradle.caching=true" >> gradle.properties

# Use parallel builds
echo "org.gradle.parallel=true" >> gradle.properties

# Increase JVM heap size if you have RAM
echo "org.gradle.jvmargs=-Xmx4g" >> gradle.properties
```

## 🔐 Code Signing (Optional)

For production/distribution builds, you may want to sign the APK:

### Generate Keystore
```bash
keytool -genkey -v -keystore molly-privacy.keystore -alias molly-privacy -keyalg RSA -keysize 2048 -validity 10000
```

### Configure Signing
Add to `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            keyAlias = "molly-privacy"
            keyPassword = "YOUR_KEY_PASSWORD" 
            storeFile = file("../molly-privacy.keystore")
            storePassword = "YOUR_STORE_PASSWORD"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

---

**Ready to build!** 🏗️ Follow these steps and you'll have a privacy-enhanced Molly APK ready for testing and distribution.