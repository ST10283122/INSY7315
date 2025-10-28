package com.example.insy_7315.models

import java.io.Serializable

data class Invoice(
    val invoiceId: Int,
    val bookingId: Int,
    val invoiceNumber: String,
    val invoiceDate: String,
    val dueDate: String?,
    val totalAmount: Double,
    val taxAmount: Double,
    val discountAmount: Double,
    val invoiceStatus: String,
    val notes: String?
) : Serializable