package com.example.insy_7315.models

data class Test(
    val testId: Int,
    val bookingId: Int,
    val clientId: Int,
    val employeeId: Int,
    val testTypeId: Int,
    val testDate: String,
    val testTime: String,
    val testLocation: String?,
    val examineeName: String,
    val examineeDetails: String?,
    val examinerName: String,
    val examinerEmail: String,
    val examinerPhone: String?,
    val resultSummary: String,
    val testOutcome: String,
    val testStatus: String,
    val completedAt: String?,
    val releasedAt: String?,
    val internalNotes: String?,
    val createdAt: String,
    val updatedAt: String,
    val bookingReference: String?,
    val clientName: String?,
    val employeeName: String?,
    val testName: String?
)