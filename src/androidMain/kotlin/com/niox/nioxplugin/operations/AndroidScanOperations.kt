package com.niox.nioxplugin.operations

import android.Manifest
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.niox.nioxplugin.NioxConstants
import com.niox.nioxplugin.models.BleDevice
import java.util.UUID

internal class AndroidScanOperations(
    private val context: Context,
    private val bluetoothLeScanner: BluetoothLeScanner?,
    private val handler: Handler,
    private val discoveredDevices: MutableList<BleDevice>
) {

    private var scanCallback: ScanCallback? = null

    fun startScan(
        durationMillis: Long,
        onDeviceFound: (BleDevice) -> Unit,
        onScanComplete: (List<BleDevice>) -> Unit,
        onError: (String, Int) -> Unit,
        onScanStarted: () -> Unit,
        onScanStopped: () -> Unit
    ) {
        // Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                onError("BLUETOOTH_SCAN permission not granted", -3)
                return
            }
        }

        discoveredDevices.clear()

        // Create scan filters for NIOX devices
        val filters = mutableListOf<ScanFilter>()

        // Filter by service UUID
        try {
            val serviceUuid = ParcelUuid(UUID.fromString(NioxConstants.SERVICE_UUID))
            filters.add(
                ScanFilter.Builder()
                    .setServiceUuid(serviceUuid)
                    .build()
            )
        } catch (e: Exception) {
            // If UUID parsing fails, continue without service filter
        }

        // Configure scan settings
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name

                // Filter by name prefix
                if (deviceName != null && deviceName.startsWith(NioxConstants.DEVICE_NAME_PREFIX)) {
                    val serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()

                    val bleDevice = BleDevice(
                        name = deviceName,
                        address = device.address,
                        rssi = result.rssi,
                        serviceUuids = serviceUuids
                    )

                    // Check if device already exists in the list
                    val existingIndex = discoveredDevices.indexOfFirst { it.address == bleDevice.address }
                    if (existingIndex >= 0) {
                        // Update existing device (RSSI might have changed)
                        discoveredDevices[existingIndex] = bleDevice
                    } else {
                        // Add new device
                        discoveredDevices.add(bleDevice)
                        onDeviceFound(bleDevice)
                    }
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                for (result in results) {
                    onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                onScanStopped()
                onError("Scan failed with error code: $errorCode", errorCode)
            }
        }

        try {
            bluetoothLeScanner?.startScan(filters, settings, scanCallback)
            onScanStarted()

            // Schedule scan stop after duration
            handler.postDelayed({
                stopScan(onScanStopped)
                onScanComplete(discoveredDevices.toList())
            }, durationMillis)

        } catch (e: SecurityException) {
            onError("Security exception: ${e.message}", -4)
        } catch (e: Exception) {
            onError("Failed to start scan: ${e.message}", -5)
        }
    }

    fun stopScan(onScanStopped: () -> Unit) {
        try {
            val callback = scanCallback
            if (callback != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        bluetoothLeScanner?.stopScan(callback)
                    }
                } else {
                    bluetoothLeScanner?.stopScan(callback)
                }
            }
        } catch (e: Exception) {
            // Ignore exceptions during stop
        } finally {
            onScanStopped()
            scanCallback = null
        }
    }
}
