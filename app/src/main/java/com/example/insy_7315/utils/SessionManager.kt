package com.example.insy_7315.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.insy_7315.models.User

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)

    fun saveUser(user: User) {
        prefs.edit().apply {
            putInt("userId", user.userId)
            putString("fullName", user.fullName)
            putString("email", user.email)
            putString("userRole", user.userRole)
            apply()
        }
    }

    fun getUser(): User? {
        val userId = prefs.getInt("userId", -1)
        if (userId == -1) return null

        return User(
            userId = userId,
            fullName = prefs.getString("fullName", "") ?: "",
            email = prefs.getString("email", "") ?: "",
            phone = null,
            userRole = prefs.getString("userRole", "") ?: "",
            termsAccepted = null,
            address = null,
            certification = null,
            licenseNumber = null,
            specialization = null,
            accountStatus = "Active"
        )
    }

    fun isLoggedIn(): Boolean = prefs.getInt("userId", -1) != -1

    fun getUserRole(): String? = prefs.getString("userRole", null)

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun getAuthHeaders(): Map<String, String> {
        val user = getUser() ?: return emptyMap()
        return mapOf(
            "x-user-id" to user.userId.toString(),
            "x-user-email" to user.email
        )
    }
}