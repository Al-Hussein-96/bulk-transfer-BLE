package com.alhussain.bulk_transfer.domain.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Voucher(
    val id: String,
    val code: String,
    val amount: String,
    val expiryDate: String
)
