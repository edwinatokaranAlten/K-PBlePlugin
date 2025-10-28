package com.niox.nioxplugin.models

/**
 * Represents a discovered BLE device
 */
data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<String> = emptyList()
)
