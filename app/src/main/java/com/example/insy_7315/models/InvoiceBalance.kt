package com.example.insy_7315.models

data class InvoiceBalance(
    val invoiceId: Int,
    val totalAmount: Double,
    val amountPaid: Double,
    val balance: Double
)