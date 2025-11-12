package com.niox.nioxplugin.delegates

import com.niox.nioxplugin.IosNioxBleManager
import com.niox.nioxplugin.NioxConstants
import com.niox.nioxplugin.models.BleDevice
import kotlinx.cinterop.*
import platform.CoreBluetooth.*
import platform.Foundation.*
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class IosCentralManagerDelegate(
    private val manager: IosNioxBleManager,
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
