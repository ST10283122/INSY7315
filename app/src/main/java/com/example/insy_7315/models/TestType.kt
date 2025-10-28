package com.example.insy_7315.models

import java.io.Serializable

data class TestType(
    val testTypeId: Int,
    val testName: String,
    val description: String?,
    val baseFee: Double?,
    val unit: String?,
    val isActive: Boolean
) : Serializable