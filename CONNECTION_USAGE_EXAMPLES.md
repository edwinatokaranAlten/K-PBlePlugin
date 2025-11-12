# BLE Connection Usage Examples

This document provides comprehensive examples of how to use the BLE connection functionality added to the NIOX Communication Plugin SDK.

## Table of Contents

1. [Overview](#overview)
2. [Android Examples](#android-examples)
3. [iOS Examples](#ios-examples)
4. [Windows Platform Note](#windows-platform-note)
5. [Complete Workflow](#complete-workflow)
6. [Error Codes Reference](#error-codes-reference)

---

## Overview

The SDK now supports full BLE connection capabilities including:

- **Device Connection**: Connect to discovered NIOX PRO devices
- **Service Discovery**: Automatically discover GATT services
- **Read Characteristics**: Read data from GATT characteristics
- **Write Characteristics**: Write data to GATT characteristics
- **Notifications**: Subscribe to characteristic value changes
- **Connection State Management**: Track connection status in real-time

### Platform Support

| Platform | Scanning | Connection | Read/Write | Notifications |
|----------|----------|------------|------------|---------------|
| Android  | ✅       | ✅         | ✅          | ✅            |
| iOS      | ✅       | ✅         | ✅          | ✅            |
| Windows  | ✅       | ❌         | ❌          | ❌            |

**Note**: Windows connection support requires modern WinRT APIs not currently available in this implementation.

---

## Android Examples

### Basic Connection Flow

```kotlin
import com.niox.nioxplugin.createNioxBleManager
import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import com.niox.nioxplugin.models.ConnectionState

class BleConnectionActivity : AppCompatActivity() {
    private lateinit var bleManager: NioxBleManager
    private var selectedDevice: BleDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleManager = createNioxBleManager(this)

        // Step 1: Scan for devices
        scanForDevices()
    }

    private fun scanForDevices() {
        bleManager.startScan(
            durationMillis = 10000,
            onDeviceFound = { device ->
                Log.d("BLE", "Found device: ${device.name}")
            },
            onScanComplete = { devices ->
                if (devices.isNotEmpty()) {
                    // Automatically connect to first device
                    connectToDevice(devices.first())
                }
            },
            onError = { message, code ->
                Log.e("BLE", "Scan error: $message")
            }
        )
    }

    private fun connectToDevice(device: BleDevice) {
        selectedDevice = device

        bleManager.connect(
            device = device,

            // Connection state callback
            onConnectionStateChanged = { state ->
                runOnUiThread {
                    when (state) {
                        ConnectionState.CONNECTING -> {
                            Log.d("BLE", "Connecting to ${device.name}...")
                            showConnectionProgress()
                        }
                        ConnectionState.CONNECTED -> {
                            Log.d("BLE", "Connected to ${device.name}")
                            hideConnectionProgress()
                            onDeviceConnected()
                        }
                        ConnectionState.DISCONNECTED -> {
                            Log.d("BLE", "Disconnected from ${device.name}")
                            onDeviceDisconnected()
                        }
                        ConnectionState.FAILED -> {
                            Log.e("BLE", "Connection failed")
                            showConnectionError()
                        }
                        else -> {}
                    }
                }
            },

            // Services discovered callback
            onServicesDiscovered = { serviceUuids ->
                runOnUiThread {
                    Log.d("BLE", "Discovered ${serviceUuids.size} services")
                    serviceUuids.forEach { uuid ->
                        Log.d("BLE", "  Service: $uuid")
                    }

                    // Now you can read/write characteristics
                    readDeviceData()
                }
            },

            // Error callback
            onError = { message, code ->
                runOnUiThread {
                    Log.e("BLE", "Connection error: $message (code: $code)")
                    Toast.makeText(this, "Connection failed: $message", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun onDeviceConnected() {
        // Device is connected, you can now interact with it
        binding.connectButton.text = "Disconnect"
        binding.connectButton.setOnClickListener {
            bleManager.disconnect()
        }
    }

    private fun onDeviceDisconnected() {
        binding.connectButton.text = "Connect"
        binding.connectButton.setOnClickListener {
            selectedDevice?.let { connectToDevice(it) }
        }
    }
}
```

### Reading Characteristics

```kotlin
private fun readDeviceData() {
    val serviceUuid = "000fc00b-8a4-4078-874c-14efbd4b510a"
    val characteristicUuid = "00002a00-0000-1000-8000-00805f9b34fb" // Example UUID

    bleManager.readCharacteristic(
        serviceUuid = serviceUuid,
        characteristicUuid = characteristicUuid,

        onSuccess = { data ->
            runOnUiThread {
                // Convert ByteArray to String or parse as needed
                val value = data.toString(Charsets.UTF_8)
                Log.d("BLE", "Read value: $value")

                // Or parse as hex
                val hexValue = data.joinToString(" ") { "%02X".format(it) }
                Log.d("BLE", "Read hex: $hexValue")

                displayData(value)
            }
        },

        onError = { message, code ->
            runOnUiThread {
                Log.e("BLE", "Read failed: $message")
                Toast.makeText(this, "Failed to read data", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
```

### Writing Characteristics

```kotlin
private fun writeCommand(command: ByteArray) {
    val serviceUuid = "000fc00b-8a4-4078-874c-14efbd4b510a"
    val characteristicUuid = "00002a01-0000-1000-8000-00805f9b34fb" // Example UUID

    bleManager.writeCharacteristic(
        serviceUuid = serviceUuid,
        characteristicUuid = characteristicUuid,
        data = command,

        onSuccess = {
            runOnUiThread {
                Log.d("BLE", "Write successful")
                Toast.makeText(this, "Command sent", Toast.LENGTH_SHORT).show()
            }
        },

        onError = { message, code ->
            runOnUiThread {
                Log.e("BLE", "Write failed: $message")
                Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

// Example: Send a start measurement command
private fun startMeasurement() {
    val command = byteArrayOf(0x01, 0x02, 0x03) // Replace with actual command
    writeCommand(command)
}
```

### Enabling Notifications

```kotlin
private fun subscribeToNotifications() {
    val serviceUuid = "000fc00b-8a4-4078-874c-14efbd4b510a"
    val characteristicUuid = "00002a02-0000-1000-8000-00805f9b34fb" // Notification characteristic

    bleManager.enableNotifications(
        serviceUuid = serviceUuid,
        characteristicUuid = characteristicUuid,

        // This callback is invoked whenever the device sends a notification
        onNotification = { data ->
            runOnUiThread {
                val value = data.joinToString(" ") { "%02X".format(it) }
                Log.d("BLE", "Notification received: $value")

                // Parse and display the measurement data
                val measurement = parseMeasurementData(data)
                updateUIWithMeasurement(measurement)
            }
        },

        onError = { message, code ->
            runOnUiThread {
                Log.e("BLE", "Failed to enable notifications: $message")
            }
        }
    )
}

private fun unsubscribeFromNotifications() {
    val serviceUuid = "000fc00b-8a4-4078-874c-14efbd4b510a"
    val characteristicUuid = "00002a02-0000-1000-8000-00805f9b34fb"

    bleManager.disableNotifications(serviceUuid, characteristicUuid)
}
```

### Complete NIOX Device Interaction Example

```kotlin
class NioxDeviceManager(private val context: Context) {

    private val bleManager = createNioxBleManager(context)
    private var connectedDevice: BleDevice? = null

    // NIOX-specific UUIDs (replace with actual values from NIOX documentation)
    companion object {
        const val NIOX_SERVICE_UUID = "000fc00b-8a4-4078-874c-14efbd4b510a"
        const val MEASUREMENT_CHAR_UUID = "00002a00-0000-1000-8000-00805f9b34fb"
        const val COMMAND_CHAR_UUID = "00002a01-0000-1000-8000-00805f9b34fb"
        const val NOTIFICATION_CHAR_UUID = "00002a02-0000-1000-8000-00805f9b34fb"
    }

    fun scanAndConnect(onConnected: () -> Unit, onError: (String) -> Unit) {
        bleManager.startScan(
            durationMillis = 10000,
            onDeviceFound = { device ->
                Log.d("NIOX", "Found device: ${device.name}")
            },
            onScanComplete = { devices ->
                if (devices.isEmpty()) {
                    onError("No NIOX devices found")
                    return@startScan
                }

                // Connect to first device
                connectToDevice(devices.first(), onConnected, onError)
            },
            onError = { message, _ ->
                onError("Scan failed: $message")
            }
        )
    }

    private fun connectToDevice(
        device: BleDevice,
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        bleManager.connect(
            device = device,
            onConnectionStateChanged = { state ->
                when (state) {
                    ConnectionState.CONNECTED -> {
                        connectedDevice = device
                        onConnected()
                    }
                    ConnectionState.FAILED, ConnectionState.DISCONNECTED -> {
                        connectedDevice = null
                    }
                    else -> {}
                }
            },
            onServicesDiscovered = { serviceUuids ->
                Log.d("NIOX", "Services discovered: ${serviceUuids.joinToString()}")
                setupNotifications()
            },
            onError = { message, _ ->
                onError("Connection failed: $message")
            }
        )
    }

    private fun setupNotifications() {
        bleManager.enableNotifications(
            serviceUuid = NIOX_SERVICE_UUID,
            characteristicUuid = NOTIFICATION_CHAR_UUID,
            onNotification = { data ->
                handleMeasurementData(data)
            },
            onError = { message, _ ->
                Log.e("NIOX", "Failed to enable notifications: $message")
            }
        )
    }

    fun startMeasurement(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (connectedDevice == null) {
            onError("Not connected to a device")
            return
        }

        // Example command - replace with actual NIOX protocol
        val startCommand = byteArrayOf(0x01, 0x00)

        bleManager.writeCharacteristic(
            serviceUuid = NIOX_SERVICE_UUID,
            characteristicUuid = COMMAND_CHAR_UUID,
            data = startCommand,
            onSuccess = {
                onSuccess()
            },
            onError = { message, _ ->
                onError("Failed to start measurement: $message")
            }
        )
    }

    fun readCurrentValue(onValue: (Double) -> Unit, onError: (String) -> Unit) {
        if (connectedDevice == null) {
            onError("Not connected to a device")
            return
        }

        bleManager.readCharacteristic(
            serviceUuid = NIOX_SERVICE_UUID,
            characteristicUuid = MEASUREMENT_CHAR_UUID,
            onSuccess = { data ->
                // Parse measurement value from bytes
                val value = parseMeasurement(data)
                onValue(value)
            },
            onError = { message, _ ->
                onError("Failed to read value: $message")
            }
        )
    }

    private fun parseMeasurement(data: ByteArray): Double {
        // Example parsing - adjust based on NIOX protocol
        if (data.size >= 4) {
            val intValue = (data[0].toInt() and 0xFF) or
                          ((data[1].toInt() and 0xFF) shl 8) or
                          ((data[2].toInt() and 0xFF) shl 16) or
                          ((data[3].toInt() and 0xFF) shl 24)
            return intValue / 100.0 // Example conversion
        }
        return 0.0
    }

    private fun handleMeasurementData(data: ByteArray) {
        val value = parseMeasurement(data)
        Log.d("NIOX", "Measurement update: $value")
        // Notify listeners, update UI, etc.
    }

    fun disconnect() {
        bleManager.disconnect()
        connectedDevice = null
    }

    fun getConnectionState(): ConnectionState {
        return bleManager.getConnectionState()
    }
}
```

---

## iOS Examples

### Basic Connection (Swift)

```swift
import NioxPlugin

class BLEConnectionManager: ObservableObject {
    @Published var connectionState: ConnectionState = .disconnected
    @Published var discoveredServices: [String] = []
    @Published var measurementValue: String = ""

    private var bleManager: NioxBleManager?
    private var connectedDevice: BleDevice?

    // NIOX service and characteristic UUIDs
    let SERVICE_UUID = "000fc00b-8a4-4078-874c-14efbd4b510a"
    let MEASUREMENT_CHAR = "00002a00-0000-1000-8000-00805f9b34fb"
    let COMMAND_CHAR = "00002a01-0000-1000-8000-00805f9b34fb"
    let NOTIFICATION_CHAR = "00002a02-0000-1000-8000-00805f9b34fb"

    init() {
        bleManager = NioxBleManagerKt.createNioxBleManager(context: nil)
    }

    func scanAndConnect() {
        bleManager?.startScan(
            durationMillis: 10000,
            onDeviceFound: { [weak self] device in
                print("Found device: \(device.name ?? "Unknown")")
            },
            onScanComplete: { [weak self] devices in
                guard let self = self, let firstDevice = devices.first else {
                    print("No devices found")
                    return
                }

                self.connectToDevice(device: firstDevice)
            },
            onError: { message, code in
                print("Scan error: \(message)")
            }
        )
    }

    func connectToDevice(device: BleDevice) {
        connectedDevice = device

        bleManager?.connect(
            device: device,

            onConnectionStateChanged: { [weak self] state in
                DispatchQueue.main.async {
                    self?.connectionState = state

                    switch state {
                    case .connecting:
                        print("Connecting...")
                    case .connected:
                        print("Connected!")
                    case .disconnected:
                        print("Disconnected")
                        self?.connectedDevice = nil
                    case .failed:
                        print("Connection failed")
                    default:
                        break
                    }
                }
            },

            onServicesDiscovered: { [weak self] serviceUuids in
                DispatchQueue.main.async {
                    self?.discoveredServices = serviceUuids
                    print("Discovered services: \(serviceUuids)")
                    self?.setupNotifications()
                }
            },

            onError: { message, code in
                print("Connection error: \(message)")
            }
        )
    }

    func setupNotifications() {
        bleManager?.enableNotifications(
            serviceUuid: SERVICE_UUID,
            characteristicUuid: NOTIFICATION_CHAR,
            onNotification: { [weak self] data in
                DispatchQueue.main.async {
                    let hexString = data.map { String(format: "%02X", $0) }.joined(separator: " ")
                    print("Notification: \(hexString)")
                    self?.handleMeasurementData(data: data)
                }
            },
            onError: { message, code in
                print("Failed to enable notifications: \(message)")
            }
        )
    }

    func readMeasurement() {
        bleManager?.readCharacteristic(
            serviceUuid: SERVICE_UUID,
            characteristicUuid: MEASUREMENT_CHAR,
            onSuccess: { [weak self] data in
                DispatchQueue.main.async {
                    let value = self?.parseMeasurement(data: data) ?? 0.0
                    self?.measurementValue = String(format: "%.2f", value)
                    print("Read measurement: \(value)")
                }
            },
            onError: { message, code in
                print("Read failed: \(message)")
            }
        )
    }

    func sendCommand(command: Data) {
        let byteArray = [UInt8](command)

        bleManager?.writeCharacteristic(
            serviceUuid: SERVICE_UUID,
            characteristicUuid: COMMAND_CHAR,
            data: KotlinByteArray(size: Int32(byteArray.count)) { index in
                return Int8(bitPattern: byteArray[Int(truncating: index)])
            },
            onSuccess: {
                print("Command sent successfully")
            },
            onError: { message, code in
                print("Write failed: \(message)")
            }
        )
    }

    private func handleMeasurementData(data: KotlinByteArray) {
        let value = parseMeasurement(data: data)
        measurementValue = String(format: "%.2f", value)
    }

    private func parseMeasurement(data: KotlinByteArray) -> Double {
        // Example parsing logic
        guard data.size >= 4 else { return 0.0 }

        // Convert to Swift array
        var bytes = [UInt8]()
        for i in 0..<Int(data.size) {
            bytes.append(UInt8(bitPattern: data.get(index: Int32(i))))
        }

        // Parse value (adjust based on actual NIOX protocol)
        let intValue = Int(bytes[0]) | (Int(bytes[1]) << 8) |
                       (Int(bytes[2]) << 16) | (Int(bytes[3]) << 24)
        return Double(intValue) / 100.0
    }

    func disconnect() {
        bleManager?.disconnect()
    }
}

// SwiftUI View
struct NioxDeviceView: View {
    @StateObject private var bleManager = BLEConnectionManager()

    var body: some View {
        VStack(spacing: 20) {
            Text("NIOX Device Connection")
                .font(.title)

            Text("Status: \(stateDescription)")
                .foregroundColor(stateColor)

            if bleManager.connectionState == .disconnected {
                Button("Scan and Connect") {
                    bleManager.scanAndConnect()
                }
                .buttonStyle(.borderedProminent)
            } else if bleManager.connectionState == .connected {
                VStack(spacing: 10) {
                    Text("Measurement: \(bleManager.measurementValue)")
                        .font(.headline)

                    Button("Read Value") {
                        bleManager.readMeasurement()
                    }

                    Button("Disconnect") {
                        bleManager.disconnect()
                    }
                    .foregroundColor(.red)
                }
            }

            if !bleManager.discoveredServices.isEmpty {
                VStack(alignment: .leading) {
                    Text("Services:")
                        .font(.headline)
                    ForEach(bleManager.discoveredServices, id: \.self) { uuid in
                        Text(uuid)
                            .font(.caption)
                    }
                }
            }
        }
        .padding()
    }

    private var stateDescription: String {
        switch bleManager.connectionState {
        case .disconnected: return "Disconnected"
        case .connecting: return "Connecting..."
        case .connected: return "Connected"
        case .disconnecting: return "Disconnecting..."
        case .failed: return "Failed"
        default: return "Unknown"
        }
    }

    private var stateColor: Color {
        switch bleManager.connectionState {
        case .connected: return .green
        case .failed: return .red
        case .connecting, .disconnecting: return .orange
        default: return .gray
        }
    }
}
```

---

## Windows Platform Note

The Windows implementation currently **does not support BLE connections**. The scanning functionality works, but GATT operations (connect, read, write, notifications) are not implemented.

**Reason**: The current Windows implementation uses classic Bluetooth APIs which do not provide access to BLE GATT services. Full support would require:

1. WinRT/UWP APIs (`Windows.Devices.Bluetooth.GenericAttributeProfile`)
2. COM/WinRT interop from Kotlin/Native
3. Or a JVM-based implementation

**Workaround**: For Windows desktop applications requiring full BLE functionality, consider:
- Using the Android AAR in a JVM-based Windows app
- Implementing a Windows-specific version using C#/UWP
- Using the SDK only for scanning on Windows and performing connections via network bridge

---

## Complete Workflow

### Typical Usage Pattern

```kotlin
// 1. Initialize
val bleManager = createNioxBleManager(context)

// 2. Check BLE status
when (bleManager.checkBleStatus()) {
    BleStatus.ENABLED -> startWorkflow()
    BleStatus.DISABLED -> promptEnableBluetooth()
    BleStatus.PERMISSION_DENIED -> requestPermissions()
    BleStatus.NOT_SUPPORTED -> showNotSupportedError()
    else -> {}
}

// 3. Scan for devices
bleManager.startScan(...)

// 4. Connect to device
bleManager.connect(device, ...)

// 5. Discover services (automatic in callback)

// 6. Read/Write/Subscribe
bleManager.readCharacteristic(...)
bleManager.writeCharacteristic(...)
bleManager.enableNotifications(...)

// 7. Disconnect when done
bleManager.disconnect()
```

---

## Error Codes Reference

### Connection Errors
- `-10`: Already connected or connecting
- `-11`: Bluetooth permission not granted (Android 12+)
- `-12`: Device not found
- `-13`: Security exception during connection
- `-14`: Failed to connect
- `-15`: Failed to discover services
- `-16`: Service discovery failed

### Read Errors
- `-20`: Not connected to a device
- `-21`: Service not found
- `-22`: Characteristic not found
- `-23`: Permission not granted
- `-24`: Failed to initiate read
- `-25`: Error reading characteristic
- `-26`: Read failed (GATT status error)

### Write Errors
- `-30`: Not connected to a device
- `-31`: Service not found
- `-32`: Characteristic not found
- `-33`: Permission not granted
- `-34`: Failed to initiate write
- `-35`: Error writing characteristic
- `-36`: Write failed (GATT status error)

### Notification Errors
- `-40`: Not connected to a device
- `-41`: Service not found
- `-42`: Characteristic not found
- `-43`: Permission not granted
- `-44`: Failed to enable notifications
- `-45`: Error enabling notifications

### Platform-Specific
- `-100`: Connection not implemented (Windows)
- `-110`: Read not implemented (Windows)
- `-120`: Write not implemented (Windows)
- `-130`: Notifications not implemented (Windows)

---

## Best Practices

1. **Always check connection state** before performing operations
2. **Handle disconnections gracefully** - devices can disconnect unexpectedly
3. **Unsubscribe from notifications** before disconnecting
4. **Use proper threading** - callbacks may come on background threads
5. **Parse data carefully** - validate byte array sizes before parsing
6. **Store device references** - keep track of connected devices
7. **Implement timeouts** - operations may hang, implement your own timeouts
8. **Clean up resources** - call `disconnect()` in Activity/Fragment `onDestroy()`

---

## Additional Resources

- See [README.md](README.md) for basic SDK documentation
- See [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) for platform-specific setup
- Refer to NIOX PRO device documentation for specific UUIDs and protocols
- Check Android/iOS BLE guides for platform-specific requirements

---

**Happy Coding! 🚀**
