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
import com.alhussain.bulk_transfer.domain.model.PinOrder
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBluetoothController(
    private val context: Context,
    private val moshi: Moshi,
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

    private val _receivedVouchers = MutableSharedFlow<List<PinOrder>>()
    override val receivedVouchers: SharedFlow<List<PinOrder>>
        get() = _receivedVouchers.asSharedFlow()

    private val _transferState = MutableStateFlow(TransferState())
    override val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private val deviceFoundReceiver =
        BluetoothDeviceReceiver { device ->
            _scannedDevices.update { devices ->
                val newDevice = device.toBluetoothDeviceDomain()
                if (newDevice in devices) devices else devices + newDevice
            }
        }

    private val bluetoothStateReceiver =
        BluetoothDeviceReceiver { device ->
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                updatePairedDevices()
            }
        }

    private var currentServerSocket: BluetoothServerSocket? = null
    private var currentClientSocket: BluetoothSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        const val SERVICE_UUID = "27b7d1da-08c7-4505-a4d1-24b998e8706c"
        private const val TAG = "BluetoothController"
    }

    init {
        updatePairedDevices()
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            },
        )
    }

    override fun startDiscovery() {
        if (!hasBluetoothScanPermission(context)) {
            Log.w(TAG, "Bluetooth scan permission not granted")
            return
        }
        context.registerReceiver(
            deviceFoundReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND),
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
        if (!hasBluetoothServerPermission(context)) {
            Log.e(TAG, "Missing Bluetooth server permissions")
            return
        }

        // Prevent multiple server instances
        if (currentServerSocket != null) {
            Log.w(TAG, "Server already running")
            return
        }

        serverJob =
            scope.launch {
                try {
                    currentServerSocket =
                        bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                            "BulkTransfer",
                            UUID.fromString(SERVICE_UUID),
                        )

                    Log.i(TAG, "Server started, listening for connections...")

                    while (currentServerSocket != null) {
                        val socket =
                            try {
                                Log.i(TAG, "Waiting for incoming connection...")
                                currentServerSocket?.accept()
                            } catch (e: IOException) {
                                Log.e(TAG, "Accept failed: ${e.message}")
                                if (currentServerSocket != null) {
                                    // Only emit error if we didn't intentionally close
                                    _errors.emit("Server error: ${e.message}")
                                }
                                null
                            }

                        socket?.let { clientSocket ->
                            Log.i(TAG, "Client connected: ${clientSocket.remoteDevice?.address}")
                            currentClientSocket = clientSocket
                            _isConnected.update { true }

                            val service = BluetoothDataTransferService(clientSocket, moshi)
                            dataTransferService = service

                            try {
                                // Server receives data, so it listens
                                _receivedVouchers.emitAll(
                                    service.listenForIncomingVouchers().onCompletion { error ->
                                        Log.i(TAG, "Transfer completed or connection closed: $error")

                                        // Delay to allow final ACK to be sent
                                        kotlinx.coroutines.delay(500)

                                        _isConnected.update { false }

                                        // Clean up this connection
                                        try {
                                            service.close()
                                            clientSocket.close()
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Error closing client socket: ${e.message}")
                                        }

                                        dataTransferService = null
                                        currentClientSocket = null
                                    },
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error receiving data: ${e.message}", e)
                                _isConnected.update { false }
                                _errors.emit("Receive error: ${e.message}")

                                try {
                                    service.close()
                                    clientSocket.close()
                                } catch (closeError: Exception) {
                                    Log.w(TAG, "Error closing after receive error: ${closeError.message}")
                                }

                                currentClientSocket = null
                                dataTransferService = null
                            }

                            // After one client disconnects, loop back to accept another
                            Log.i(TAG, "Ready to accept next connection...")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Server setup failed: ${e.message}", e)
                    _errors.emit("Server failed to start: ${e.message}")
                    currentServerSocket?.close()
                    currentServerSocket = null
                }
            }
    }

    override fun connectToDevice(device: BluetoothDeviceDomain) {
        connectToAddress(device.address)
    }

    override fun connectToAddress(address: String) {
        if (!hasBluetoothConnectPermission(context)) {
            Log.e(TAG, "Missing Bluetooth connect permission")
            return
        }

        scope.launch {
            _isConnected.update { false } // Reset state

            val bluetoothDevice =
                try {
                    bluetoothAdapter?.getRemoteDevice(address)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid MAC address: $address")
                    _errors.emit("Invalid MAC address")
                    null
                } ?: return@launch

            // Close any existing connection
            currentClientSocket?.close()
            currentClientSocket = null
            dataTransferService?.close()
            dataTransferService = null

            currentClientSocket =
                bluetoothDevice.createRfcommSocketToServiceRecord(
                    UUID.fromString(SERVICE_UUID),
                )

            stopDiscovery()

            currentClientSocket?.let { socket ->
                try {
                    Log.i(TAG, "Connecting to ${bluetoothDevice.address}...")

                    withTimeout(30000) {
                        // 30 second timeout
                        socket.connect()
                    }

                    Log.i(TAG, "Connected successfully to ${bluetoothDevice.address}")
                    _isConnected.update { true }

                    // Client is the SENDER - create service but DON'T listen
                    val service = BluetoothDataTransferService(socket, moshi)
                    dataTransferService = service

                    // Don't call listenForIncomingVouchers() here!
                    // This device will SEND data via sendVouchersWithProgress()
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Connection timeout to ${bluetoothDevice.address}")
                    socket.close()
                    currentClientSocket = null
                    _isConnected.update { false }
                    _errors.emit("Connection timeout")
                } catch (e: IOException) {
                    Log.e(TAG, "Connection failed to ${bluetoothDevice.address}: ${e.message}")
                    socket.close()
                    currentClientSocket = null
                    _isConnected.update { false }
                    _errors.emit("Connection failed: ${e.message}")
                }
            }
        }
    }

    override fun sendVouchersWithProgress(vouchers: List<PinOrder>) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            _transferState.update { it.copy(error = "No Bluetooth permission") }
            return
        }

        if (dataTransferService == null) {
            _transferState.update { it.copy(error = "No active connection") }
            Log.e(TAG, "Cannot send - no active connection")
            return
        }

        Log.i(TAG, "Starting to send ${vouchers.size} vouchers")

        scope.launch {
            try {
                dataTransferService?.sendVouchersWithAck(vouchers)?.collect { state ->
                    _transferState.emit(state)

                    if (state.error != null) {
                        _errors.emit(state.error)
                        Log.e(TAG, "Transfer error: ${state.error}")
                    }

                    if (!state.isTransferring && state.error == null && state.progress == 1f) {
                        Log.i(TAG, "Transfer completed successfully")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transfer failed: ${e.message}", e)
                _transferState.emit(TransferState(error = "Transfer failed: ${e.message}"))
                _errors.emit("Transfer failed: ${e.message}")
            }
        }
    }

    override fun clearState() {
        _transferState.update {
            TransferState()
        }
    }

    override fun sendVouchers(vouchers: List<PinOrder>) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "No Bluetooth permission for sending")
            return
        }
        if (dataTransferService == null) {
            Log.e(TAG, "No active connection for sending")
            return
        }

        Log.w(TAG, "Using legacy sendVouchers (no ACK) - consider using sendVouchersWithProgress instead")

        scope.launch {
            try {
                val service = dataTransferService
                if (service != null) {
                    _errors.emit("Legacy send method not supported - use sendVouchersWithProgress")
                }
            } catch (e: Exception) {
                _errors.emit("Failed to send vouchers: ${e.message}")
            }
        }
    }

    override fun closeConnection() {
        Log.i(TAG, "Closing client connection")

        try {
            dataTransferService?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing data transfer service: ${e.message}")
        }

        try {
            currentClientSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing client socket: ${e.message}")
        }

        currentClientSocket = null
        dataTransferService = null
        _isConnected.update { false }

        // DON'T close server socket here - let it keep listening for new connections
    }

    override fun stopServer() {
        Log.i(TAG, "Stopping server")

        serverJob?.cancel()
        serverJob = null

        try {
            currentServerSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket: ${e.message}")
        }

        currentServerSocket = null
    }

    override fun release() {
        Log.i(TAG, "Releasing Bluetooth controller")

        try {
            context.unregisterReceiver(deviceFoundReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering device receiver: ${e.message}")
        }

        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering state receiver: ${e.message}")
        }

        closeConnection()
        stopServer()
    }

    override fun getLocalAddress(): String? {
        // Warning: This may return null or 02:00:00:00:00:00 on newer Android versions
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

    private fun hasPermission(permission: String): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothScanPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH,
                ) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasBluetoothServerPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 8.1 - 11
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH,
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADMIN,
                ) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasBluetoothConnectPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADMIN,
                ) == PackageManager.PERMISSION_GRANTED
        }
}
