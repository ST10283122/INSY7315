package com.example.insy_7315.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface HttpClient {
    suspend fun request(endpoint: String, method: String, body: JSONObject? = null): Result<JSONObject>
}

class RealHttpClient(private val baseUrl: String) : HttpClient {
    override suspend fun request(endpoint: String, method: String, body: JSONObject?): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/$endpoint")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = method
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
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
                Result.failure(e)
            }
        }
    }
}