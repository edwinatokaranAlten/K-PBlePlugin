# NIOX Communication Plugin

A Kotlin Multiplatform BLE SDK for scanning and connecting to NIOX PRO devices across Android, iOS, and Windows platforms.

## Features

- **Cross-platform BLE support**: Single codebase for Android, iOS, and Windows
- **BLE status checking**: Check if BLE is available and enabled on the device
- **Device scanning**: Scan for NIOX PRO devices with configurable duration
- **Real-time device discovery**: Receive callbacks as devices are found
- **Filtered scanning**: Automatically filters for devices with "NIOX PRO" name prefix
- **Service UUID filtering**: Filters by service UUID `000fc00b-8a4-4078-874c-14efbd4b510a`

## Project Information

- **Project Name**: niox-communication-plugin
- **Bundle ID**: com.niox.nioxplugin
- **Version**: 1.0.0

## Device Specifications

The SDK is configured to scan for NIOX devices with the following characteristics:

- **Device Name Prefix**: `NIOX PRO`
- **Service UUID**: `000fc00b-8a4-4078-874c-14efbd4b510a`
- **TX Power Service UUID**: `1804`

## Platform Support

- **Android**: API 21+ (Android 5.0 Lollipop and above)
- **iOS**: iOS 13+ (arm64, x64, simulator arm64)
- **Windows**: Native Windows 10+ (without Java dependency)

## Build Requirements

- **Gradle**: 8.5+
- **Kotlin**: 1.9.22
- **Android SDK**: API 34
- **Xcode**: 14+ (for iOS builds, macOS only)
- **MinGW**: For Windows builds

## Building the SDK

### Quick Build Scripts

**For Mobile Platforms (Android AAR + iOS XCFramework):**
```bash
./build-mobile.sh
```
This script builds both Android AAR and iOS XCFramework (iOS only on macOS).

**For Windows (Native DLL):**
```powershell
.\build-windows.ps1
```
This script builds the Windows native DLL without Java dependency.

### Manual Build Commands

#### 1. Android AAR

```bash
./gradlew assembleRelease
```

Output: `build/outputs/aar/niox-communication-plugin-release.aar`

#### 2. iOS XCFramework

```bash
# Debug version
./gradlew createXCFramework

# Release version
./gradlew createReleaseXCFramework
```

Output:
- `build/XCFrameworks/debug/NioxPlugin.xcframework` (Debug)
- `build/XCFrameworks/release/NioxPlugin.xcframework` (Release)

#### 3. Windows DLL

```bash
./gradlew mingwX64MainBinaries
```

Output: `build/bin/mingwX64/releaseShared/nioxplugin.dll`

## Integration

### Android Integration

1. Copy the AAR file to your Android project's `libs` folder
2. Add the dependency in your `build.gradle`:

```gradle
dependencies {
    implementation files('libs/niox-communication-plugin-release.aar')
}
```

3. Add required permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

4. Usage example:

```kotlin
import com.niox.nioxplugin.createNioxBleManager
import com.niox.nioxplugin.models.BleStatus

class MainActivity : AppCompatActivity() {
    private lateinit var bleManager: NioxBleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the BLE manager with context
        bleManager = createNioxBleManager(this)

        // Check BLE status
        val status = bleManager.checkBleStatus()
        if (status == BleStatus.ENABLED) {
            startScan()
        }
    }

    private fun startScan() {
        bleManager.startScan(
            durationMillis = 10000,
            onDeviceFound = { device ->
                Log.d("BLE", "Found device: ${device.name} - ${device.address}")
            },
            onScanComplete = { devices ->
                Log.d("BLE", "Scan complete. Found ${devices.size} devices")
            },
            onError = { message, code ->
                Log.e("BLE", "Scan error: $message (code: $code)")
            }
        )
    }
}
```

### iOS Integration

1. Drag and drop the XCFramework into your Xcode project
2. In your project settings, add the framework to "Frameworks, Libraries, and Embedded Content"
3. Add Bluetooth permissions to your `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app needs Bluetooth to communicate with NIOX devices</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app needs Bluetooth to communicate with NIOX devices</string>
```

4. Usage example:

```swift
import NioxPlugin

class ViewController: UIViewController {
    var bleManager: NioxBleManager?

    override func viewDidLoad() {
        super.viewDidLoad()

        // Initialize the BLE manager
        bleManager = NioxBleManagerKt.createNioxBleManager(context: nil)

        // Check BLE status
        let status = bleManager?.checkBleStatus()
        if status == BleStatus.enabled {
            startScan()
        }
    }

    func startScan() {
        bleManager?.startScan(
            durationMillis: 10000,
            onDeviceFound: { device in
                print("Found device: \(device.name ?? "Unknown") - \(device.address)")
            },
            onScanComplete: { devices in
                print("Scan complete. Found \(devices.count) devices")
            },
            onError: { message, code in
                print("Scan error: \(message) (code: \(code))")
            }
        )
    }
}
```

