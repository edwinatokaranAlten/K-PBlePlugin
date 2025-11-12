package com.niox.nioxplugin

import com.niox.nioxplugin.delegates.IosCentralManagerDelegate
import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import com.niox.nioxplugin.models.ConnectionState
import com.niox.nioxplugin.operations.IosScanOperations
import com.niox.nioxplugin.platform.createPlatformBleAdapter
import kotlinx.cinterop.*
import platform.CoreBluetooth.*
import platform.darwin.dispatch_get_main_queue

actual fun createNioxBleManager(context: Any?): NioxBleManager {
    return IosNioxBleManager()
}

@OptIn(ExperimentalForeignApi::class)
class IosNioxBleManager : NioxBleManager {

    private val platformAdapter = createPlatformBleAdapter()
    private val connectionManager = NioxConnectionManager(platformAdapter)

    private val discoveredDevices = mutableListOf<BleDevice>()
    private var isScanning = false

    // Keep CBCentralManager and delegate for scanning
    internal val centralManager: CBCentralManager
    internal val delegate: IosCentralManagerDelegate

    init {
        delegate = IosCentralManagerDelegate(
            manager = this,
            onDeviceDiscovered = { device ->
                val existingIndex = discoveredDevices.indexOfFirst { it.address == device.address }
                if (existingIndex >= 0) {
                    discoveredDevices[existingIndex] = device
                } else {
                    discoveredDevices.add(device)
                }
            }
        )
        centralManager = CBCentralManager(delegate, dispatch_get_main_queue())
    }

    // Scan operations remain platform-specific
    private val scanOperations = IosScanOperations(this, discoveredDevices)

    override fun checkBleStatus(): BleStatus {
        return platformAdapter.checkBleStatus()
    }

    override fun startScan(
        durationMillis: Long,
        onDeviceFound: (BleDevice) -> Unit,
        onScanComplete: (List<BleDevice>) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        if (isScanning) {
            onError("Scan already in progress", -2)
            return
        }
        isScanning = true
        scanOperations.startScan(durationMillis, onDeviceFound, onScanComplete, onError) {
            isScanning = false
        }
    }

    override fun stopScan() {
        if (!isScanning) return
        scanOperations.stopScan()
        isScanning = false
    }

    override fun getDiscoveredDevices(): List<BleDevice> {
        return discoveredDevices.toList()
    }

    override fun connect(
        device: BleDevice,
        onConnectionStateChanged: (ConnectionState) -> Unit,
        onServicesDiscovered: (List<String>) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        connectionManager.connect(
            device,
            onConnectionStateChanged,
            onError
        )
    }

    override fun disconnect() {
        connectionManager.disconnect()
    }

    override fun getConnectionState(): ConnectionState {
        return connectionManager.getConnectionState()
    }

    override fun getConnectedDevice(): BleDevice? {
        return connectionManager.getConnectedDevice()
    }

    override fun readCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        platformAdapter.readCharacteristic(
            serviceUuid,
            characteristicUuid,
            onSuccess,
            onError
        )
    }

    override fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        onSuccess: () -> Unit,
        onError: (String, Int) -> Unit
    ) {
        platformAdapter.writeCharacteristic(
            serviceUuid,
            characteristicUuid,
            data,
            onSuccess,
            onError
        )
    }

    override fun enableNotifications(
        serviceUuid: String,
        characteristicUuid: String,
        onNotification: (ByteArray) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        platformAdapter.setNotificationCallback(characteristicUuid, onNotification)
        platformAdapter.enableNotification(
            serviceUuid,
            characteristicUuid,
            onSuccess = {},
            onError
        )
    }

    override fun disableNotifications(serviceUuid: String, characteristicUuid: String) {
        platformAdapter.removeNotificationCallback(characteristicUuid)
        // Platform adapter doesn't expose disable notification yet, but callback is removed
    }
}
