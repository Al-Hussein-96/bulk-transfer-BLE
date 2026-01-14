package com.alhussain.bulk_transfer.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.alhussain.bulk_transfer.domain.BluetoothController
import com.alhussain.bulk_transfer.domain.model.BluetoothDeviceDomain
import com.alhussain.bulk_transfer.domain.model.Voucher
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBluetoothController(
    private val context: Context,
    private val moshi: Moshi
) : BluetoothController {

    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private var dataTransferService: BluetoothDataTransferService? = null

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _pairedDevices.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean>
        get() = _isConnected.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    override val errors: SharedFlow<String>
        get() = _errors.asSharedFlow()

    private val _receivedVouchers = MutableSharedFlow<List<Voucher>>()
    override val receivedVouchers: SharedFlow<List<Voucher>>
        get() = _receivedVouchers.asSharedFlow()

    private val deviceFoundReceiver = BluetoothDeviceReceiver { device ->
        _scannedDevices.update { devices ->
            val newDevice = device.toBluetoothDeviceDomain()
            if (newDevice in devices) devices else devices + newDevice
        }
    }

    private val bluetoothStateReceiver = BluetoothDeviceReceiver { device ->
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            updatePairedDevices()
        }
    }

    private var currentServerSocket: BluetoothServerSocket? = null
    private var currentClientSocket: BluetoothSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    init {
        updatePairedDevices()
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
        )
    }

    override fun startDiscovery() {
        if (!hasBluetoothScanPermission(context)) {
            Log.w("BT", "Bluetooth scan permission not granted")
            return
        }
        context.registerReceiver(
            deviceFoundReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )
        updatePairedDevices()
        bluetoothAdapter?.startDiscovery()
    }

    override fun stopDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }
        bluetoothAdapter?.cancelDiscovery()
    }

    override fun startBluetoothServer() {
        if (!hasBluetoothScanPermission(context)) {
            return
        }
        scope.launch {
            currentServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                "BulkTransfer",
                UUID.fromString(SERVICE_UUID)
            )
            var shouldLoop = true
            while (shouldLoop) {
                currentClientSocket = try {
                    currentServerSocket?.accept()
                } catch (e: IOException) {
                    shouldLoop = false
                    null
                }
                currentClientSocket?.let { socket ->
                    currentServerSocket?.close()
                    _isConnected.update { true }
                    val service = BluetoothDataTransferService(socket, moshi)
                    dataTransferService = service
                    _receivedVouchers.emitAll(
                        service.listenForIncomingVouchers().onCompletion {
                            closeConnection()
                        }
                    )
                }
            }
        }
    }

    override fun connectToDevice(device: BluetoothDeviceDomain) {
        connectToAddress(device.address)
    }

    override fun connectToAddress(address: String) {
        if (!hasBluetoothConnectPermission(context)) {
            return
        }
        scope.launch {
            val bluetoothDevice = try {
                bluetoothAdapter?.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                _errors.emit("Invalid MAC address")
                null
            } ?: return@launch
            
            currentClientSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString(SERVICE_UUID))
            stopDiscovery()
            currentClientSocket?.let { socket ->
                try {
                    socket.connect()
                    Log.w("AndroidBluetoothController","connecting to socket BLE")
                    _isConnected.update { true }
                    val service = BluetoothDataTransferService(socket, moshi)
                    Log.w("AndroidBluetoothController","getting service ${service.toString()}")

                    dataTransferService = service
                    _receivedVouchers.emitAll(
                        service.listenForIncomingVouchers().onCompletion {
                            closeConnection()
                        }
                    )
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.w("AndroidBluetoothController","Connection Exception ${e.toString()}")

                    socket.close()
                    currentClientSocket = null
                    _errors.emit("Connection failed")
                }
            }
        }
    }

    override fun sendVouchers(vouchers: List<Voucher>) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }
        if (dataTransferService == null) {
            return
        }
        scope.launch {
            val success = dataTransferService?.sendVouchers(vouchers) ?: false
            if (!success) {
                _errors.emit("Failed to send vouchers")
            }
        }
    }

    override fun closeConnection() {
        currentClientSocket?.close()
        currentServerSocket?.close()
        currentClientSocket = null
        currentServerSocket = null
        _isConnected.update { false }
    }

    override fun release() {
        try {
            context.unregisterReceiver(deviceFoundReceiver)
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {}
        closeConnection()
    }

    override fun getLocalAddress(): String? {
        // Warning: This may return null or 02:00:00:00:00:00 on newer Android versions.
        // In a real scenario, we might need to ask the user to provide it or use Bluetooth LE for discovery.
        return bluetoothAdapter?.address ?: bluetoothAdapter?.name
    }

    private fun updatePairedDevices() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }
        bluetoothAdapter
            ?.bondedDevices
            ?.map { it.toBluetoothDeviceDomain() }
            ?.let { devices ->
                _pairedDevices.update { devices }
            }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothScanPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        const val SERVICE_UUID = "27b7d1da-08c7-4505-a4d1-24b998e8706c"
    }
}
