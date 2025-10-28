package com.niox.nioxplugin.models

/**
 * Result of a BLE scan operation
 */
sealed class ScanResult {
    /**
     * Scan completed successfully with discovered devices
     */
    data class Success(val devices: List<BleDevice>) : ScanResult()

    /**
     * Scan failed with an error
     */
    data class Error(val message: String, val errorCode: Int = 0) : ScanResult()

    /**
     * Scan is in progress and a device was discovered
     */
    data class DeviceFound(val device: BleDevice) : ScanResult()
}
