package com.niox.nioxplugin

import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import kotlinx.cinterop.*
import platform.windows.*
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze

actual fun createNioxBleManager(context: Any?): NioxBleManager {
    return WindowsNioxBleManager()
}

@OptIn(ExperimentalForeignApi::class)
class WindowsNioxBleManager : NioxBleManager {

    private val discoveredDevices = mutableListOf<BleDevice>()
    private var isScanning = false
    private var scanHandle: HANDLE? = null

    override fun checkBleStatus(): BleStatus {
        // Check if Bluetooth radio is available and enabled on Windows
        memScoped {
            val findParams = alloc<BLUETOOTH_FIND_RADIO_PARAMS>()
            findParams.dwSize = sizeOf<BLUETOOTH_FIND_RADIO_PARAMS>().toUInt()

            val radioHandle = alloc<HANDLEVar>()
            val findHandle = BluetoothFindFirstRadio(findParams.ptr, radioHandle.ptr)

            if (findHandle == null) {
                // No Bluetooth radio found
                return BleStatus.NOT_SUPPORTED
            }

            // Get radio info to check if it's enabled
            val radioInfo = alloc<BLUETOOTH_RADIO_INFO>()
            radioInfo.dwSize = sizeOf<BLUETOOTH_RADIO_INFO>().toUInt()

            val result = BluetoothGetRadioInfo(radioHandle.value, radioInfo.ptr)

            // Close handles
            CloseHandle(radioHandle.value)
            BluetoothFindRadioClose(findHandle)

            if (result != ERROR_SUCCESS.toInt()) {
                return BleStatus.UNKNOWN
            }

            // Check if radio is enabled (not in airplane mode or disabled)
            return if (radioInfo.fEnabled != 0u) {
                BleStatus.ENABLED
            } else {
                BleStatus.DISABLED
            }
        }
    }

    override fun startScan(
        durationMillis: Long,
        onDeviceFound: (BleDevice) -> Unit,
        onScanComplete: (List<BleDevice>) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        val status = checkBleStatus()
        if (status != BleStatus.ENABLED) {
            onError("BLE is not available. Status: $status", -1)
            return
        }

        if (isScanning) {
            onError("Scan already in progress", -2)
            return
        }

        discoveredDevices.clear()
        isScanning = true

        // Use Windows Bluetooth LE APIs to scan for devices
        memScoped {
            val searchParams = alloc<BLUETOOTH_DEVICE_SEARCH_PARAMS>()
            searchParams.dwSize = sizeOf<BLUETOOTH_DEVICE_SEARCH_PARAMS>().toUInt()
            searchParams.fReturnAuthenticated = 1
            searchParams.fReturnRemembered = 1
            searchParams.fReturnUnknown = 1
            searchParams.fReturnConnected = 1
            searchParams.fIssueInquiry = 1
            searchParams.cTimeoutMultiplier = 2u

            val deviceInfo = alloc<BLUETOOTH_DEVICE_INFO>()
            deviceInfo.dwSize = sizeOf<BLUETOOTH_DEVICE_INFO>().toUInt()

            val findHandle = BluetoothFindFirstDevice(searchParams.ptr, deviceInfo.ptr)

            if (findHandle == null) {
                isScanning = false
                onError("Failed to start BLE scan", GetLastError().toInt())
                return
            }

            // First device
            processDevice(deviceInfo, onDeviceFound)

            // Continue finding devices
            while (BluetoothFindNextDevice(findHandle, deviceInfo.ptr) != 0) {
                processDevice(deviceInfo, onDeviceFound)
            }

            BluetoothFindDeviceClose(findHandle)
        }

        isScanning = false
        onScanComplete(discoveredDevices.toList())
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun processDevice(
        deviceInfo: BLUETOOTH_DEVICE_INFO,
        onDeviceFound: (BleDevice) -> Unit
    ) {
        // Get device name
        val deviceName = deviceInfo.szName.toKString()

        // Filter by name prefix
        if (deviceName.startsWith(NioxConstants.DEVICE_NAME_PREFIX)) {
            // Convert Bluetooth address to string
            val address = deviceInfo.Address.run {
                "%02X:%02X:%02X:%02X:%02X:%02X".format(
                    ullLong and 0xFF,
                    (ullLong shr 8) and 0xFF,
                    (ullLong shr 16) and 0xFF,
                    (ullLong shr 24) and 0xFF,
                    (ullLong shr 32) and 0xFF,
                    (ullLong shr 40) and 0xFF
                )
            }

            val bleDevice = BleDevice(
                name = deviceName,
                address = address,
                rssi = -60, // RSSI not directly available in classic Bluetooth API
                serviceUuids = emptyList() // Service UUIDs would need GATT enumeration
            )

            // Check if device already exists
            val existingIndex = discoveredDevices.indexOfFirst { it.address == bleDevice.address }
            if (existingIndex >= 0) {
                discoveredDevices[existingIndex] = bleDevice
            } else {
                discoveredDevices.add(bleDevice)
                onDeviceFound(bleDevice)
            }
        }
    }

    override fun stopScan() {
        isScanning = false
        scanHandle?.let {
            // Clean up scan handle if needed
        }
        scanHandle = null
    }

    override fun getDiscoveredDevices(): List<BleDevice> {
        return discoveredDevices.toList()
    }
}
