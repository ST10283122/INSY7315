package com.example.insy_7315.models

import java.io.Serializable

data class User(
    val userId: Int,
    val fullName: String,
    val email: String,
    val phone: String?,
    val userRole: String, // "Client", "Employee", "Admin"
    val termsAccepted: Boolean?,
    val address: String?,
    val certification: String?,
    val licenseNumber: String?,
    val specialization: String?,
    val accountStatus: String
) : Serializable