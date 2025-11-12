package com.niox.nioxplugin

import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.ConnectionState
import com.niox.nioxplugin.platform.PlatformBleAdapter
import kotlinx.coroutines.*

/**
 * Connection manager in commonMain
 * Orchestrates the NIOX-specific connection flow using platform adapter
 */
internal class NioxConnectionManager(
    private val platformAdapter: PlatformBleAdapter
) {

    private var currentDevice: BleDevice? = null
    private var connectionState: ConnectionState = ConnectionState.DISCONNECTED

    // NIOX Characteristics
    private var serverConfigCharacteristic: String? = null
    private var clientConfigCharacteristic: String? = null
    private var notificationCharacteristic: String? = null
    private var indicationCharacteristic: String? = null
    private var writeCharacteristic: String? = null

    // Server Config Data (parsed from SERVER_CONFIG characteristic)
    private var fdcRxConfig: Byte = 0
    private var fdcTxConfig: Byte = 0
    private var maxRxPktCountPerAck: Byte = 0
    private var maxTxPktCountPerAck: Byte = 0
    private var notificationMode: Byte = 0 // 0x00 = notification, 0x01 = indication

    // Coroutine scope for retry operations
    private val scope = CoroutineScope(Dispatchers.Default)
    private var readRetryJob: Job? = null

    // User callbacks
    private var onConnectionStateChangedCallback: ((ConnectionState) -> Unit)? = null
    private var onConnectionErrorCallback: ((String, Int) -> Unit)? = null

    init {
        // Register universal callback to receive ALL characteristic value updates
        platformAdapter.setCharacteristicValueCallback { characteristicUuid, data ->
            handleCharacteristicValueUpdate(characteristicUuid, data)
        }
    }

    /**
     * Handle all characteristic value updates from platform
     * This receives both explicit reads and notifications/indications
     */
    private fun handleCharacteristicValueUpdate(characteristicUuid: String, data: ByteArray) {
        // Log or process all characteristic updates here in commonMain
        println("NioxConnectionManager: Received value from $characteristicUuid, size: ${data.size}")

        // You can add specific handling based on characteristic UUID here
        when {
            characteristicUuid.equals(NioxConstants.Characteristics.SERVER_CONFIG, ignoreCase = true) -> {
                println("Server Config data received")
            }
            characteristicUuid.equals(NioxConstants.Characteristics.NOTIFICATION, ignoreCase = true) -> {
                println("Notification channel data received")
            }
            characteristicUuid.equals(NioxConstants.Characteristics.INDICATION, ignoreCase = true) -> {
                println("Indication channel data received")
            }
        }
    }

    /**
     * Connect to a NIOX device with automatic service discovery and characteristic setup
     */
    fun connect(
        device: BleDevice,
        onConnectionStateChanged: (ConnectionState) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        // Validate not already connected
        if (connectionState == ConnectionState.CONNECTED ||
            connectionState == ConnectionState.CONNECTING) {
            onError("Already connected or connecting", -10)
            return
        }

        // Store callbacks
        this.onConnectionStateChangedCallback = onConnectionStateChanged
        this.onConnectionErrorCallback = onError
        this.currentDevice = device

        // Update state
        updateConnectionState(ConnectionState.CONNECTING)

        // Step 1: Connect to GATT
        platformAdapter.connectGatt(
            device = device,
            onConnectionStateChanged = { state ->
                handleConnectionStateChange(state)
            },
            onError = { message, code ->
                updateConnectionState(ConnectionState.FAILED)
                onError(message, code)
            }
        )
    }

    /**
     * Handle connection state changes from platform
     */
    private fun handleConnectionStateChange(state: ConnectionState) {
        updateConnectionState(state)

        when (state) {
            ConnectionState.CONNECTED -> {
                // Step 2: Discover services
                discoverServicesAndSetup()
            }
            ConnectionState.DISCONNECTED -> {
                cleanup()
            }
            ConnectionState.FAILED -> {
                onConnectionErrorCallback?.invoke("Connection failed", -15)
                cleanup()
            }
            else -> {
                // CONNECTING, DISCONNECTING - just update state
            }
        }
    }

    /**
     * Discover services and setup NIOX characteristics
     */
    private fun discoverServicesAndSetup() {
        platformAdapter.discoverServices(
            onServicesDiscovered = { serviceUuids ->
                // Verify NIOX service exists
                val hasNioxService = serviceUuids.any { uuid ->
                    uuid.equals(NioxConstants.SERVICE_UUID, ignoreCase = true)
                }

                if (hasNioxService) {
                    setupNioxCharacteristics()
                }
            },
            onError = { message, code ->
                onConnectionErrorCallback?.invoke(
                    "Service discovery failed: $message",
                    code
                )
            }
        )
    }

    /**
     * Setup NIOX-specific characteristics
     * iOS-style timing: Enable channels immediately, write config, then read server config
     */
    private fun setupNioxCharacteristics() {
        // Process each characteristic from the NIOX service
        NioxConstants.Characteristics.ALL.forEach { characteristicUuid: String ->
            when (characteristicUuid) {
                NioxConstants.Characteristics.SERVER_CONFIG -> {
                    serverConfigCharacteristic = characteristicUuid
                }
                NioxConstants.Characteristics.CLIENT_CONFIG -> {
                    clientConfigCharacteristic = characteristicUuid
                }
                NioxConstants.Characteristics.NOTIFICATION -> {
                    notificationCharacteristic = characteristicUuid
                }
                NioxConstants.Characteristics.INDICATION -> {
                    indicationCharacteristic = characteristicUuid
                }
                NioxConstants.Characteristics.WRITE -> {
                    writeCharacteristic = characteristicUuid
                }
                else -> {
                    // Unknown characteristic
                }
            }
        }


        enableNotifications()

        // Write initial config to Client Config characteristic
        clientConfigCharacteristic?.let { uuid ->
            writeInitialConfig(uuid)
        }

        // Read server config after channels are enabled
        serverConfigCharacteristic?.let { uuid ->
            readServerConfigWithRetry(uuid)
        }
    }

    /**
     * Read server config characteristic with retry mechanism
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun readServerConfigWithRetry(
        characteristicUuid: String,
        maxRetries: Int = 3,
        delayMs: Long = 100L
    ) {
        readRetryJob?.cancel()
        readRetryJob = scope.launch {
            var retryCount = 0
            var success = false

            while (retryCount < maxRetries && !success) {
                try {
                    // Suspend until read completes
                    val result = suspendCancellableCoroutine<Boolean> { continuation ->
                        platformAdapter.readCharacteristic(
                            serviceUuid = NioxConstants.SERVICE_UUID,
                            characteristicUuid = characteristicUuid,
                            onSuccess = { data ->
                                // Server config data received - parse it
                                parseServerConfig(data)

                                success = true
                                continuation.resume(true) { }
                            },
                            onError = { message, code ->
                                if (retryCount >= maxRetries - 1) {
                                    // Final retry failed
                                    onConnectionErrorCallback?.invoke(
                                        "Failed to read server config after $maxRetries attempts: $message",
                                        code
                                    )
                                }
                                continuation.resume(false) { }
                            }
                        )
                    }

                    if (!result) {
                        retryCount++
                        if (retryCount < maxRetries) {
                            delay(delayMs)
                        }
                    }
                } catch (e: CancellationException) {
                    // Job was cancelled
                    break
                } catch (e: Exception) {
                    retryCount++
                    if (retryCount >= maxRetries) {
                        onConnectionErrorCallback?.invoke(
                            "Failed to read server config: ${e.message}",
                            -40
                        )
                    } else {
                        delay(delayMs)
                    }
                }
            }
        }
    }

    /**
     * Stop any ongoing read retry operations
     */
    fun stopReadRetry() {
        readRetryJob?.cancel()
        readRetryJob = null
    }

    /**
     * Parse server config data from characteristic read
     */
    private fun parseServerConfig(data: ByteArray) {
        if (data.size < 7) {
            onConnectionErrorCallback?.invoke(
                "Invalid server config data size: ${data.size}",
                -41
            )
            return
        }

        // Parse byte[3]: FDC configuration
        fdcRxConfig = (data[3].toInt() and 0x0F).toByte()  // Lower nibble
        fdcTxConfig = ((data[3].toInt() shr 4) and 0x0F).toByte()  // Upper nibble

        // Parse byte[4]: Packet count thresholds for ACK
        maxRxPktCountPerAck = (data[4].toInt() and 0x0F).toByte()  // Lower nibble
        maxTxPktCountPerAck = ((data[4].toInt() shr 4) and 0x0F).toByte()  // Upper nibble

        // Parse byte[6]: Notification mode
        notificationMode = data[6]
    }

    /**
     * Enable BOTH notification AND indication channels (iOS approach)
     * Both channels are always enabled regardless of byte[6] value
     */
    private fun enableNotifications() {
        // Enable notification channel
        notificationCharacteristic?.let { charUuid ->
            platformAdapter.enableNotification(
                serviceUuid = NioxConstants.SERVICE_UUID,
                characteristicUuid = charUuid,
                onSuccess = {
                    // Notification channel enabled successfully
                },
                onError = { message, code ->
                    onConnectionErrorCallback?.invoke(
                        "Failed to enable notification channel: $message",
                        code
                    )
                }
            )
        }

        // Enable indication channel
        indicationCharacteristic?.let { charUuid ->
            platformAdapter.enableNotification(
                serviceUuid = NioxConstants.SERVICE_UUID,
                characteristicUuid = charUuid,
                onSuccess = {
                    // Indication channel enabled successfully
                },
                onError = { message, code ->
                    onConnectionErrorCallback?.invoke(
                        "Failed to enable indication channel: $message",
                        code
                    )
                }
            )
        }
    }

    /**
     * Write initial config to Client Config characteristic
     */
    private fun writeInitialConfig(characteristicUuid: String) {
        platformAdapter.writeCharacteristic(
            serviceUuid = NioxConstants.SERVICE_UUID,
            characteristicUuid = characteristicUuid,
            data = NioxConstants.configData,
            onSuccess = {
                // Initial config written successfully
            },
            onError = { message, code ->
                onConnectionErrorCallback?.invoke(
                    "Failed to write initial config: $message",
                    code
                )
            }
        )
    }

    /**
     * Update connection state and notify callbacks
     */
    private fun updateConnectionState(newState: ConnectionState) {
        connectionState = newState
        onConnectionStateChangedCallback?.invoke(newState)
    }

    /**
     * Disconnect from device
     */
    fun disconnect() {
        platformAdapter.disconnect()
    }

    /**
     * Get current connection state
     */
    fun getConnectionState(): ConnectionState {
        return connectionState
    }

    /**
     * Get connected device
     */
    fun getConnectedDevice(): BleDevice? {
        return currentDevice
    }

    /**
     * Cleanup on disconnect
     */
    private fun cleanup() {
        currentDevice = null
        onConnectionStateChangedCallback = null
        onConnectionErrorCallback = null

        // Cancel any ongoing retry operations
        stopReadRetry()

        // Clear characteristic references
        serverConfigCharacteristic = null
        clientConfigCharacteristic = null
        notificationCharacteristic = null
        indicationCharacteristic = null
        writeCharacteristic = null

        // Clear server config data
        fdcRxConfig = 0
        fdcTxConfig = 0
        maxRxPktCountPerAck = 0
        maxTxPktCountPerAck = 0
        notificationMode = 0
    }
}
