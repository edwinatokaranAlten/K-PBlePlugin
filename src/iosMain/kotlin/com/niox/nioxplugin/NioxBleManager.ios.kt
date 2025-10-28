package com.niox.nioxplugin

import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import kotlinx.cinterop.*
import platform.CoreBluetooth.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

actual fun createNioxBleManager(context: Any?): NioxBleManager {
    return IosNioxBleManager()
}

@OptIn(ExperimentalForeignApi::class)
class IosNioxBleManager : NioxBleManager {

    private val centralManager: CBCentralManager
    private val delegate: BleDelegate
    private val discoveredDevices = mutableListOf<BleDevice>()
    private var isScanning = false

    init {
        delegate = BleDelegate(
            onDeviceDiscovered = { device ->
                val existingIndex = discoveredDevices.indexOfFirst { it.address == device.address }
                if (existingIndex >= 0) {
                    discoveredDevices[existingIndex] = device
                } else {
                    discoveredDevices.add(device)
                }
            }
        )
        centralManager = CBCentralManager(delegate, dispatch_get_main_queue())
    }

    override fun checkBleStatus(): BleStatus {
        return when (centralManager.state) {
            CBManagerStatePoweredOn -> BleStatus.ENABLED
            CBManagerStatePoweredOff -> BleStatus.DISABLED
            CBManagerStateUnsupported -> BleStatus.NOT_SUPPORTED
            CBManagerStateUnauthorized -> BleStatus.PERMISSION_DENIED
            CBManagerStateUnknown -> BleStatus.UNKNOWN
            else -> BleStatus.UNKNOWN
        }
    }

    override fun startScan(
        durationMillis: Long,
        onDeviceFound: (BleDevice) -> Unit,
        onScanComplete: (List<BleDevice>) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        val status = checkBleStatus()
        if (status != BleStatus.ENABLED) {
            onError("BLE is not available. Status: $status", -1)
            return
        }

        if (isScanning) {
            onError("Scan already in progress", -2)
            return
        }

        discoveredDevices.clear()
        delegate.onDeviceFound = onDeviceFound

        // Parse service UUID
        val serviceUUID = CBUUID.UUIDWithString(NioxConstants.SERVICE_UUID)

        // Start scanning with service UUIDs
        centralManager.scanForPeripheralsWithServices(
            serviceUUIDs = listOf(serviceUUID),
            options = null
        )

        isScanning = true

        // Schedule stop after duration
        NSTimer.scheduledTimerWithTimeInterval(
            interval = durationMillis / 1000.0,
            repeats = false
        ) { _ ->
            stopScan()
            onScanComplete(discoveredDevices.toList())
        }
    }

    override fun stopScan() {
        if (!isScanning) return

        centralManager.stopScan()
        isScanning = false
        delegate.onDeviceFound = null
    }

    override fun getDiscoveredDevices(): List<BleDevice> {
        return discoveredDevices.toList()
    }

    @OptIn(ExperimentalForeignApi::class)
    private class BleDelegate(
        private val onDeviceDiscovered: (BleDevice) -> Unit
    ) : NSObject(), CBCentralManagerDelegateProtocol {

        var onDeviceFound: ((BleDevice) -> Unit)? = null

        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            // State updates are handled by checkBleStatus()
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber
        ) {
            val peripheralName = didDiscoverPeripheral.name

            // Filter by name prefix
            if (peripheralName != null && peripheralName.startsWith(NioxConstants.DEVICE_NAME_PREFIX)) {

                // Extract service UUIDs from advertisement data
                @Suppress("UNCHECKED_CAST")
                val serviceUuids = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? List<CBUUID>)
                    ?.map { it.UUIDString }
                    ?: emptyList()

                val bleDevice = BleDevice(
                    name = peripheralName,
                    address = didDiscoverPeripheral.identifier.UUIDString,
                    rssi = RSSI.intValue,
                    serviceUuids = serviceUuids
                )

                onDeviceDiscovered(bleDevice)
                onDeviceFound?.invoke(bleDevice)
            }
        }

    }
}