### Windows Integration

1. Copy the DLL file to your Windows project directory
2. Load the library in your C++/C# project:

**C++ Example:**

```cpp
#include <windows.h>

typedef void* (*CreateManagerFunc)();
typedef int (*CheckStatusFunc)(void*);

int main() {
    HMODULE lib = LoadLibrary("nioxplugin.dll");
    if (lib) {
        auto createManager = (CreateManagerFunc)GetProcAddress(lib, "createNioxBleManager");
        auto checkStatus = (CheckStatusFunc)GetProcAddress(lib, "checkBleStatus");

        if (createManager && checkStatus) {
            void* manager = createManager();
            int status = checkStatus(manager);
            // Use the BLE manager
        }

        FreeLibrary(lib);
    }
    return 0;
}
```

**C# Example:**

```csharp
using System;
using System.Runtime.InteropServices;

class Program
{
    [DllImport("nioxplugin.dll")]
    static extern IntPtr createNioxBleManager(IntPtr context);

    [DllImport("nioxplugin.dll")]
    static extern int checkBleStatus(IntPtr manager);

    static void Main()
    {
        IntPtr manager = createNioxBleManager(IntPtr.Zero);
        int status = checkBleStatus(manager);
        Console.WriteLine($"BLE Status: {status}");
    }
}
```

## API Reference

### NioxBleManager

Main interface for BLE operations.

#### Methods

##### `checkBleStatus(): BleStatus`

Check the current BLE status on the device.

**Returns:**
- `BleStatus.ENABLED` - BLE is available and turned on
- `BleStatus.DISABLED` - BLE is available but turned off
- `BleStatus.NOT_SUPPORTED` - BLE is not supported on this device
- `BleStatus.PERMISSION_DENIED` - Permission to use BLE is not granted
- `BleStatus.UNKNOWN` - BLE status is unknown

##### `startScan(durationMillis, onDeviceFound, onScanComplete, onError)`

Start scanning for NIOX PRO devices.

**Parameters:**
- `durationMillis: Long` - Duration to scan in milliseconds (default: 10000)
- `onDeviceFound: (BleDevice) -> Unit` - Callback invoked when a device is found
- `onScanComplete: (List<BleDevice>) -> Unit` - Callback invoked when scan completes
- `onError: (String, Int) -> Unit` - Callback invoked if an error occurs

##### `stopScan()`

Stop an ongoing scan.

##### `getDiscoveredDevices(): List<BleDevice>`

Get list of currently discovered devices from the last scan.

**Returns:** List of discovered BLE devices

### Models

#### BleDevice

```kotlin
data class BleDevice(
    val name: String?,           // Device name
    val address: String,         // MAC address (Android/Windows) or UUID (iOS)
    val rssi: Int,              // Signal strength
    val serviceUuids: List<String>  // Advertised service UUIDs
)
```

## Permissions

### Android

The SDK requires the following permissions:
- `BLUETOOTH` - Basic Bluetooth operations
- `BLUETOOTH_ADMIN` - Bluetooth administration
- `BLUETOOTH_SCAN` - Scan for BLE devices (Android 12+)
- `BLUETOOTH_CONNECT` - Connect to BLE devices (Android 12+)
- `ACCESS_FINE_LOCATION` - Required for BLE scanning
- `ACCESS_COARSE_LOCATION` - Alternative location permission

### iOS

Add to Info.plist:
- `NSBluetoothAlwaysUsageDescription`
- `NSBluetoothPeripheralUsageDescription`

### Windows

No special permissions required. User must have Bluetooth enabled in Windows settings.

## Troubleshooting

### Android

**Issue:** Scan fails with permission error
- **Solution:** Request runtime permissions for Bluetooth and Location before scanning

**Issue:** No devices found
- **Solution:** Ensure Bluetooth is enabled and location services are on (required for BLE on Android)

### iOS

**Issue:** App crashes on launch
- **Solution:** Ensure Bluetooth usage descriptions are added to Info.plist

**Issue:** No devices found
- **Solution:** Check that Bluetooth permission has been granted in iOS Settings

### Windows

**Issue:** DLL fails to load
- **Solution:** Ensure all required runtime dependencies are present (VC++ Redistributables)

**Issue:** Bluetooth not supported error
- **Solution:** Verify that the PC has a Bluetooth adapter installed and enabled

## License

Copyright (c) 2024 NIOX. All rights reserved.

## Support

For issues and questions, please contact support or open an issue in the project repository.
