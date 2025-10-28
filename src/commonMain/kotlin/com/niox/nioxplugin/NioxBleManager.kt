package com.niox.nioxplugin

import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import com.niox.nioxplugin.models.ScanResult

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
}

/**
 * Factory function to create platform-specific NioxBleManager instance
 */
expect fun createNioxBleManager(context: Any? = null): NioxBleManager
