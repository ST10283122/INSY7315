package com.example.insy_7315.models

import java.io.Serializable

data class Payment(
    val paymentId: Int,
    val invoiceId: Int,
    val bookingId: Int,
    val paymentReference: String,
    val paymentAmount: Double,
    val paymentMethod: String,
    val paymentType: String,
    val paymentStatus: String,
    val paymentDate: String,
    val transactionId: String?
) : Serializable