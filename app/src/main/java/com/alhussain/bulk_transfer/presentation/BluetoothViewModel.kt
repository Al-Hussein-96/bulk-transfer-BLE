package com.alhussain.bulk_transfer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alhussain.bulk_transfer.domain.BluetoothController
import com.alhussain.bulk_transfer.domain.model.BluetoothDeviceDomain
import com.alhussain.bulk_transfer.domain.model.PinOrder
import com.alhussain.bulk_transfer.domain.model.PinOrdersData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothController: BluetoothController
) : ViewModel() {

    private val _state = MutableStateFlow(BluetoothUiState())
    val state = combine(
        bluetoothController.scannedDevices,
        bluetoothController.pairedDevices,
        _state
    ) { scannedDevices, pairedDevices, state ->
        state.copy(
            scannedDevices = scannedDevices,
            pairedDevices = pairedDevices,
            localAddress = bluetoothController.getLocalAddress()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BluetoothUiState())

    init {
        viewModelScope.launch {
            bluetoothController.isConnected.collect { isConnected ->
                _state.update { it.copy(isConnected = isConnected) }
            }
        }

        viewModelScope.launch {
            bluetoothController.errors.collect { error ->
                _state.update { it.copy(errorMessage = error) }
            }
        }

        viewModelScope.launch {
            bluetoothController.receivedVouchers.collect { vouchers ->
                _state.update { it.copy(receivedVouchers = it.receivedVouchers + vouchers) }
            }
        }
    }

    fun startScan() {
        bluetoothController.startDiscovery()
    }

    fun stopScan() {
        bluetoothController.stopDiscovery()
    }

    fun connectToDevice(device: BluetoothDeviceDomain) {
        _state.update { it.copy(isConnecting = true) }
        bluetoothController.connectToDevice(device)
    }

    fun connectToAddress(address: String) {
        _state.update { it.copy(isConnecting = true) }
        bluetoothController.connectToAddress(address)
    }

    fun disconnect() {
        bluetoothController.closeConnection()
        _state.update { it.copy(isConnected = false, isConnecting = false) }
    }

    fun sendVouchers() {
        val vouchers = PinOrdersData.generateDu10Items()
        bluetoothController.sendVouchers(vouchers)
    }

    fun waitForIncomingConnections() {
        _state.update { it.copy(isConnecting = true) }
        bluetoothController.startBluetoothServer()
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothController.release()
    }
}

data class BluetoothUiState(
    val scannedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val pairedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val receivedVouchers: List<PinOrder> = emptyList(),
    val localAddress: String? = null
)
