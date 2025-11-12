package com.niox.nioxplugin.platform

import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import com.niox.nioxplugin.models.ConnectionState

/**
 * Platform-specific BLE adapter interface
 * Implemented by each platform (Android, iOS, Windows) to provide native BLE operations
 */
interface PlatformBleAdapter {

    /**
     * Check BLE availability and status
     */
    fun checkBleStatus(): BleStatus


    /**
     * Connect to GATT server
     * @param device Device to connect to
     * @param onConnectionStateChanged Callback for connection state changes
     * @param onError Callback on error
     */
    fun connectGatt(
        device: BleDevice,
        onConnectionStateChanged: (ConnectionState) -> Unit,
        onError: (String, Int) -> Unit
    )

    /**
     * Discover services on connected GATT server
     * @param onServicesDiscovered Callback with discovered service UUIDs
     * @param onError Callback on error
     */
    fun discoverServices(
        onServicesDiscovered: (List<String>) -> Unit,
        onError: (String, Int) -> Unit
    )

    /**
     * Enable notifications for a characteristic
     * @param serviceUuid Service UUID
     * @param characteristicUuid Characteristic UUID
     * @param onSuccess Callback on success
     * @param onError Callback on error
     */
    fun enableNotification(
        serviceUuid: String,
        characteristicUuid: String,
        onSuccess: () -> Unit,
        onError: (String, Int) -> Unit
    )


    /**
     * Read characteristic value
     * @param serviceUuid Service UUID
     * @param characteristicUuid Characteristic UUID
     * @param onSuccess Callback with read data
     * @param onError Callback on error
     */
    fun readCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String, Int) -> Unit
    )

    /**
     * Write characteristic value
     * @param serviceUuid Service UUID
     * @param characteristicUuid Characteristic UUID
     * @param data Data to write
     * @param onSuccess Callback on success
     * @param onError Callback on error
     */
    fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        onSuccess: () -> Unit,
        onError: (String, Int) -> Unit
    )

    /**
     * Disconnect from GATT server
     */
    fun disconnect()

    /**
     * Register notification callback
     * @param characteristicUuid Characteristic UUID
     * @param onNotification Callback for notifications
     */
    fun setNotificationCallback(
        characteristicUuid: String,
        onNotification: (ByteArray) -> Unit
    )

    /**
     * Unregister notification callback
     * @param characteristicUuid Characteristic UUID
     */
    fun removeNotificationCallback(characteristicUuid: String)

    /**
     * Set a universal callback for ALL characteristic value updates
     * This receives both explicit reads and notifications/indications
     * @param onCharacteristicValueReceived Callback with (characteristicUuid, data)
     */
    fun setCharacteristicValueCallback(
        onCharacteristicValueReceived: (characteristicUuid: String, data: ByteArray) -> Unit
    )
}

/**
 * Factory function to create platform-specific BLE adapter
 */
expect fun createPlatformBleAdapter(context: Any? = null): PlatformBleAdapter
