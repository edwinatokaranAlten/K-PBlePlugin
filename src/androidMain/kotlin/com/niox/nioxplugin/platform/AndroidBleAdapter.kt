package com.niox.nioxplugin.platform

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.niox.nioxplugin.NioxConstants
import com.niox.nioxplugin.models.BleDevice
import com.niox.nioxplugin.models.BleStatus
import com.niox.nioxplugin.models.ConnectionState
import java.util.UUID

actual fun createPlatformBleAdapter(context: Any?): PlatformBleAdapter {
    require(context is Context) { "Android implementation requires a Context" }
    return AndroidBleAdapter(context)
}

internal class AndroidBleAdapter(private val context: Context) : PlatformBleAdapter {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())
    private val discoveredDevices = mutableListOf<BleDevice>()
    private var isScanning = false

    // Connection-related fields
    private var bluetoothGatt: BluetoothGatt? = null
    private val notificationCallbacks = mutableMapOf<String, (ByteArray) -> Unit>()

    // Callbacks for connection
    private var onConnectionStateChangedCallback: ((ConnectionState) -> Unit)? = null
    private var onServicesDiscoveredCallback: ((List<String>) -> Unit)? = null
    private var onConnectionErrorCallback: ((String, Int) -> Unit)? = null

    // Callbacks for characteristics
    private var readCallback: ((ByteArray) -> Unit)? = null
    private var readErrorCallback: ((String, Int) -> Unit)? = null
    private var writeCallback: (() -> Unit)? = null
    private var writeErrorCallback: ((String, Int) -> Unit)? = null

    // Universal characteristic value callback - receives ALL value updates
    private var characteristicValueCallback: ((String, ByteArray) -> Unit)? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    onConnectionStateChangedCallback?.invoke(ConnectionState.CONNECTED)
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    onConnectionStateChangedCallback?.invoke(ConnectionState.DISCONNECTED)
                    try {
                        gatt.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                    bluetoothGatt = null
                    notificationCallbacks.clear()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val serviceUuids = gatt.services.map { it.uuid.toString() }
                onServicesDiscoveredCallback?.invoke(serviceUuids)
            } else {
                onConnectionErrorCallback?.invoke("Service discovery failed with status: $status", -16)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value
                }
                val byteArray = data ?: ByteArray(0)

                // Always send to universal callback first
                characteristicValueCallback?.invoke(characteristic.uuid.toString(), byteArray)

                // Then send to specific read callback
                readCallback?.invoke(byteArray)
            } else {
                readErrorCallback?.invoke("Characteristic read failed with status: $status", -26)
            }
            readCallback = null
            readErrorCallback = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeCallback?.invoke()
            } else {
                writeErrorCallback?.invoke("Characteristic write failed with status: $status", -36)
            }
            writeCallback = null
            writeErrorCallback = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                characteristic.value
            } else {
                @Suppress("DEPRECATION")
                characteristic.value
            }
            val byteArray = data ?: ByteArray(0)
            val uuid = characteristic.uuid.toString()

            // Always send to universal callback first
            characteristicValueCallback?.invoke(uuid, byteArray)

            // Then send to specific notification callback
            val callback = notificationCallbacks[uuid]
            callback?.invoke(byteArray)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            // Descriptor write completed (for enabling notifications)
        }
    }

    override fun checkBleStatus(): BleStatus {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BleStatus.NOT_SUPPORTED
        }

        if (bluetoothAdapter == null) {
            return BleStatus.NOT_SUPPORTED
        }

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

        return if (bluetoothAdapter.isEnabled) {
            BleStatus.ENABLED
        } else {
            BleStatus.DISABLED
        }
    }

    override fun connectGatt(
        device: BleDevice,
        onConnectionStateChanged: (ConnectionState) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        this.onConnectionStateChangedCallback = onConnectionStateChanged
        this.onConnectionErrorCallback = onError

        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        if (bluetoothDevice == null) {
            onError("Invalid device address", -11)
            return
        }

        try {
            onConnectionStateChanged(ConnectionState.CONNECTING)
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothDevice.connectGatt(context, false, gattCallback, 2) // TRANSPORT_LE = 2
            } else {
                bluetoothDevice.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            onError("Permission denied: ${e.message}", -12)
        } catch (e: Exception) {
            onError("Failed to connect: ${e.message}", -13)
        }
    }

    override fun discoverServices(
        onServicesDiscovered: (List<String>) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        this.onServicesDiscoveredCallback = onServicesDiscovered
        this.onConnectionErrorCallback = onError

        val gatt = bluetoothGatt
        if (gatt == null) {
            onError("Not connected to device", -14)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    gatt.discoverServices()
                } else {
                    onError("Permission denied for service discovery", -15)
                }
            } else {
                gatt.discoverServices()
            }
        } catch (e: Exception) {
            onError("Failed to discover services: ${e.message}", -15)
        }
    }

    override fun enableNotification(
        serviceUuid: String,
        characteristicUuid: String,
        onSuccess: () -> Unit,
        onError: (String, Int) -> Unit
    ) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            onError("Not connected to device", -27)
            return
        }

        val service = gatt.getService(UUID.fromString(serviceUuid))
        if (service == null) {
            onError("Service not found: $serviceUuid", -28)
            return
        }

        val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid))
        if (characteristic == null) {
            onError("Characteristic not found: $characteristicUuid", -29)
            return
        }

        try {
            gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor != null) {
                // Determine if this is notification or indication based on UUID
                val isIndication = characteristicUuid.equals(NioxConstants.Characteristics.INDICATION, ignoreCase = true)
                val descriptorValue = if (isIndication) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, descriptorValue)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = descriptorValue
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                onSuccess()
            } else {
                onError("CCCD descriptor not found", -30)
            }
        } catch (e: Exception) {
            onError("Failed to enable notification: ${e.message}", -31)
        }
    }

    override fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
        } catch (e: SecurityException) {
            // Ignore permission errors on disconnect
        } catch (e: Exception) {
            // Ignore other errors on disconnect
        }
    }

    override fun readCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String, Int) -> Unit
    ) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            onError("Not connected to device", -22)
            return
        }

        val service = gatt.getService(UUID.fromString(serviceUuid))
        if (service == null) {
            onError("Service not found: $serviceUuid", -23)
            return
        }

        val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid))
        if (characteristic == null) {
            onError("Characteristic not found: $characteristicUuid", -24)
            return
        }

        this.readCallback = onSuccess
        this.readErrorCallback = onError

        try {
            gatt.readCharacteristic(characteristic)
        } catch (e: SecurityException) {
            this.readCallback = null
            this.readErrorCallback = null
            onError("Permission denied: ${e.message}", -25)
        } catch (e: Exception) {
            this.readCallback = null
            this.readErrorCallback = null
            onError("Failed to read characteristic: ${e.message}", -26)
        }
    }

    override fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray,
        onSuccess: () -> Unit,
        onError: (String, Int) -> Unit
    ) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            onError("Not connected to device", -32)
            return
        }

        val service = gatt.getService(UUID.fromString(serviceUuid))
        if (service == null) {
            onError("Service not found: $serviceUuid", -33)
            return
        }

        val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid))
        if (characteristic == null) {
            onError("Characteristic not found: $characteristicUuid", -34)
            return
        }

        this.writeCallback = onSuccess
        this.writeErrorCallback = onError

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        } catch (e: SecurityException) {
            this.writeCallback = null
            this.writeErrorCallback = null
            onError("Permission denied: ${e.message}", -35)
        } catch (e: Exception) {
            this.writeCallback = null
            this.writeErrorCallback = null
            onError("Failed to write characteristic: ${e.message}", -36)
        }
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
}
