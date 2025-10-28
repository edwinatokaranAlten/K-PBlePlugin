# Quick Start Guide

Get started with the NIOX Communication Plugin in 5 minutes!

## Prerequisites

- **For Android**: Android Studio, Android SDK API 21+
- **For iOS**: Xcode 14+, macOS
- **For Windows**: Visual Studio 2019+ or MinGW-w64

## Build the SDK

### Quick Build Scripts

**Mobile Platforms (Android + iOS):**
```bash
./build-mobile.sh
```
Builds Android AAR and iOS XCFramework (iOS only on macOS)

**Windows Platform:**
```powershell
.\build-windows.ps1
```
Builds Windows native DLL (no Java dependency)

### Individual Platform Builds

**Android AAR:**
```bash
./gradlew assembleRelease
```
Output: `build/outputs/aar/niox-communication-plugin-release.aar`

**iOS XCFramework:**
```bash
./gradlew createReleaseXCFramework
```
Output: `build/XCFrameworks/release/NioxPlugin.xcframework`

**Windows DLL:**
```bash
./gradlew mingwX64MainBinaries
```
Output: `build/bin/mingwX64/releaseShared/nioxplugin.dll`

## Quick Integration

### Android (Kotlin)

```kotlin
import com.niox.nioxplugin.createNioxBleManager
import com.niox.nioxplugin.models.BleStatus

// In your Activity or Fragment
val bleManager = createNioxBleManager(context)

// Check BLE status
if (bleManager.checkBleStatus() == BleStatus.ENABLED) {
    // Start scanning
    bleManager.startScan(
        durationMillis = 10000,
        onDeviceFound = { device ->
            println("Found: ${device.name} - ${device.address}")
        },
        onScanComplete = { devices ->
            println("Scan complete. Found ${devices.size} devices")
        },
        onError = { message, code ->
            println("Error: $message")
        }
    )
}
```

**Don't forget to add permissions in AndroidManifest.xml!**

### iOS (Swift)

```swift
import NioxPlugin

let bleManager = NioxBleManagerKt.createNioxBleManager(context: nil)

if bleManager.checkBleStatus() == .enabled {
    bleManager.startScan(
        durationMillis: 10000,
        onDeviceFound: { device in
            print("Found: \(device.name ?? "Unknown") - \(device.address)")
        },
        onScanComplete: { devices in
            print("Scan complete. Found \(devices.count) devices")
        },
        onError: { message, code in
            print("Error: \(message)")
        }
    )
}
```

**Don't forget to add Bluetooth usage descriptions in Info.plist!**

### Windows (C#)

```csharp
// Load the DLL
[DllImport("nioxplugin.dll")]
static extern IntPtr createNioxBleManager(IntPtr context);

[DllImport("nioxplugin.dll")]
static extern int checkBleStatus(IntPtr manager);

// Use the SDK
IntPtr manager = createNioxBleManager(IntPtr.Zero);
int status = checkBleStatus(manager);

if (status == 0) { // ENABLED
    Console.WriteLine("BLE is ready!");
}
```

## Device Requirements

The SDK scans for devices matching these criteria:

- **Name prefix**: `NIOX PRO`
- **Service UUID**: `000fc00b-8a4-4078-874c-14efbd4b510a`
- **TX Power Service**: `1804`

## Common Issues

### Android
- **No devices found**: Check Location permissions are granted and Location services are enabled
- **Permission denied**: Request `BLUETOOTH_SCAN` and `ACCESS_FINE_LOCATION` permissions

### iOS
- **Permission denied**: Add Bluetooth usage descriptions to Info.plist
- **Framework not found**: Ensure XCFramework is added to "Frameworks, Libraries, and Embedded Content"

### Windows
- **DLL not loaded**: Ensure Visual C++ Redistributable is installed
- **Bluetooth not found**: Check that Bluetooth adapter is installed and enabled

## Next Steps

- Read the [README.md](README.md) for detailed information
- Check [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) for platform-specific examples
- Review the API documentation in the README

## Sample Code

Complete sample projects are available in the repository:

- `samples/android/` - Android sample app
- `samples/ios/` - iOS sample app
- `samples/windows/` - Windows sample app

## Support

For issues, questions, or contributions:
- Open an issue on GitHub
- Contact: support@niox.com
- Documentation: See README.md and INTEGRATION_GUIDE.md

---

**Happy coding! 🚀**
