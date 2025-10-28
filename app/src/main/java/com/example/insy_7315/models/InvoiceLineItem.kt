package com.example.insy_7315.models

import java.io.Serializable

data class InvoiceLineItem(
    val description: String,
    val quantity: Double,
    val unitPrice: Double
) : Serializable {
    val lineTotal: Double
        get() = quantity * unitPrice
}