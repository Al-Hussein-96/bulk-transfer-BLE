package com.alhussain.bulk_transfer.data.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.alhussain.bulk_transfer.domain.model.PinOrder
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class TransferMessageJsonAdapter(
    private val voucherListAdapter: JsonAdapter<List<PinOrder>>,
) : JsonAdapter<TransferMessage>() {
    @ToJson
    override fun toJson(
        writer: JsonWriter,
        value: TransferMessage?,
    ) {
        if (value == null) {
            writer.nullValue()
            return
        }

        writer.beginObject()

        when (value) {
            is TransferMessage.Data -> {
                writer.name("type").value("data")
                writer.name("vouchers")
                voucherListAdapter.toJson(writer, value.vouchers)
            }

            is TransferMessage.Ack -> {
                writer.name("type").value("ack")
                writer.name("success").value(value.success)
                writer.name("receivedCount").value(value.receivedCount)
                writer.name("message").value(value.message)
            }

            is TransferMessage.Progress -> {
                writer.name("type").value("progress")
                writer.name("current").value(value.current)
                writer.name("total").value(value.total)
            }

            TransferMessage.Complete -> {
                writer.name("type").value("complete")
            }
        }

        writer.endObject()
    }

    @FromJson
    override fun fromJson(reader: JsonReader): TransferMessage {
        var type: String? = null
        var success: Boolean? = null
        var receivedCount: Int? = null
        var message: String = ""
        var current: Int? = null
        var total: Int? = null
        var vouchers: List<PinOrder>? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> type = reader.nextString()
                "success" -> success = reader.nextBoolean()
                "receivedCount" -> receivedCount = reader.nextInt()
                "message" -> message = reader.nextString()
                "current" -> current = reader.nextInt()
                "total" -> total = reader.nextInt()
                "vouchers" -> vouchers = voucherListAdapter.fromJson(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return when (type) {
            "data" -> {
                TransferMessage.Data(vouchers.orEmpty())
            }

            "ack" -> {
                TransferMessage.Ack(
                    success = success ?: false,
                    receivedCount = receivedCount ?: 0,
                    message = message,
                )
            }

            "progress" -> {
                TransferMessage.Progress(
                    current = current ?: 0,
                    total = total ?: 0,
                )
            }

            "complete" -> {
                TransferMessage.Complete
            }

            else -> {
                throw JsonDataException("Unknown TransferMessage type: $type")
            }
        }
    }
}

sealed class TransferMessage {
    data class Data(
        val vouchers: List<PinOrder>,
    ) : TransferMessage()

    data class Ack(
        val success: Boolean,
        val receivedCount: Int,
        val message: String = "",
    ) : TransferMessage()

    data class Progress(
        val current: Int,
        val total: Int,
    ) : TransferMessage()

    object Complete : TransferMessage()
}

data class TransferState(
    val isTransferring: Boolean = false,
    val progress: Float = 0f,
    val currentItem: Int = 0,
    val totalItems: Int = 0,
    val error: String? = null,
)

class BluetoothDataTransferService(
    private val socket: BluetoothSocket,
    private val moshi: Moshi,
) {
    @OptIn(ExperimentalStdlibApi::class)
    private val messageAdapter = moshi.adapter<TransferMessage>()

    // Use simple BufferedReader/Writer instead of Okio for better control
    private val reader by lazy {
        BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
    }

    private val writer by lazy {
        BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
    }

    companion object {
        private const val CHUNK_SIZE = 10
        private const val ACK_TIMEOUT = 10000L
        private const val TAG = "BluetoothTransfer"
    }

    // ===== RECEIVER SIDE =====
    fun listenForIncomingVouchers(): Flow<List<PinOrder>> =
        flow {
            if (!socket.isConnected) {
                Log.w(TAG, "Socket not connected")
                return@flow
            }

            try {
                Log.i(TAG, "Starting to listen for incoming vouchers")

                while (true) {
                    val json = reader.readLine() ?: break
                    if (json.isBlank()) continue

                    Log.d(TAG, "Received message: ${json.take(100)}...")

                    when (val message = messageAdapter.fromJson(json)) {
                        is TransferMessage.Data -> {
                            Log.i(TAG, "Received ${message.vouchers.size} vouchers")
                            try {
                                emit(message.vouchers)
                                sendAck(true, message.vouchers.size, "Received successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing vouchers", e)
                                sendAck(false, 0, "Processing error: ${e.message}")
                            }
                        }

                        is TransferMessage.Complete -> {
                            Log.i(TAG, "Transfer completed")
                            sendAck(true, 0, "Transfer complete")
                            break
                        }

                        is TransferMessage.Ack -> {
                            // Receiver shouldn't get ACKs, log and skip
                            Log.w(TAG, "Receiver got unexpected ACK message")
                        }

                        is TransferMessage.Progress -> {
                            // Receiver shouldn't get Progress, log and skip
                            Log.w(TAG, "Receiver got unexpected Progress message")
                        }

                        else -> {
                            Log.w(TAG, "unexpected  message")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Connection error in receiver", e)
                throw e
            } finally {
                Log.i(TAG, "Receiver stopped listening")
            }
        }.flowOn(Dispatchers.IO)

    private fun sendAck(
        success: Boolean,
        count: Int,
        message: String,
    ) {
        try {
            val ack = TransferMessage.Ack(success, count, message)
            val json = messageAdapter.toJson(ack)

            synchronized(writer) {
                writer.write(json)
                writer.newLine()
                writer.flush()
            }

            Log.d(TAG, "Sent ACK: success=$success, count=$count")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send ACK", e)
            throw e
        }
    }

    // ===== SENDER SIDE =====
    suspend fun sendVouchersWithAck(vouchers: List<PinOrder>): Flow<TransferState> =
        flow {
            if (!socket.isConnected) {
                emit(TransferState(error = "Socket not connected"))
                return@flow
            }

            val totalChunks = (vouchers.size + CHUNK_SIZE - 1) / CHUNK_SIZE
            var sentCount = 0

            try {
                emit(TransferState(isTransferring = true, totalItems = vouchers.size))
                Log.i(TAG, "Starting transfer of ${vouchers.size} vouchers in $totalChunks chunks")

                vouchers.chunked(CHUNK_SIZE).forEachIndexed { chunkIndex, chunk ->
                    // Send chunk
                    val dataMessage = TransferMessage.Data(chunk)
                    val json = messageAdapter.toJson(dataMessage)

                    synchronized(writer) {
                        writer.write(json)
                        writer.newLine()
                        writer.flush()
                    }

                    Log.i(TAG, "Sent chunk ${chunkIndex + 1}/$totalChunks (${chunk.size} items)")

                    // Wait for ACK
                    val ackReceived =
                        withTimeoutOrNull(ACK_TIMEOUT) {
                            waitForAck()
                        }

                    when {
                        ackReceived == null -> {
                            Log.e(TAG, "ACK timeout for chunk ${chunkIndex + 1}")
                            emit(
                                TransferState(
                                    isTransferring = false,
                                    error = "ACK timeout for chunk ${chunkIndex + 1}",
                                ),
                            )
                            return@flow
                        }

                        !ackReceived.success -> {
                            Log.e(TAG, "Receiver reported error: ${ackReceived.message}")
                            emit(
                                TransferState(
                                    isTransferring = false,
                                    error = "Receiver error: ${ackReceived.message}",
                                ),
                            )
                            return@flow
                        }

                        else -> {
                            Log.d(TAG, "Received ACK for chunk ${chunkIndex + 1}")
                        }
                    }

                    sentCount += chunk.size
                    val progress = sentCount.toFloat() / vouchers.size

                    emit(
                        TransferState(
                            isTransferring = true,
                            progress = progress,
                            currentItem = sentCount,
                            totalItems = vouchers.size,
                        ),
                    )

                    delay(100) // Small delay between chunks
                }

                // Send completion message
                val completeMessage = TransferMessage.Complete
                val json = messageAdapter.toJson(completeMessage)

                synchronized(writer) {
                    writer.write(json)
                    writer.newLine()
                    writer.flush()
                }

                Log.i(TAG, "Sent completion message")

                // Wait for final ACK
                withTimeoutOrNull(ACK_TIMEOUT) {
                    waitForAck()
                }

                emit(
                    TransferState(
                        isTransferring = false,
                        progress = 1f,
                        currentItem = vouchers.size,
                        totalItems = vouchers.size,
                    ),
                )

                Log.i(TAG, "Transfer completed successfully")
            } catch (e: IOException) {
                Log.e(TAG, "Send error", e)
                emit(
                    TransferState(
                        isTransferring = false,
                        error = "Connection error: ${e.message}",
                    ),
                )
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun waitForAck(): TransferMessage.Ack {
        val json = reader.readLine() ?: throw IOException("No ACK received")
        Log.d(TAG, "Waiting for ACK, received: ${json.take(100)}...")

        val message = messageAdapter.fromJson(json)

        return when (message) {
            is TransferMessage.Ack -> {
                Log.d(TAG, "Parsed ACK: success=${message.success}")
                message
            }

            else -> {
                Log.e(TAG, "Expected ACK but got: $message")
                throw IOException("Expected ACK but got: $message")
            }
        }
    }

    fun close() {
        try {
            reader.close()
            writer.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing streams", e)
        }
    }
}
