# Molly-FOSS Privacy Enhanced Build

A privacy-focused modification of [Molly](https://github.com/mollyim/mollyim-android) that disables GIF search and link previews for enhanced privacy and reduced data collection.

## 🔒 Privacy Modifications

This build includes the following privacy enhancements over standard Molly-FOSS:

### ✅ **GIF Search Completely Disabled**
- **What**: All GIF search functionality has been removed
- **Why**: Prevents queries to external GIF services (Giphy) that could track users
- **Behavior**: Attempting to access GIF search shows "GIF search disabled" toast and closes immediately
- **Privacy Benefit**: No external API calls, no search query logging, no tracking pixels

### ✅ **Link Previews Completely Disabled**  
- **What**: Automatic link preview generation is disabled
- **Why**: Prevents automatic fetching of webpage metadata which could leak browsing patterns
- **Behavior**: URLs appear as plain text without thumbnails, descriptions, or metadata
- **Privacy Benefit**: No automatic external HTTP requests, no metadata collection, no IP leakage

## 🆚 Comparison with Standard Builds

| Feature | Molly-FOSS | This Build | Privacy Benefit |
|---------|------------|------------|-----------------|
| GIF Search | ✅ Enabled | ❌ Disabled | No external API calls to Giphy |
| Link Previews | ✅ Enabled | ❌ Disabled | No automatic webpage fetching |
| All other features | ✅ Full | ✅ Full | Unchanged functionality |

## 🧪 Tested Functionality

**✅ Confirmed Working:**
- ✅ Text messaging
- ✅ Voice calls  
- ✅ Video calls
- ✅ File attachments
- ✅ Contact sharing
- ✅ Location sharing
- ✅ All Signal/Molly core features

**✅ Confirmed Disabled:**
- ❌ GIF search (shows toast message)
- ❌ Link previews (URLs show as plain text)

## 📱 Installation

### Requirements
- Android 8.1+ (API level 27+)
- ~110MB storage space
- ARM64 or x86_64 device architecture

### Install via ADB
```bash
# Enable Developer Options and USB Debugging on your device
# Connect via USB and authorize debugging

# Install APK
adb install Molly-debug-7.53.5-1-FOSS.apk
```

### Install via File Manager
1. Download the APK file
2. Open with your device's file manager
3. Tap to install (may require "Install from Unknown Sources")

## 🔧 Technical Details

### Modified Files
- `app/src/main/java/org/thoughtcrime/securesms/giph/ui/GiphyActivity.java`
- `app/src/main/java/org/thoughtcrime/securesms/linkpreview/LinkPreviewRepository.java` 
- `app/src/main/java/org/thoughtcrime/securesms/components/LinkPreviewView.java`

### Build Information
- **Base Version**: Molly 7.53.5-1
- **Build Type**: FOSS Website Debug
- **Build Date**: September 2024
- **APK Size**: ~106 MB
- **Target Android**: API 35 (Android 15)
- **Minimum Android**: API 27 (Android 8.1)

### Verification
You can verify the modifications by:
1. **GIF Test**: Try to access GIF search → should show "GIF search disabled" toast
2. **Link Test**: Send a URL in chat → should appear as plain text only

## 🔄 Updates

This build includes an automated update system:

- **Monthly builds** automatically merge upstream Molly changes
- **GitHub Actions** workflow builds new APKs with privacy modifications intact
- **Artifacts** available for 90 days in GitHub Actions

## 🏗️ Building from Source

### Prerequisites
```bash
# Install Java 17
sudo apt install openjdk-17-jdk

# Install Android SDK
sudo mkdir -p /opt/android-sdk
# ... (full build instructions in BUILD.md)
```

### Build Commands
```bash
# Clone repository
git clone [your-repo-url]
cd mollyim-android
git checkout disable-gif-linkpreview

# Build debug APK
./gradlew assembleProdFossWebsiteDebug

# Output: app/build/outputs/apk/prodFossWebsite/debug/
```

## 🔐 Security Notes

- **Debug Build**: This is a debug build for testing. Production use should use release builds with proper signing.
- **Same Codebase**: Based on official Molly codebase with minimal privacy-focused modifications
- **No Additional Permissions**: No extra permissions required beyond standard Molly
- **Network Reduction**: Actually reduces network traffic by disabling external API calls

## 🤝 Contributing

Contributions welcome! Please:
1. Fork the repository
2. Make privacy-focused improvements
3. Test thoroughly on real devices
4. Submit pull requests with clear documentation

## 📄 License

This project inherits the license from the original Molly project:
- Licensed under [GNU AGPLv3](https://www.gnu.org/licenses/agpl-3.0.html)
- Original Signal code maintains its respective licenses

## ⚠️ Disclaimer

- This is an **unofficial modification** of Molly
- **Not affiliated** with Molly team or Signal Foundation
- **Use at your own risk** - test thoroughly before daily use
- **Backup your data** before switching messaging apps

## 📞 Support

For issues specific to these privacy modifications:
- Check existing GitHub issues
- Create new issue with device info and steps to reproduce
- Include relevant log outputs

For general Molly issues:
- Visit the [official Molly repository](https://github.com/mollyim/mollyim-android)
- Check [Molly documentation](https://github.com/mollyim/mollyim-android/wiki)

---

**Built with privacy in mind** 🔒