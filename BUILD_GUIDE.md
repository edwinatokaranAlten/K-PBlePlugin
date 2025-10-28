# Build Guide

Quick reference for building the NIOX Communication Plugin SDK.

## Build Scripts Overview

| Platform | Script | Command | Output |
|----------|--------|---------|--------|
| **Mobile** (Android + iOS) | [build-mobile.sh](build-mobile.sh) | `./build-mobile.sh` | AAR + XCFramework |
| **Windows** | [build-windows.ps1](build-windows.ps1) | `.\build-windows.ps1` | Native DLL |

## Quick Start

### Build for Mobile Platforms

```bash
# Makes script executable (first time only)
chmod +x build-mobile.sh

# Run the build
./build-mobile.sh
```

**Outputs:**
- Android: `build/outputs/aar/niox-communication-plugin-release.aar`
- iOS: `build/XCFrameworks/release/NioxPlugin.xcframework` (macOS only)

### Build for Windows

```powershell
# Run in PowerShell
.\build-windows.ps1
```

**Output:**
- Windows: `build/bin/mingwX64/releaseShared/nioxplugin.dll`

## Platform-Specific Notes

### Android AAR
- **Requirements**: JDK 8+, Android SDK
- **Build time**: ~1-2 minutes
- **Size**: ~100-200 KB
- **API Level**: 21+ (Android 5.0+)

### iOS XCFramework
- **Requirements**: macOS with Xcode 14+
- **Build time**: ~2-4 minutes
- **Size**: ~500 KB - 1 MB
- **Targets**: arm64, x64, simulator-arm64
- **iOS Version**: 13.0+

### Windows DLL
- **Requirements**: MinGW-w64 toolchain
- **Build time**: ~2-3 minutes
- **Size**: ~500 KB - 2 MB
- **Architecture**: x86_64
- **OS**: Windows 10+

## Gradle Commands

If you prefer using Gradle directly:

```bash
# Android
./gradlew assembleRelease

# iOS (Debug)
./gradlew createXCFramework

# iOS (Release)
./gradlew createReleaseXCFramework

# Windows
./gradlew mingwX64MainBinaries

# Clean build
./gradlew clean

# List all tasks
./gradlew tasks
```

## Troubleshooting

### build-mobile.sh

**Problem**: "Permission denied"
```bash
chmod +x build-mobile.sh
./build-mobile.sh
```

**Problem**: "gradlew not found"
```bash
# Re-download Gradle wrapper
gradle wrapper --gradle-version 8.5
```

**Problem**: iOS build fails on non-Mac
- iOS XCFramework can only be built on macOS
- The script will skip iOS build and show a warning

### build-windows.ps1

**Problem**: "Cannot be loaded because running scripts is disabled"
```powershell
# Run PowerShell as Administrator
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

**Problem**: "gradlew.bat not found"
```bash
gradle wrapper --gradle-version 8.5
```

**Problem**: MinGW toolchain not found
- Install MinGW-w64 from: https://www.mingw-w64.org/
- Add MinGW bin directory to PATH

## Build Verification

After building, verify the outputs:

```bash
# Check Android AAR
ls -lh build/outputs/aar/niox-communication-plugin-release.aar

# Check iOS XCFramework (macOS only)
ls -lh build/XCFrameworks/release/NioxPlugin.xcframework

# Check Windows DLL
ls -lh build/bin/mingwX64/releaseShared/nioxplugin.dll
```

## Clean Build

To perform a clean build:

```bash
# Clean all build artifacts
./gradlew clean

# Then run your platform-specific build script
./build-mobile.sh
# or
.\build-windows.ps1
```

## Continuous Integration

### GitHub Actions Example

```yaml
name: Build SDK

on: [push, pull_request]

jobs:
  build-mobile:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build Mobile SDKs
        run: |
          chmod +x build-mobile.sh
          ./build-mobile.sh
      - name: Upload artifacts
        uses: actions/upload-artifact@v3
        with:
          name: mobile-sdk
          path: |
            build/outputs/aar/*.aar
            build/XCFrameworks/release/*.xcframework

  build-windows:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build Windows DLL
        run: .\build-windows.ps1
      - name: Upload artifacts
        uses: actions/upload-artifact@v3
        with:
          name: windows-sdk
          path: build/bin/mingwX64/releaseShared/*.dll
```

## Next Steps

After building:

1. **Test the SDK**: See [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) for integration examples
2. **Distribute**: Copy the artifacts to your application projects
3. **Documentation**: Refer to [README.md](README.md) for API documentation

## Support

- **Issues**: Report build issues in the project repository
- **Documentation**: See [README.md](README.md) and [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md)
- **Quick Start**: See [QUICKSTART.md](QUICKSTART.md) for rapid integration
