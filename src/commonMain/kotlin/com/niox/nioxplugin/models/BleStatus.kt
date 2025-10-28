package com.niox.nioxplugin.models

/**
 * Represents the current BLE status on the device
 */
enum class BleStatus {
    /**
     * BLE is available and turned on
     */
    ENABLED,

    /**
     * BLE is available but turned off
     */
    DISABLED,

    /**
     * BLE is not supported on this device
     */
    NOT_SUPPORTED,

    /**
     * Permission to use BLE is not granted
     */
    PERMISSION_DENIED,

    /**
     * BLE status is unknown or still being determined
     */
    UNKNOWN
}
