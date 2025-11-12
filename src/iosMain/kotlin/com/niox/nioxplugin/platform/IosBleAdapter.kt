package com.niox.nioxplugin.platform

import com.niox.nioxplugin.NioxConstants
import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import com.niox.nioxplugin.models.ConnectionState
import kotlinx.cinterop.*
import platform.CoreBluetooth.*
import platform.Foundation.*
import platform.darwin.NSObject

actual fun createPlatformBleAdapter(context: Any?): PlatformBleAdapter {
    return IosBleAdapter()
}

@OptIn(ExperimentalForeignApi::class)
internal class IosBleAdapter : PlatformBleAdapter {

    private val centralManager: CBCentralManager
    private val centralDelegate: IosCentralManagerDelegate

    private var currentPeripheral: CBPeripheral? = null
    private var peripheralDelegate: IosPeripheralDelegate? = null

    // Callbacks
    private var onConnectionStateChangedCallback: ((ConnectionState) -> Unit)? = null
    private var onServicesDiscoveredCallback: ((List<String>) -> Unit)? = null
    private var onConnectionErrorCallback: ((String, Int) -> Unit)? = null

    private val notificationCallbacks = mutableMapOf<String, (ByteArray) -> Unit>()

    // Universal characteristic value callback - receives ALL value updates
    internal var characteristicValueCallback: ((String, ByteArray) -> Unit)? = null

    init {
        centralDelegate = IosCentralManagerDelegate(this)
        centralManager = CBCentralManager(centralDelegate, null)
    }

    override fun checkBleStatus(): BleStatus {
        return when (centralManager.state) {
            CBManagerStatePoweredOn -> BleStatus.ENABLED
            CBManagerStatePoweredOff -> BleStatus.DISABLED
            CBManagerStateUnsupported -> BleStatus.NOT_SUPPORTED
            CBManagerStateUnauthorized -> BleStatus.PERMISSION_DENIED
            else -> BleStatus.DISABLED
        }
    }

    override fun connectGatt(
        device: BleDevice,
        onConnectionStateChanged: (ConnectionState) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        this.onConnectionStateChangedCallback = onConnectionStateChanged
        this.onConnectionErrorCallback = onError

        val peripheral = centralDelegate.getPeripheralByAddress(device.address)
        if (peripheral == null) {
            onError("Device not found: ${device.address}", -11)
            return
        }

        currentPeripheral = peripheral
        peripheralDelegate = IosPeripheralDelegate(this)
        peripheral.delegate = peripheralDelegate

        onConnectionStateChanged(ConnectionState.CONNECTING)
        centralManager.connectPeripheral(peripheral, null)
    }

    override fun discoverServices(
        onServicesDiscovered: (List<String>) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        this.onServicesDiscoveredCallback = onServicesDiscovered
        this.onConnectionErrorCallback = onError

        val peripheral = currentPeripheral
        if (peripheral == null) {
            onError("Not connected to device", -14)
            return
        }

        peripheral.discoverServices(null)
    }

    override fun enableNotification(
        serviceUuid: String,
        characteristicUuid: String,
        onSuccess: () -> Unit,
        onError: (String, Int) -> Unit
    ) {
        val peripheral = currentPeripheral
        if (peripheral == null) {
            onError("Not connected to device", -27)
            return
        }

        val service = peripheral.services?.firstOrNull { service ->
            (service as? CBService)?.UUID?.UUIDString?.equals(serviceUuid, ignoreCase = true) == true
        } as? CBService

        if (service == null) {
            onError("Service not found: $serviceUuid", -28)
            return
        }

        val characteristic = service.characteristics?.firstOrNull { char ->
            (char as? CBCharacteristic)?.UUID?.UUIDString?.equals(characteristicUuid, ignoreCase = true) == true
        } as? CBCharacteristic

        if (characteristic == null) {
            onError("Characteristic not found: $characteristicUuid", -29)
            return
        }

        peripheral.setNotifyValue(true, characteristic)
        onSuccess()
    }

