package com.alhussain.bulk_transfer.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.alhussain.bulk_transfer.domain.model.BluetoothDeviceDomain

@SuppressLint("MissingPermission")
fun BluetoothDevice.toBluetoothDeviceDomain(): BluetoothDeviceDomain {
    return BluetoothDeviceDomain(
        name = name,
        address = address
    )
}
