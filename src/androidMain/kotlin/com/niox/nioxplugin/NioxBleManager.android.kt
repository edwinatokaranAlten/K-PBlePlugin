package com.niox.nioxplugin

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import java.util.UUID

actual fun createNioxBleManager(context: Any?): NioxBleManager {
    require(context is Context) { "Android implementation requires a Context" }
    return AndroidNioxBleManager(context)
}

class AndroidNioxBleManager(private val context: Context) : NioxBleManager {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val discoveredDevices = mutableListOf<BleDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private var scanCallback: ScanCallback? = null

    override fun checkBleStatus(): BleStatus {
        // Check if BLE is supported
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BleStatus.NOT_SUPPORTED
        }

        // Check if adapter is available
        if (bluetoothAdapter == null) {
            return BleStatus.NOT_SUPPORTED
        }

        // Check permissions based on Android version
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            return BleStatus.PERMISSION_DENIED
        }

        // Check if Bluetooth is enabled
        return if (bluetoothAdapter.isEnabled) {
            BleStatus.ENABLED
        } else {
            BleStatus.DISABLED
        }
    }

    override fun startScan(
        durationMillis: Long,
        onDeviceFound: (BleDevice) -> Unit,
        onScanComplete: (List<BleDevice>) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        // Check BLE status
        val status = checkBleStatus()
        if (status != BleStatus.ENABLED) {
            onError("BLE is not available. Status: $status", -1)
            return
        }

        if (isScanning) {
            onError("Scan already in progress", -2)
            return
        }

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
                isScanning = false
                onError("Scan failed with error code: $errorCode", errorCode)
            }
        }

        try {
            bluetoothLeScanner?.startScan(filters, settings, scanCallback)
            isScanning = true

            // Schedule scan stop after duration
            handler.postDelayed({
                stopScan()
                onScanComplete(discoveredDevices.toList())
            }, durationMillis)

        } catch (e: SecurityException) {
            onError("Security exception: ${e.message}", -4)
        } catch (e: Exception) {
            onError("Failed to start scan: ${e.message}", -5)
        }
    }

    override fun stopScan() {
        if (!isScanning) return

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
            isScanning = false
            scanCallback = null
        }
    }

    override fun getDiscoveredDevices(): List<BleDevice> {
        return discoveredDevices.toList()
    }
}
