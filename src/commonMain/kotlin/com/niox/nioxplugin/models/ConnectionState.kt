package com.niox.nioxplugin.models

/**
 * Represents the connection state of a BLE device
 */
enum class ConnectionState {
    /**
     * Device is disconnected
     */
    DISCONNECTED,

    /**
     * Device is currently connecting
     */
    CONNECTING,

    /**
     * Device is connected
     */
    CONNECTED,

    /**
     * Device is currently disconnecting
     */
    DISCONNECTING,

    /**
     * Connection failed
     */
    FAILED
}
