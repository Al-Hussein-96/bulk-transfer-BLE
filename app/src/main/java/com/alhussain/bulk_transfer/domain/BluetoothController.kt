package com.alhussain.bulk_transfer.domain

import com.alhussain.bulk_transfer.domain.model.BluetoothDeviceDomain
import com.alhussain.bulk_transfer.domain.model.Voucher
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BluetoothController {
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val isConnected: StateFlow<Boolean>
    val errors: SharedFlow<String>
    val receivedVouchers: SharedFlow<List<Voucher>>

    fun startDiscovery()
    fun stopDiscovery()
    fun startBluetoothServer()
    fun connectToDevice(device: BluetoothDeviceDomain)
    fun connectToAddress(address: String)
    fun sendVouchers(vouchers: List<Voucher>)
    fun closeConnection()
    fun release()
    fun getLocalAddress(): String?
}
