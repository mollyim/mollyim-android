# Docker Build - Quick Start

## Prerequisites

```bash
# Install Docker
# Visit: https://docs.docker.com/get-docker/

# Verify installation
docker --version
docker compose version
```

## Build Commands

### Android App

```bash
# Build debug APK (fastest)
./build.sh debug

# Build production release with post-quantum crypto
./build.sh release --production

# Build all variants
./build.sh full

# Clean build
./build.sh clean
```

### Python Translation Server

```bash
# Start server (runs on port 5353)
./build.sh server

# Test server
./build.sh server-test

# Stop server
./build.sh server-stop
```

### Development

```bash
# Interactive shell with Gradle access
./build.sh dev

# Inside shell:
./gradlew assembleDebug
./gradlew test
./gradlew :security-lib:build
```

## Configuration

```bash
# Copy environment template
cp .env.example .env

# Edit .env to customize:
# - PRODUCTION_CRYPTO=ON/OFF
# - CI_APP_TITLE=YourAppName
# - CI_PACKAGE_ID=com.your.package
# - Build variants, signing keys, etc.
```

## Output Locations

```bash
# APKs
app/build/outputs/apk/prodGmsWebsite/debug/*.apk
app/build/outputs/apk/prodGmsWebsite/release/*.apk

# List built APKs
find app/build/outputs/apk -name "*.apk"
```

## Common Tasks

```bash
# Verify environment
./build.sh verify

# Build and sign release
export CI_KEYSTORE_PATH=./my-key.jks
export CI_KEYSTORE_PASSWORD=mypassword
export CI_KEYSTORE_ALIAS=myalias
./build.sh release

# Clean Docker resources
./build.sh clean-docker

# Rebuild from scratch
./build.sh rebuild
```

## Help

```bash
# Show all commands
./build.sh help

# Full documentation
cat DOCKER_BUILD.md
```

## Troubleshooting

```bash
# Docker not running
sudo systemctl start docker  # Linux
# Or start Docker Desktop     # macOS/Windows

# Low disk space
./build.sh clean-docker
docker system prune -a

# Build fails
./build.sh rebuild --no-cache
```

---

**Full Documentation:** See [DOCKER_BUILD.md](DOCKER_BUILD.md) for comprehensive guide.
