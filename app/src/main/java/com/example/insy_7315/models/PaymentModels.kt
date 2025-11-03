package com.example.insy_7315.models

data class PaymentInitiation(
    val paymentUrl: String,
    val paymentData: Map<String, String>,
    val paymentReference: String,
    val sessionId: String?
)

enum class PaymentGateway {
    PAYFAST,
    OZOW,
    STRIPE
}

enum class PaymentOption {
    DEPOSIT,
    FULL
}