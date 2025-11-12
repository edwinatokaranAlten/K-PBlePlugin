package com.niox.nioxplugin

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import com.niox.nioxplugin.models.ConnectionState
import com.niox.nioxplugin.operations.AndroidScanOperations
import com.niox.nioxplugin.platform.createPlatformBleAdapter

actual fun createNioxBleManager(context: Any?): NioxBleManager {
    require(context is Context) { "Android implementation requires a Context" }
    return AndroidNioxBleManager(context)
}

class AndroidNioxBleManager(private val context: Context) : NioxBleManager {

    private val platformAdapter = createPlatformBleAdapter(context)
    private val connectionManager = NioxConnectionManager(platformAdapter)

    private val discoveredDevices = mutableListOf<BleDevice>()
    private var isScanning = false

    // Initialize BLE scanner
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())

    // Scan operations remain platform-specific as they don't follow the same pattern
    private val scanOperations = AndroidScanOperations(
        context,
        bluetoothLeScanner,
        handler,
        discoveredDevices
    )

    override fun checkBleStatus(): BleStatus {
        return platformAdapter.checkBleStatus()
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

        scanOperations.startScan(
            durationMillis,
            onDeviceFound,
            onScanComplete,
            onError,
            onScanStarted = { isScanning = true },
            onScanStopped = { isScanning = false }
        )
    }

    override fun stopScan() {
        if (!isScanning) return
        scanOperations.stopScan { isScanning = false }
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
