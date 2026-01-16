package com.alhussain.bulk_transfer.data.bluetooth

import android.bluetooth.BluetoothSocket
import com.alhussain.bulk_transfer.domain.model.PinOrder
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.buffer
import okio.source
import java.io.IOException

class BluetoothDataTransferService(
    private val socket: BluetoothSocket,
    private val moshi: Moshi
) {
    private val voucherListAdapter = moshi.adapter<List<PinOrder>>(
        Types.newParameterizedType(List::class.java, PinOrder::class.java)
    )

    fun listenForIncomingVouchers(): Flow<List<PinOrder>> = flow {
        if (!socket.isConnected) return@flow
        
        val source = socket.inputStream.source().buffer()
        
        try {
            while (!source.exhausted()) {
                val json = source.readUtf8Line() ?: break
                if (json.isBlank()) continue
                
                voucherListAdapter.fromJson(json)?.let {
                    emit(it)
                }
            }
        } catch (e: IOException) {
            // Connection closed
        } finally {
            try {
                source.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun sendVouchers(vouchers: List<PinOrder>): Boolean {
        return try {
            val json = voucherListAdapter.toJson(vouchers)
            // Use line-based protocol for simplicity and robustness with okio source.readUtf8Line()
            socket.outputStream.write((json + "\n").encodeToByteArray())
            socket.outputStream.flush()
            true
        } catch (e: IOException) {
            false
        }
    }
}
