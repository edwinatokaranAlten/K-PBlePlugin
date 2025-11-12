package com.niox.nioxplugin

/**
 * Constants for NIOX device identification and configuration
 */
object NioxConstants {
    /**
     * Device name prefix for NIOX devices
     */
    const val DEVICE_NAME_PREFIX = "NIOX PRO"

    /**
     * Primary service UUID for NIOX devices
     * This is the main GATT service that contains all NIOX characteristics
     */
    const val SERVICE_UUID = "0000fc00-b8a4-4078-874c-14efbd4b510a"

    /**
     * TX Power service UUID
     */
    const val TX_POWER_SERVICE_UUID = "1804"

    /**
     * NIOX GATT Characteristic UUIDs
     * These characteristics are available within the main SERVICE_UUID
     */
    object Characteristics {
        /**
         * Server Configuration Characteristic (FC10)
         * Used for server-side configuration settings
         */
        const val SERVER_CONFIG = "0000fc10-b8a4-4078-874c-14efbd4b510a"

        /**
         * Client Configuration Characteristic (FC11)
         * Used for client-side configuration settings
         */
        const val CLIENT_CONFIG = "0000fc11-b8a4-4078-874c-14efbd4b510a"

        /**
         * Notification Characteristic (FC12)
         * Used for receiving notifications from the device
         */
        const val NOTIFICATION = "0000fc12-b8a4-4078-874c-14efbd4b510a"

        /**
         * Indication Characteristic (FC13)
         * Used for receiving indications from the device
         */
        const val INDICATION = "0000fc13-b8a4-4078-874c-14efbd4b510a"

        /**
         * Write Characteristic (FC14)
         * Used for writing data to the device
         */
        const val WRITE = "0000fc14-b8a4-4078-874c-14efbd4b510a"

        /**
         * All NIOX characteristic UUIDs as a list
         */
        val ALL = listOf(SERVER_CONFIG, CLIENT_CONFIG, NOTIFICATION, INDICATION, WRITE)
    }

    val configData: ByteArray = byteArrayOf(0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)


}
