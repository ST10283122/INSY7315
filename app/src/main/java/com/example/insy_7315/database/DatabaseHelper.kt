package com.example.insy_7315.database

import android.util.Log
import com.example.insy_7315.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
            Log.e(TAG, "Request failed", e)
            Result.failure(e)
        }
    }

    // ============= USER FUNCTIONS =============

    suspend fun registerUser(
        fullName: String,
        email: String,
        phone: String?,
        passwordHash: String,
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
                put("password", passwordHash)
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

    suspend fun emailExists(email: String): Boolean = false

    // ============= TEST TYPES FUNCTIONS =============

    suspend fun getTestTypes(): Result<List<TestType>> = withContext(Dispatchers.IO) {
        try {
            val result = makeRequest("test-types", "GET")

            result.fold(
                onSuccess = { json ->
                    val testTypesArray = json.getJSONArray("testTypes")
                    val testTypes = mutableListOf<TestType>()

                    for (i in 0 until testTypesArray.length()) {
                        val testTypeJson = testTypesArray.getJSONObject(i)
                        testTypes.add(TestType(
                            testTypeId = testTypeJson.getInt("TestTypeID"),
                            testName = testTypeJson.getString("TestName"),
                            description = testTypeJson.optString("Description", null),
                            baseFee = testTypeJson.optDouble("BaseFee").takeIf { !it.isNaN() },
                            unit = testTypeJson.optString("Unit", null),
                            isActive = testTypeJson.getBoolean("IsActive")
                        ))
                    }

                    Result.success(testTypes)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get test types failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTestTypeById(testTypeId: Int): Result<TestType> = withContext(Dispatchers.IO) {
        try {
            val result = makeRequest("test-types/$testTypeId", "GET")

            result.fold(
                onSuccess = { json ->
                    val testTypeJson = json.getJSONObject("testType")
                    Result.success(TestType(
                        testTypeId = testTypeJson.getInt("TestTypeID"),
                        testName = testTypeJson.getString("TestName"),
                        description = testTypeJson.optString("Description", null),
                        baseFee = testTypeJson.optDouble("BaseFee").takeIf { !it.isNaN() },
                        unit = testTypeJson.optString("Unit", null),
                        isActive = testTypeJson.getBoolean("IsActive")
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get test type failed", e)
            Result.failure(e)
        }
    }

    // ============= BOOKINGS FUNCTIONS =============

    suspend fun createBooking(
        clientId: Int,
        testTypeId: Int,
        preferredDate: String,
        preferredTime: String,
        location: String,
        additionalNotes: String?,
        sessionFee: Double,
        depositRequired: Double = 0.0
    ): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("clientId", clientId)
                put("testTypeId", testTypeId)
                put("preferredDate", preferredDate)
                put("preferredTime", preferredTime)
                put("location", location)
                put("additionalNotes", additionalNotes)
                put("sessionFee", sessionFee)
                put("depositRequired", depositRequired)
            }

            val result = makeRequest("bookings", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val bookingJson = json.getJSONObject("booking")
                    Result.success(parseBooking(bookingJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Create booking failed", e)
            Result.failure(e)
        }
    }

    suspend fun getBookingsByClient(clientId: Int): Result<List<Booking>> = withContext(Dispatchers.IO) {
        try {
            val result = makeRequest("bookings/client/$clientId", "GET")

            result.fold(
                onSuccess = { json ->
                    val bookingsArray = json.getJSONArray("bookings")
                    val bookings = mutableListOf<Booking>()

                    for (i in 0 until bookingsArray.length()) {
                        val bookingJson = bookingsArray.getJSONObject(i)
                        bookings.add(parseBooking(bookingJson))
                    }

                    Result.success(bookings)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get bookings failed", e)
            Result.failure(e)
        }
    }

    suspend fun getBookingById(bookingId: Int): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val result = makeRequest("bookings/$bookingId", "GET")

            result.fold(
                onSuccess = { json ->
                    val bookingJson = json.getJSONObject("booking")
                    Result.success(parseBooking(bookingJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get booking failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateBookingStatus(
        bookingId: Int,
        status: String,
        updatedBy: Int
    ): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("status", status)
                put("updatedBy", updatedBy)
            }

            val result = makeRequest("bookings/$bookingId/status", "PATCH", body)

            result.fold(
                onSuccess = { json ->
                    val bookingJson = json.getJSONObject("booking")
                    Result.success(parseBooking(bookingJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update booking status failed", e)
            Result.failure(e)
        }
    }

    suspend fun cancelBooking(
        bookingId: Int,
        cancellationReason: String,
        cancelledBy: Int
    ): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("cancellationReason", cancellationReason)
                put("cancelledBy", cancelledBy)
            }

            val result = makeRequest("bookings/$bookingId/cancel", "PATCH", body)

            result.fold(
                onSuccess = { json ->
                    val bookingJson = json.getJSONObject("booking")
                    Result.success(parseBooking(bookingJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cancel booking failed", e)
            Result.failure(e)
        }
    }

    // ============= INVOICES FUNCTIONS =============

    suspend fun createInvoice(
        bookingId: Int,
        dueDate: String?,
        totalAmount: Double,
        taxAmount: Double = 0.0,
        discountAmount: Double = 0.0,
        notes: String?,
        lineItems: List<InvoiceLineItem>?
    ): Result<Invoice> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("bookingId", bookingId)
                put("dueDate", dueDate)
                put("totalAmount", totalAmount)
                put("taxAmount", taxAmount)
                put("discountAmount", discountAmount)
                put("notes", notes)

                if (lineItems != null) {
                    val lineItemsArray = JSONArray()
                    lineItems.forEach { item ->
                        lineItemsArray.put(JSONObject().apply {
                            put("description", item.description)
                            put("quantity", item.quantity)
                            put("unitPrice", item.unitPrice)
                        })
                    }
                    put("lineItems", lineItemsArray)
                }
            }

            val result = makeRequest("invoices", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val invoiceJson = json.getJSONObject("invoice")
                    Result.success(parseInvoice(invoiceJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Create invoice failed", e)
            Result.failure(e)
        }
    }

    suspend fun getInvoiceById(invoiceId: Int): Result<Invoice> = withContext(Dispatchers.IO) {
        try {
            val result = makeRequest("invoices/$invoiceId", "GET")

            result.fold(
                onSuccess = { json ->
                    val invoiceJson = json.getJSONObject("invoice")
                    Result.success(parseInvoice(invoiceJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get invoice failed", e)
            Result.failure(e)
        }
    }

    suspend fun getInvoicesByBooking(bookingId: Int): Result<List<Invoice>> = withContext(Dispatchers.IO) {
        try {
            val result = makeRequest("invoices/booking/$bookingId", "GET")

            result.fold(
                onSuccess = { json ->
                    val invoicesArray = json.getJSONArray("invoices")
                    val invoices = mutableListOf<Invoice>()

                    for (i in 0 until invoicesArray.length()) {
                        val invoiceJson = invoicesArray.getJSONObject(i)
                        invoices.add(parseInvoice(invoiceJson))
                    }

                    Result.success(invoices)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get invoices failed", e)
            Result.failure(e)
        }
    }

    // ============= PAYMENTS FUNCTIONS =============

    suspend fun createPayment(
        invoiceId: Int,
        bookingId: Int,
        paymentAmount: Double,
        paymentMethod: String,
        paymentType: String,
        transactionId: String?,
        paymentGateway: String?,
        notes: String?,
        createdBy: Int?
    ): Result<Payment> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("invoiceId", invoiceId)
                put("bookingId", bookingId)
                put("paymentAmount", paymentAmount)
                put("paymentMethod", paymentMethod)
                put("paymentType", paymentType)
                put("transactionId", transactionId)
                put("paymentGateway", paymentGateway)
                put("notes", notes)
                put("createdBy", createdBy)
            }

            val result = makeRequest("payments", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val paymentJson = json.getJSONObject("payment")
                    Result.success(parsePayment(paymentJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Create payment failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPaymentsByInvoice(invoiceId: Int): Result<List<Payment>> = withContext(Dispatchers.IO) {
        try {
            val result = makeRequest("payments/invoice/$invoiceId", "GET")

            result.fold(
                onSuccess = { json ->
                    val paymentsArray = json.getJSONArray("payments")
                    val payments = mutableListOf<Payment>()

                    for (i in 0 until paymentsArray.length()) {
                        val paymentJson = paymentsArray.getJSONObject(i)
                        payments.add(parsePayment(paymentJson))
                    }

                    Result.success(payments)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get payments failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPaymentsByBooking(bookingId: Int): Result<List<Payment>> = withContext(Dispatchers.IO) {
        try {
            val result = makeRequest("payments/booking/$bookingId", "GET")

            result.fold(
                onSuccess = { json ->
                    val paymentsArray = json.getJSONArray("payments")
                    val payments = mutableListOf<Payment>()

                    for (i in 0 until paymentsArray.length()) {
                        val paymentJson = paymentsArray.getJSONObject(i)
                        payments.add(parsePayment(paymentJson))
                    }

                    Result.success(payments)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get payments failed", e)
            Result.failure(e)
        }
    }

    // ============= HELPER FUNCTIONS =============

    private fun parseBooking(json: JSONObject): Booking {
        return Booking(
            bookingId = json.getInt("BookingID"),
            bookingReference = json.getString("BookingReference"),
            clientId = json.getInt("ClientID"),
            employeeId = json.optInt("EmployeeID"),
            testTypeId = json.getInt("TestTypeID"),
            testName = json.optString("TestName", null),
            preferredDate = json.getString("PreferredDate"),
            preferredTime = json.getString("PreferredTime"),
            actualDate = json.optString("ActualDate", null),
            actualTime = json.optString("ActualTime", null),
            location = json.getString("Location"),
            additionalNotes = json.optString("AdditionalNotes", null),
            bookingStatus = json.getString("BookingStatus"),
            sessionFee = json.getDouble("SessionFee"),
            depositRequired = json.getDouble("DepositRequired"),
            employeeName = json.optString("EmployeeName", null)
        )
    }

    private fun parseInvoice(json: JSONObject): Invoice {
        return Invoice(
            invoiceId = json.getInt("InvoiceID"),
            bookingId = json.getInt("BookingID"),
            invoiceNumber = json.getString("InvoiceNumber"),
            invoiceDate = json.getString("InvoiceDate"),
            dueDate = json.optString("DueDate", null),
            totalAmount = json.getDouble("TotalAmount"),
            taxAmount = json.getDouble("TaxAmount"),
            discountAmount = json.getDouble("DiscountAmount"),
            invoiceStatus = json.getString("InvoiceStatus"),
            notes = json.optString("Notes", null)
        )
    }

    private fun parsePayment(json: JSONObject): Payment {
        return Payment(
            paymentId = json.getInt("PaymentID"),
            invoiceId = json.getInt("InvoiceID"),
            bookingId = json.getInt("BookingID"),
            paymentReference = json.getString("PaymentReference"),
            paymentAmount = json.getDouble("PaymentAmount"),
            paymentMethod = json.getString("PaymentMethod"),
            paymentType = json.getString("PaymentType"),
            paymentStatus = json.getString("PaymentStatus"),
            paymentDate = json.getString("PaymentDate"),
            transactionId = json.optString("TransactionID", null)
        )
    }
}