    override fun disconnect() {
        val peripheral = currentPeripheral
        if (peripheral != null) {
            centralManager.cancelPeripheralConnection(peripheral)
        }
    }

    override fun readCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        val peripheral = currentPeripheral
        if (peripheral == null) {
            onError("Not connected to device", -22)
            return
        }

        val service = peripheral.services?.firstOrNull { service ->
            (service as? CBService)?.UUID?.UUIDString?.equals(serviceUuid, ignoreCase = true) == true
        } as? CBService

        if (service == null) {
            onError("Service not found: $serviceUuid", -23)
            return
        }

        val characteristic = service.characteristics?.firstOrNull { char ->
            (char as? CBCharacteristic)?.UUID?.UUIDString?.equals(characteristicUuid, ignoreCase = true) == true
        } as? CBCharacteristic

        if (characteristic == null) {
            onError("Characteristic not found: $characteristicUuid", -24)
            return
        }

        peripheralDelegate?.setReadCallback(characteristicUuid, onSuccess, onError)
        peripheral.readValueForCharacteristic(characteristic)
    }

    override fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        onSuccess: () -> Unit,
        onError: (String, Int) -> Unit
    ) {
        val peripheral = currentPeripheral
        if (peripheral == null) {
            onError("Not connected to device", -32)
            return
        }

        val service = peripheral.services?.firstOrNull { service ->
            (service as? CBService)?.UUID?.UUIDString?.equals(serviceUuid, ignoreCase = true) == true
        } as? CBService

        if (service == null) {
            onError("Service not found: $serviceUuid", -33)
            return
        }

        val characteristic = service.characteristics?.firstOrNull { char ->
            (char as? CBCharacteristic)?.UUID?.UUIDString?.equals(characteristicUuid, ignoreCase = true) == true
        } as? CBCharacteristic

        if (characteristic == null) {
            onError("Characteristic not found: $characteristicUuid", -34)
            return
        }

        peripheralDelegate?.setWriteCallback(characteristicUuid, onSuccess, onError)

        val nsData = data.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
        }

        peripheral.writeValue(nsData, characteristic, CBCharacteristicWriteWithResponse)
    }

    override fun setNotificationCallback(
        characteristicUuid: String,
        onNotification: (ByteArray) -> Unit
    ) {
        notificationCallbacks[characteristicUuid] = onNotification
    }

    override fun removeNotificationCallback(characteristicUuid: String) {
        notificationCallbacks.remove(characteristicUuid)
    }

    override fun setCharacteristicValueCallback(
        onCharacteristicValueReceived: (characteristicUuid: String, data: ByteArray) -> Unit
    ) {
        characteristicValueCallback = onCharacteristicValueReceived
    }

    // Internal methods called by delegates
    internal fun handleConnectionStateChange(state: ConnectionState) {
        onConnectionStateChangedCallback?.invoke(state)

        if (state == ConnectionState.DISCONNECTED) {
            currentPeripheral = null
            peripheralDelegate = null
            notificationCallbacks.clear()
        }
    }

    internal fun handleServicesDiscovered(serviceUuids: List<String>) {
        onServicesDiscoveredCallback?.invoke(serviceUuids)
    }

    internal fun handleNotification(characteristicUuid: String, data: ByteArray) {
        notificationCallbacks[characteristicUuid]?.invoke(data)
    }

    internal fun handleConnectionError(message: String, code: Int) {
        onConnectionErrorCallback?.invoke(message, code)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosCentralManagerDelegate(
    private val adapter: IosBleAdapter
) : NSObject(), CBCentralManagerDelegateProtocol {

    private val discoveredPeripherals = mutableMapOf<String, CBPeripheral>()

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        // State updates handled by checkBleStatus()
    }

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral
    ) {
        adapter.handleConnectionStateChange(ConnectionState.CONNECTED)
    }

    @Suppress("CONFLICTING_OVERLOADS")
    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        adapter.handleConnectionStateChange(ConnectionState.DISCONNECTED)
        if (error != null) {
            adapter.handleConnectionError(
                "Disconnected: ${error.localizedDescription}",
                error.code.toInt()
            )
        }
    }

    @Suppress("CONFLICTING_OVERLOADS")
    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        adapter.handleConnectionStateChange(ConnectionState.FAILED)
        adapter.handleConnectionError(
            "Connection failed: ${error?.localizedDescription ?: "Unknown error"}",
            error?.code?.toInt() ?: -1
        )
    }

    fun getPeripheralByAddress(address: String): CBPeripheral? {
        return discoveredPeripherals[address]
    }

    fun addDiscoveredPeripheral(address: String, peripheral: CBPeripheral) {
        discoveredPeripherals[address] = peripheral
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosPeripheralDelegate(
    private val adapter: IosBleAdapter
) : NSObject(), CBPeripheralDelegateProtocol {

    private val readCallbacks = mutableMapOf<String, Pair<(ByteArray) -> Unit, (String, Int) -> Unit>>()
    private val writeCallbacks = mutableMapOf<String, Pair<() -> Unit, (String, Int) -> Unit>>()

    fun setReadCallback(
        characteristicUuid: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        readCallbacks[characteristicUuid] = Pair(onSuccess, onError)
    }

    fun setWriteCallback(
        characteristicUuid: String,
        onSuccess: () -> Unit,
        onError: (String, Int) -> Unit
    ) {
        writeCallbacks[characteristicUuid] = Pair(onSuccess, onError)
    }

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        if (didDiscoverServices != null) {
            adapter.handleConnectionError(
                "Service discovery failed: ${didDiscoverServices.localizedDescription}",
                didDiscoverServices.code.toInt()
            )
            return
        }

        val serviceUuids = peripheral.services?.mapNotNull { (it as? CBService)?.UUID?.UUIDString } ?: emptyList()

        // Discover characteristics for all services
        peripheral.services?.forEach { service ->
            val cbService = service as? CBService
            if (cbService != null) {
                peripheral.discoverCharacteristics(null, cbService)
            }
        }

        adapter.handleServicesDiscovered(serviceUuids)
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?
    ) {
        if (error != null) {
            return
        }

        // Characteristics discovered - nothing specific to do here
        // Auto-enabling notifications is now handled by commonMain via enableNotification()
    }

    @Suppress("CONFLICTING_OVERLOADS")
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        val uuid = didUpdateValueForCharacteristic.UUID.UUIDString

        if (error != null) {
            val callback = readCallbacks.remove(uuid)
            callback?.second?.invoke(
                "Failed to read characteristic: ${error.localizedDescription}",
                error.code.toInt()
            )
            return
        }

        val data = didUpdateValueForCharacteristic.value
        val byteArray = if (data != null) {
            ByteArray(data.length.toInt()).apply {
                usePinned { pinned ->
                    platform.posix.memcpy(pinned.addressOf(0), data.bytes, data.length)
                }
            }
        } else {
            ByteArray(0)
        }

        // Always send to universal callback first
        adapter.characteristicValueCallback?.invoke(uuid, byteArray)

        // Check if this is a read operation or notification
        val readCallback = readCallbacks.remove(uuid)
        if (readCallback != null) {
            readCallback.first.invoke(byteArray)
        } else {
            // It's a notification
            adapter.handleNotification(uuid, byteArray)
        }
    }

    @Suppress("CONFLICTING_OVERLOADS")
    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        val uuid = didWriteValueForCharacteristic.UUID.UUIDString
        val callback = writeCallbacks.remove(uuid)

        if (error != null) {
            callback?.second?.invoke(
                "Failed to write characteristic: ${error.localizedDescription}",
                error.code.toInt()
            )
        } else {
            callback?.first?.invoke()
        }
    }

    // Note: didUpdateNotificationStateForCharacteristic callback is not implemented here
    // due to Kotlin/Native method signature conflicts with didUpdateValueForCharacteristic
    // and didWriteValueForCharacteristic. The notification state changes are handled
    // implicitly by CoreBluetooth, and the existing flow works correctly without this callback.
    //
    // If needed in the future, this can be implemented using @ObjCSignatureOverride annotation
    // or by creating a separate wrapper class for this specific delegate method.
}
