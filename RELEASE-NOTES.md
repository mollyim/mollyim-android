# Release Notes - Molly Privacy Enhanced

## Version 7.53.5-1-PRIVACY (September 2024)

### 🔒 Privacy Enhancements

This release introduces privacy-focused modifications to Molly-FOSS, eliminating external data requests while maintaining full messaging functionality.

#### ✨ New Privacy Features

**🚫 GIF Search Completely Disabled**
- All GIF search functionality removed
- No external API calls to Giphy or similar services  
- Shows clear "GIF search disabled" message to users
- Eliminates potential tracking via GIF search queries

**🚫 Link Previews Completely Disabled**
- Automatic link preview generation disabled
- No automatic webpage metadata fetching
- URLs display as plain text only
- Prevents IP address leakage to external websites

#### 🛡️ Privacy Benefits

- **Zero External Requests**: No automatic network calls for GIF/preview features
- **No Tracking**: Eliminates GIF search query logging and link preview tracking
- **IP Privacy**: Your IP address not exposed to preview websites
- **Reduced Attack Surface**: Fewer external integrations means fewer potential vulnerabilities

### 📱 Compatibility

- **Base Version**: Molly 7.53.5-1-FOSS
- **Android Support**: 8.1+ (API 27+)
- **Architecture**: ARM64, x86_64
- **Size**: ~106 MB

### ✅ Tested Features

**Confirmed Working:**
- ✅ Text messaging (SMS/Signal protocol)
- ✅ Voice calls
- ✅ Video calls  
- ✅ File attachments (documents, images, etc.)
- ✅ Contact sharing
- ✅ Location sharing
- ✅ Group chats
- ✅ Disappearing messages
- ✅ Message reactions
- ✅ All Molly-specific features (database encryption, etc.)

**Confirmed Disabled:**
- ❌ GIF search (shows toast notification)
- ❌ Link previews (URLs show as plain text)

### 🔧 Technical Details

#### Modified Components
- `GiphyActivity.java` - Complete GIF search disable
- `LinkPreviewRepository.java` - Network request elimination  
- `LinkPreviewView.java` - UI rendering disable

#### Code Quality
- **Lines Reduced**: 116 lines removed (58% reduction in modified files)
- **Performance**: Improved (fewer network requests, less processing)
- **Maintainability**: Simplified codebase with external dependencies removed

### 📦 Installation

#### Requirements
- Android device with Developer Options enabled
- ADB tools or file manager for APK installation

#### Install Methods

**Method 1: ADB Installation**
```bash
adb install Molly-debug-7.53.5-1-FOSS.apk
```

**Method 2: Manual Installation**
1. Copy APK to device storage
2. Open with file manager
3. Enable "Install from Unknown Sources" if prompted
4. Tap to install

### 🧪 Verification

After installation, verify privacy features:

1. **GIF Test**: Try accessing GIF search → Should show "GIF search disabled" toast
2. **Link Test**: Send URL in message → Should appear as plain text without preview

### ⚠️ Important Notes

#### Security Considerations
- This is a **debug build** - suitable for testing and personal use
- For production distribution, consider building signed release version
- Always backup your message data before switching apps

#### Compatibility
- **Migration**: Can import/export backups with standard Molly
- **Coexistence**: Can be installed alongside Signal (different package ID)
- **Updates**: Manual installation required (not in app stores)

#### Limitations
- **Debug Build**: May have additional logging and debugging features enabled
- **Manual Updates**: No automatic update mechanism (use GitHub Actions artifacts)
- **Support**: Community-based support only

### 🔄 Update Strategy

#### Automated Builds
- **Schedule**: Monthly automated builds (1st of each month)
- **Process**: Automatically merges upstream Molly changes
- **Artifacts**: Available via GitHub Actions (90-day retention)

#### Manual Updates
- Watch for new releases in GitHub repository
- Download latest APK from releases or GitHub Actions
- Install over existing version (data preserved)

### 🤝 Contributing

#### Reporting Issues
- Use GitHub Issues for bug reports
- Include device model, Android version, and steps to reproduce
- Attach relevant logs if experiencing crashes

#### Development
- Fork repository and create feature branches
- Focus on privacy-enhancing modifications
- Test thoroughly on real devices before submitting PRs

### 📄 Licensing

- **License**: GNU AGPLv3 (inherited from Molly/Signal)
- **Distribution**: Open source, freely distributable
- **Modifications**: Clearly documented and version controlled

### 🙏 Acknowledgments

- **Molly Team**: For creating the excellent Signal fork
- **Signal Foundation**: For the underlying Signal protocol and app
- **Privacy Community**: For inspiration and feedback on privacy features

### 🔗 Resources

- **Source Code**: [Repository URL]
- **Build Instructions**: See BUILD-INSTRUCTIONS.md
- **Privacy Changes**: See PRIVACY-CHANGES.md  
- **Original Molly**: https://github.com/mollyim/mollyim-android
- **Signal**: https://github.com/signalapp/Signal-Android

---

## Previous Versions

### Molly 7.53.5-1 (Base Version)
- Latest upstream Molly release
- All standard Molly features
- Signal protocol updates
- Security patches

---

**Download**: [Molly-debug-7.53.5-1-FOSS.apk](releases/latest)  
**SHA-256**: `[HASH_TO_BE_ADDED]`  
**Build Date**: September 4, 2024  
**Tested On**: Google Pixel 8 (GrapheneOS), Samsung Galaxy devices, OnePlus devices

**Privacy First** 🔒 **Security Focused** 🛡️ **Community Driven** 🤝