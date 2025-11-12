package com.niox.nioxplugin

import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import com.niox.nioxplugin.models.ConnectionState

/**
 * Main BLE Manager interface for NIOX devices
 *
 * Device identification constants are defined in [NioxConstants]
 */
interface NioxBleManager {

    /**
     * Check the current BLE status on the device
     * @return Current BLE status
     */
    fun checkBleStatus(): BleStatus

    /**
     * Start scanning for NIOX devices
     * @param durationMillis Duration to scan in milliseconds (default 10 seconds)
     * @param onDeviceFound Callback invoked when a device is found
     * @param onScanComplete Callback invoked when scan completes with all found devices
     * @param onError Callback invoked if an error occurs
     */
    fun startScan(
        durationMillis: Long = 10000,
        onDeviceFound: (BleDevice) -> Unit = {},
        onScanComplete: (List<BleDevice>) -> Unit,
        onError: (String, Int) -> Unit = { _, _ -> }
    )

    /**
     * Stop an ongoing scan
     */
    fun stopScan()

    /**
     * Get list of currently discovered devices from the last scan
     * @return List of discovered BLE devices
     */
    fun getDiscoveredDevices(): List<BleDevice>

    /**
     * Connect to a specific BLE device
     * @param device The device to connect to (obtained from startScan)
     * @param onConnectionStateChanged Callback invoked when connection state changes
     * @param onServicesDiscovered Callback invoked when GATT services are discovered
     * @param onError Callback invoked if connection fails
     */
    fun connect(
        device: BleDevice,
        onConnectionStateChanged: (ConnectionState) -> Unit,
        onServicesDiscovered: (List<String>) -> Unit = {},
        onError: (String, Int) -> Unit = { _, _ -> }
    )

    /**
     * Disconnect from the currently connected device
     */
    fun disconnect()

    /**
     * Get the current connection state
     * @return Current connection state
     */
    fun getConnectionState(): ConnectionState

    /**
     * Get the currently connected device
     * @return Connected device or null if not connected
     */
    fun getConnectedDevice(): BleDevice?

    /**
     * Read data from a GATT characteristic
     * @param serviceUuid Service UUID containing the characteristic
     * @param characteristicUuid Characteristic UUID to read from
     * @param onSuccess Callback with the read data
     * @param onError Callback if read fails
     */
    fun readCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String, Int) -> Unit = { _, _ -> }
    )

    /**
     * Write data to a GATT characteristic
     * @param serviceUuid Service UUID containing the characteristic
     * @param characteristicUuid Characteristic UUID to write to
     * @param data Data to write
     * @param onSuccess Callback when write succeeds
     * @param onError Callback if write fails
     */
    fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        onSuccess: () -> Unit = {},
        onError: (String, Int) -> Unit = { _, _ -> }
    )

    /**
     * Enable notifications for a GATT characteristic
     * @param serviceUuid Service UUID containing the characteristic
     * @param characteristicUuid Characteristic UUID to enable notifications for
     * @param onNotification Callback invoked when notification is received
     * @param onError Callback if enabling notifications fails
     */
    fun enableNotifications(
        serviceUuid: String,
        characteristicUuid: String,
        onNotification: (ByteArray) -> Unit,
        onError: (String, Int) -> Unit = { _, _ -> }
    )

    /**
     * Disable notifications for a GATT characteristic
     * @param serviceUuid Service UUID containing the characteristic
     * @param characteristicUuid Characteristic UUID to disable notifications for
     */
    fun disableNotifications(
        serviceUuid: String,
        characteristicUuid: String
    )
}

/**
 * Factory function to create platform-specific NioxBleManager instance
 */
expect fun createNioxBleManager(context: Any? = null): NioxBleManager
