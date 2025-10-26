package com.example.insy_7315.database

import android.util.Log
import com.example.insy_7315.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DatabaseHelper {
    private const val TAG = "DatabaseHelper"
    private const val BASE_URL = "http://192.168.1.35:3000/api"

    private suspend fun makeRequest(
        endpoint: String,
        method: String,
        body: JSONObject? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/$endpoint")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = method
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (body != null && (method == "POST" || method == "PUT")) {
                connection.doOutput = true
                connection.outputStream.write(body.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)

            if (responseCode in 200..299) {
                Result.success(jsonResponse)
            } else {
                Result.failure(Exception(jsonResponse.optString("error", "Request failed")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            Result.failure(e)
        }
    }

    suspend fun registerUser(
        fullName: String,
        email: String,
        phone: String?,
        passwordHash: String, // Plain password - backend will hash it
        userRole: String,
        termsAccepted: Boolean = false,
        address: String? = null,
        certification: String? = null,
        licenseNumber: String? = null,
        specialization: String? = null
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("fullName", fullName)
                put("email", email)
                put("phone", phone)
                put("password", passwordHash) // Send plain password
                put("userRole", userRole)
                put("termsAccepted", termsAccepted)
            }

            val result = makeRequest("register", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val userJson = json.getJSONObject("user")
                    Result.success(User(
                        userId = userJson.getInt("UserID"),
                        fullName = userJson.getString("FullName"),
                        email = userJson.getString("Email"),
                        phone = userJson.optString("Phone", null),
                        userRole = userJson.getString("UserRole"),
                        termsAccepted = null,
                        address = null,
                        certification = null,
                        licenseNumber = null,
                        specialization = null,
                        accountStatus = userJson.getString("AccountStatus")
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            val result = makeRequest("login", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val userJson = json.getJSONObject("user")
                    Result.success(User(
                        userId = userJson.getInt("UserID"),
                        fullName = userJson.getString("FullName"),
                        email = userJson.getString("Email"),
                        phone = userJson.optString("Phone", null),
                        userRole = userJson.getString("UserRole"),
                        termsAccepted = userJson.optBoolean("TermsAccepted"),
                        address = userJson.optString("Address", null),
                        certification = userJson.optString("Certification", null),
                        licenseNumber = userJson.optString("LicenseNumber", null),
                        specialization = userJson.optString("Specialization", null),
                        accountStatus = userJson.getString("AccountStatus")
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserById(userId: Int): Result<User> = withContext(Dispatchers.IO) {
        try {
            val result = makeRequest("users/$userId", "GET")

            result.fold(
                onSuccess = { json ->
                    val userJson = json.getJSONObject("user")
                    Result.success(User(
                        userId = userJson.getInt("UserID"),
                        fullName = userJson.getString("FullName"),
                        email = userJson.getString("Email"),
                        phone = userJson.optString("Phone", null),
                        userRole = userJson.getString("UserRole"),
                        termsAccepted = userJson.optBoolean("TermsAccepted"),
                        address = userJson.optString("Address", null),
                        certification = userJson.optString("Certification", null),
                        licenseNumber = userJson.optString("LicenseNumber", null),
                        specialization = userJson.optString("Specialization", null),
                        accountStatus = userJson.getString("AccountStatus")
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get user failed", e)
            Result.failure(e)
        }
    }

    suspend fun emailExists(email: String): Boolean = false // Backend handles this
}