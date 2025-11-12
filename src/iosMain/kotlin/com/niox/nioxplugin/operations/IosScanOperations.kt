package com.niox.nioxplugin.operations

import com.niox.nioxplugin.IosNioxBleManager
import com.niox.nioxplugin.NioxConstants
import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSTimer

internal class IosScanOperations(
    private val manager: IosNioxBleManager,
    private val discoveredDevices: MutableList<BleDevice>
) {

    fun startScan(
        durationMillis: Long,
        onDeviceFound: (BleDevice) -> Unit,
        onScanComplete: (List<BleDevice>) -> Unit,
        onError: (String, Int) -> Unit,
        onScanStarted: () -> Unit
    ) {
        val status = manager.checkBleStatus()
        if (status != BleStatus.ENABLED) {
            onError("BLE is not available. Status: $status", -1)
            return
        }

        discoveredDevices.clear()
        manager.delegate.onDeviceFound = onDeviceFound

        // Parse service UUID
        val serviceUUID = CBUUID.UUIDWithString(NioxConstants.SERVICE_UUID)

        // Start scanning with service UUIDs
        manager.centralManager.scanForPeripheralsWithServices(
            serviceUUIDs = listOf(serviceUUID),
            options = null
        )

        onScanStarted()

        // Schedule stop after duration
        NSTimer.scheduledTimerWithTimeInterval(
            interval = durationMillis / 1000.0,
            repeats = false
        ) { _ ->
            stopScan()
            onScanComplete(discoveredDevices.toList())
        }
    }

    fun stopScan() {
        manager.centralManager.stopScan()
        manager.delegate.onDeviceFound = null
    }
}
