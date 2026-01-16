package com.alhussain.bulk_transfer.domain.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PinOrder(
    val reqId: String,
    val customerRef: String?,
    val pinSerial: String,
    val pinCode: String?,
    val pinExpiry: String?,
    val status: String,
    val isStock: Int,
    val type: String,
    val terminalId: String,
    val productId: Int,
    val productName: String,
    val orderId: Int,
    val listPrice: Double,
    val commission: Double,
    val trx: Int?,
    val ean: String?,
    val orderDate: String,
    val merchantName: String,
    val customerId: String,
    val account: String?,
    val exTerminal: String?,
    val providerId: Int,
    val received: Double?,
    val tax: Double?,
    val provider: String,
    val logo: String,
    val printedDate: String?
)

object PinOrdersData{
     fun generateDu10Items(): List<PinOrder> {
        return (1..10).map { num ->
            PinOrder(
                reqId = "POS-PIN-19962027-D5KH5SEC6QKS73F6V2AG",
                customerRef = "1232676671-20260115161647",
                pinSerial = "DU10000$num",
                pinCode = "1000000${num}10$num",
                pinExpiry = "2027-06-09",
                status = "instock",
                isStock = 1,
                type = "PIN",
                terminalId = "1232676671",
                productId = 122,
                productName = "Du10",
                orderId = 10125094,
                listPrice = 10.0,
                commission = 0.0,
                trx = 10042684,
                ean = "6294013439955",
                orderDate = "2026-01-15 04:16:50 PM",
                merchantName = "AlHussain96",
                customerId = "19962027",
                account = "-",
                exTerminal = null,
                providerId = 1,
                received = -1.0,
                tax = -1.0,
                provider = "DU",
                logo = "https://stgapp.axiomwallet.com/gateway/api/v2/static/images/DU_logo.bmp",
                printedDate = null
            )
        }
    }
}
