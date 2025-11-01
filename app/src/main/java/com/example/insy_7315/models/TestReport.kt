package com.example.insy_7315.models

data class TestReport(
    val testId: Int,
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
    val testName: String,
    val testDescription: String?,
    val bookingReference: String
)