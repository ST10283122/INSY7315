package com.example.insy_7315.models

import java.io.Serializable

data class Booking(
    val bookingId: Int,
    val bookingReference: String,
    val clientId: Int,
    val employeeId: Int?,
    val testTypeId: Int,
    val testName: String?,
    val preferredDate: String,
    val preferredTime: String,
    val actualDate: String?,
    val actualTime: String?,
    val location: String,
    val additionalNotes: String?,
    val bookingStatus: String,
    val sessionFee: Double,
    val depositRequired: Double,
    val employeeName: String?
) : Serializable