# Integration Guide

This guide provides detailed step-by-step instructions for integrating the NIOX Communication Plugin into your applications.

## Table of Contents

- [Android Integration](#android-integration)
- [iOS Integration](#ios-integration)
- [Windows Integration](#windows-integration)
- [Common Usage Patterns](#common-usage-patterns)

---

## Android Integration

### Step 1: Add the AAR to Your Project

1. Copy `niox-communication-plugin-release.aar` to your project's `app/libs` directory
2. In your app's `build.gradle`, add:

```gradle
android {
    // ... other configuration
}

dependencies {
    implementation files('libs/niox-communication-plugin-release.aar')
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

### Step 2: Update AndroidManifest.xml

Add required permissions:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Bluetooth permissions -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />

    <!-- Android 12+ permissions -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <!-- Location permissions (required for BLE scanning) -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Declare BLE feature -->
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />

    <application>
        <!-- Your app content -->
    </application>
</manifest>
```

### Step 3: Request Runtime Permissions

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initializeBle()
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initializeBle()
        }
    }

    private fun initializeBle() {
        // BLE initialization code here
    }
}
```

### Step 4: Use the SDK

```kotlin
import com.niox.nioxplugin.createNioxBleManager
import com.niox.nioxplugin.models.BleStatus
import com.niox.nioxplugin.models.BleDevice

class MainActivity : AppCompatActivity() {
    private lateinit var bleManager: NioxBleManager
    private val discoveredDevices = mutableListOf<BleDevice>()

    private fun initializeBle() {
        bleManager = createNioxBleManager(this)

        // Check BLE status
        when (bleManager.checkBleStatus()) {
            BleStatus.ENABLED -> {
                Log.d("BLE", "BLE is enabled, ready to scan")
                startScan()
            }
            BleStatus.DISABLED -> {
                Log.w("BLE", "BLE is disabled, prompt user to enable")
                promptEnableBluetooth()
            }
            BleStatus.NOT_SUPPORTED -> {
                Log.e("BLE", "BLE is not supported on this device")
            }
            BleStatus.PERMISSION_DENIED -> {
                Log.e("BLE", "BLE permissions denied")
            }
            BleStatus.UNKNOWN -> {
                Log.w("BLE", "BLE status unknown")
            }
        }
    }

    private fun startScan() {
        bleManager.startScan(
            durationMillis = 10000, // Scan for 10 seconds
            onDeviceFound = { device ->
                runOnUiThread {
                    Log.d("BLE", "Found: ${device.name} (${device.address}) RSSI: ${device.rssi}")
                    discoveredDevices.add(device)
                    updateDeviceList()
                }
            },
            onScanComplete = { devices ->
                runOnUiThread {
                    Log.d("BLE", "Scan complete. Total devices found: ${devices.size}")
                    Toast.makeText(this, "Found ${devices.size} NIOX devices", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { message, code ->
                runOnUiThread {
                    Log.e("BLE", "Scan error: $message (code: $code)")
                    Toast.makeText(this, "Scan error: $message", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun promptEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.stopScan()
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }
}
```

---

## iOS Integration

### Step 1: Add XCFramework to Xcode Project

1. Open your Xcode project
2. Drag and drop `NioxPlugin.xcframework` into your project navigator
3. In the dialog that appears, check "Copy items if needed"
4. In your project settings, go to "Frameworks, Libraries, and Embedded Content"
5. Ensure `NioxPlugin.xcframework` is set to "Embed & Sign"

### Step 2: Update Info.plist

Add Bluetooth usage descriptions:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app needs Bluetooth to scan for and connect to NIOX PRO devices</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app needs Bluetooth to communicate with NIOX PRO devices</string>
```

### Step 3: Use the SDK (Swift)

```swift
import UIKit
import NioxPlugin

class ViewController: UIViewController {

    private var bleManager: NioxBleManager?
    private var discoveredDevices: [BleDevice] = []

    override func viewDidLoad() {
        super.viewDidLoad()

        // Initialize BLE manager
        bleManager = NioxBleManagerKt.createNioxBleManager(context: nil)

        // Check BLE status
        checkBleStatus()
    }

    private func checkBleStatus() {
        guard let status = bleManager?.checkBleStatus() else { return }

        switch status {
        case .enabled:
            print("BLE is enabled, ready to scan")
            startScan()

        case .disabled:
            print("BLE is disabled")
            showAlert(title: "Bluetooth Off", message: "Please enable Bluetooth in Settings")

        case .notSupported:
            print("BLE not supported on this device")
            showAlert(title: "Not Supported", message: "Bluetooth LE is not supported on this device")

        case .permissionDenied:
            print("BLE permission denied")
            showAlert(title: "Permission Required", message: "Please grant Bluetooth permission in Settings")

        case .unknown:
            print("BLE status unknown")

        default:
            break
        }
    }

    private func startScan() {
        bleManager?.startScan(
            durationMillis: 10000, // 10 seconds
            onDeviceFound: { [weak self] device in
                DispatchQueue.main.async {
                    print("Found device: \(device.name ?? "Unknown") - \(device.address)")
                    self?.discoveredDevices.append(device)
                    self?.updateDeviceList()
                }
            },
            onScanComplete: { [weak self] devices in
                DispatchQueue.main.async {
                    print("Scan complete. Found \(devices.count) devices")
                    self?.showAlert(
                        title: "Scan Complete",
                        message: "Found \(devices.count) NIOX devices"
                    )
                }
            },
            onError: { [weak self] message, code in
                DispatchQueue.main.async {
                    print("Scan error: \(message) (code: \(code))")
                    self?.showAlert(title: "Scan Error", message: message)
                }
            }
        )
    }

    private func updateDeviceList() {
        // Update your UI with discovered devices
        // e.g., reload table view, update collection view, etc.
    }

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    deinit {
        bleManager?.stopScan()
    }
}
```

### Step 4: Use the SDK (Objective-C)

```objc
#import <NioxPlugin/NioxPlugin.h>

@interface ViewController ()
@property (nonatomic, strong) id<NioxBleManager> bleManager;
@property (nonatomic, strong) NSMutableArray<BleDevice *> *discoveredDevices;
@end

@implementation ViewController

- (void)viewDidLoad {
    [super viewDidLoad];

    self.discoveredDevices = [NSMutableArray array];
    self.bleManager = [NioxBleManagerKt createNioxBleManagerWithContext:nil];

    [self checkBleStatus];
}

- (void)checkBleStatus {
    BleStatus status = [self.bleManager checkBleStatus];

    if (status == BleStatusEnabled) {
        NSLog(@"BLE is enabled");
        [self startScan];
    } else if (status == BleStatusDisabled) {
        NSLog(@"BLE is disabled");
    }
}

- (void)startScan {
    [self.bleManager startScanWithDurationMillis:10000
        onDeviceFound:^(BleDevice *device) {
            dispatch_async(dispatch_get_main_queue(), ^{
                NSLog(@"Found: %@ - %@", device.name, device.address);
                [self.discoveredDevices addObject:device];
            });
        }
        onScanComplete:^(NSArray<BleDevice *> *devices) {
            dispatch_async(dispatch_get_main_queue(), ^{
                NSLog(@"Scan complete. Found %lu devices", (unsigned long)devices.count);
            });
        }
        onError:^(NSString *message, int32_t code) {
            dispatch_async(dispatch_get_main_queue(), ^{
                NSLog(@"Scan error: %@ (code: %d)", message, code);
            });
        }
    ];
}

- (void)dealloc {
    [self.bleManager stopScan];
}

@end
```

---

## Windows Integration

### C++ Integration

#### Step 1: Include Headers and Link Library

```cpp
#include <windows.h>
#include <string>
#include <functional>
#include <iostream>

// Function pointer types for DLL functions
typedef void* (*CreateManagerFunc)(void*);
typedef int (*CheckStatusFunc)(void*);
typedef void (*StartScanFunc)(void*, long long, void*, void*, void*);
typedef void (*StopScanFunc)(void*);
```

#### Step 2: Load and Use the DLL

```cpp
class NioxBleWrapper {
private:
    HMODULE dllHandle;
    void* managerInstance;

    CreateManagerFunc createManager;
    CheckStatusFunc checkStatus;
    StartScanFunc startScan;
    StopScanFunc stopScan;

public:
    NioxBleWrapper(const std::wstring& dllPath) {
        dllHandle = LoadLibraryW(dllPath.c_str());
        if (!dllHandle) {
            throw std::runtime_error("Failed to load nioxplugin.dll");
        }

        // Load function pointers
        createManager = (CreateManagerFunc)GetProcAddress(dllHandle, "createNioxBleManager");
        checkStatus = (CheckStatusFunc)GetProcAddress(dllHandle, "checkBleStatus");
        startScan = (StartScanFunc)GetProcAddress(dllHandle, "startScan");
        stopScan = (StopScanFunc)GetProcAddress(dllHandle, "stopScan");

        if (!createManager || !checkStatus || !startScan || !stopScan) {
            FreeLibrary(dllHandle);
            throw std::runtime_error("Failed to load DLL functions");
        }

        managerInstance = createManager(nullptr);
    }

    int getStatus() {
        return checkStatus(managerInstance);
    }

    void scan(long long durationMs) {
        startScan(managerInstance, durationMs, nullptr, nullptr, nullptr);
    }

    void stop() {
        stopScan(managerInstance);
    }

    ~NioxBleWrapper() {
        if (dllHandle) {
            FreeLibrary(dllHandle);
        }
    }
};

// Usage example
int main() {
    try {
        NioxBleWrapper ble(L"nioxplugin.dll");

        int status = ble.getStatus();
        std::cout << "BLE Status: " << status << std::endl;

        if (status == 0) { // ENABLED
            std::cout << "Starting scan..." << std::endl;
            ble.scan(10000); // 10 seconds

            // Wait for scan to complete
            Sleep(11000);

            ble.stop();
        }

    } catch (const std::exception& e) {
        std::cerr << "Error: " << e.what() << std::endl;
        return 1;
    }

    return 0;
}
```

### C# Integration

#### Step 1: Create P/Invoke Wrapper

```csharp
using System;
using System.Runtime.InteropServices;

namespace NioxBlePlugin
{
    public enum BleStatus
    {
        Enabled = 0,
        Disabled = 1,
        NotSupported = 2,
        PermissionDenied = 3,
        Unknown = 4
    }

    public class BleDevice
    {
        public string Name { get; set; }
        public string Address { get; set; }
        public int Rssi { get; set; }
    }

    public delegate void DeviceFoundCallback(string name, string address, int rssi);
    public delegate void ScanCompleteCallback(int deviceCount);
    public delegate void ErrorCallback(string message, int code);

    public class NioxBleManager : IDisposable
    {
        [DllImport("nioxplugin.dll", CallingConvention = CallingConvention.Cdecl)]
        private static extern IntPtr createNioxBleManager(IntPtr context);

        [DllImport("nioxplugin.dll", CallingConvention = CallingConvention.Cdecl)]
        private static extern int checkBleStatus(IntPtr manager);

        [DllImport("nioxplugin.dll", CallingConvention = CallingConvention.Cdecl)]
        private static extern void startScan(
            IntPtr manager,
            long durationMillis,
            DeviceFoundCallback onDeviceFound,
            ScanCompleteCallback onScanComplete,
            ErrorCallback onError
        );

        [DllImport("nioxplugin.dll", CallingConvention = CallingConvention.Cdecl)]
        private static extern void stopScan(IntPtr manager);

        private IntPtr managerHandle;

        public NioxBleManager()
        {
            managerHandle = createNioxBleManager(IntPtr.Zero);
            if (managerHandle == IntPtr.Zero)
            {
                throw new Exception("Failed to create BLE manager");
            }
        }

        public BleStatus CheckStatus()
        {
            int status = checkBleStatus(managerHandle);
            return (BleStatus)status;
        }

        public void StartScan(
            long durationMs,
            Action<string, string, int> onDeviceFound,
            Action<int> onComplete,
            Action<string, int> onError)
        {
            startScan(
                managerHandle,
                durationMs,
                (name, address, rssi) => onDeviceFound?.Invoke(name, address, rssi),
                (count) => onComplete?.Invoke(count),
                (message, code) => onError?.Invoke(message, code)
            );
        }

        public void StopScan()
        {
            stopScan(managerHandle);
        }

        public void Dispose()
        {
            if (managerHandle != IntPtr.Zero)
            {
                StopScan();
                // Note: Add proper cleanup if DLL provides a destroy function
            }
        }
    }
}
```

#### Step 2: Use the Wrapper

```csharp
using System;
using System.Collections.Generic;
using NioxBlePlugin;

class Program
{
    static void Main(string[] args)
    {
        using (var bleManager = new NioxBleManager())
        {
            var status = bleManager.CheckStatus();
            Console.WriteLine($"BLE Status: {status}");

            if (status == BleStatus.Enabled)
            {
                Console.WriteLine("Starting scan...");

                var devices = new List<BleDevice>();

                bleManager.StartScan(
                    durationMs: 10000,
                    onDeviceFound: (name, address, rssi) =>
                    {
                        Console.WriteLine($"Found: {name} ({address}) RSSI: {rssi}");
                        devices.Add(new BleDevice
                        {
                            Name = name,
                            Address = address,
                            Rssi = rssi
                        });
                    },
                    onComplete: (count) =>
                    {
                        Console.WriteLine($"Scan complete. Found {count} devices");
                    },
                    onError: (message, code) =>
                    {
                        Console.WriteLine($"Error: {message} (code: {code})");
                    }
                );

                // Wait for scan to complete
                System.Threading.Thread.Sleep(11000);

                Console.WriteLine($"\nTotal devices discovered: {devices.Count}");
                foreach (var device in devices)
                {
                    Console.WriteLine($"  - {device.Name}: {device.Address}");
                }
            }
            else
            {
                Console.WriteLine("Bluetooth is not available");
            }
        }

        Console.WriteLine("Press any key to exit...");
        Console.ReadKey();
    }
}
```

---

## Common Usage Patterns

### Pattern 1: Continuous Scanning

```kotlin
// Android/Kotlin example
class ContinuousScanManager(private val context: Context) {
    private val bleManager = createNioxBleManager(context)
    private var isScanning = false

    fun startContinuousScanning(onDeviceFound: (BleDevice) -> Unit) {
        if (isScanning) return

        isScanning = true
        performScan(onDeviceFound)
    }

    private fun performScan(onDeviceFound: (BleDevice) -> Unit) {
        if (!isScanning) return

        bleManager.startScan(
            durationMillis = 5000,
            onDeviceFound = onDeviceFound,
            onScanComplete = {
                // Wait a bit before starting next scan
                Handler(Looper.getMainLooper()).postDelayed({
                    performScan(onDeviceFound)
                }, 2000)
            },
            onError = { _, _ ->
                // Handle error and retry
                Handler(Looper.getMainLooper()).postDelayed({
                    performScan(onDeviceFound)
                }, 5000)
            }
        )
    }

    fun stopContinuousScanning() {
        isScanning = false
        bleManager.stopScan()
    }
}
```

### Pattern 2: Find Specific Device

```swift
// iOS/Swift example
func findDevice(withAddress address: String, timeout: TimeInterval = 30) {
    let startTime = Date()

    func scan() {
        bleManager?.startScan(
            durationMillis: 5000,
            onDeviceFound: { device in
                if device.address == address {
                    self.bleManager?.stopScan()
                    self.connectToDevice(device)
                }
            },
            onScanComplete: { _ in
                let elapsed = Date().timeIntervalSince(startTime)
                if elapsed < timeout {
                    // Continue scanning
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        scan()
                    }
                } else {
                    print("Device not found within timeout")
                }
            },
            onError: { message, _ in
                print("Scan error: \(message)")
            }
        )
    }

    scan()
}
```

### Pattern 3: Device Selection UI

```kotlin
// Android RecyclerView Adapter example
class BleDeviceAdapter : RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {
    private val devices = mutableListOf<BleDevice>()
    var onDeviceClick: ((BleDevice) -> Unit)? = null

    fun addDevice(device: BleDevice) {
        val index = devices.indexOfFirst { it.address == device.address }
        if (index >= 0) {
            devices[index] = device
            notifyItemChanged(index)
        } else {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun clear() {
        devices.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
        holder.itemView.setOnClickListener { onDeviceClick?.invoke(device) }
    }

    override fun getItemCount() = devices.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.deviceName)
        private val addressText: TextView = view.findViewById(R.id.deviceAddress)
        private val rssiText: TextView = view.findViewById(R.id.deviceRssi)

        fun bind(device: BleDevice) {
            nameText.text = device.name ?: "Unknown Device"
            addressText.text = device.address
            rssiText.text = "RSSI: ${device.rssi} dBm"
        }
    }
}
```

---

## Best Practices

1. **Always check BLE status** before attempting to scan
2. **Request permissions properly** on Android 12+ (separate Bluetooth and Location permissions)
3. **Handle errors gracefully** and provide user feedback
4. **Stop scanning** when not needed to conserve battery
5. **Use appropriate scan durations** (5-10 seconds typically)
6. **Deduplicate devices** by address when building UI lists
7. **Run callbacks on main thread** when updating UI
8. **Clean up resources** in `onDestroy()` / `deinit` / `Dispose()`

---

## Troubleshooting

### Common Issues

1. **No devices found**
   - Ensure Bluetooth is enabled
   - Check permissions are granted
   - Verify NIOX device is powered on and advertising
   - Check device name starts with "NIOX PRO"

2. **Permission errors**
   - Android: Request runtime permissions
   - iOS: Add usage descriptions to Info.plist
   - Check system Bluetooth permissions in Settings

3. **Build errors**
   - Ensure minimum SDK versions are met
   - Verify all dependencies are included
   - Clean and rebuild project

For more help, see the main [README.md](README.md) or contact support.
