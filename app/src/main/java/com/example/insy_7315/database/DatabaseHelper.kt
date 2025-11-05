package com.example.insy_7315.database

import android.util.Log
import com.example.insy_7315.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.annotation.VisibleForTesting

object DatabaseHelper {
    private const val TAG = "DatabaseHelper"
    const val BASE_URL = "https://polygraph-backend.azurewebsites.net/api"

    // Injectible HTTP client
    var httpClient: HttpClient = RealHttpClient(BASE_URL)

    data class StripeConfig(
        val publishableKey: String,
        val currency: String,
        val country: String
    )

    suspend fun getStripeConfig(): Result<StripeConfig> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("config/stripe", "GET")

            result.fold(
                onSuccess = { json ->
                    Result.success(StripeConfig(
                        publishableKey = json.getString("publishableKey"),
                        currency = json.optString("currency", "ZAR"),
                        country = json.optString("country", "ZA")
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get Stripe config failed", e)
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
                address?.let { put("address", it) }
                certification?.let { put("certification", it) }
                licenseNumber?.let { put("licenseNumber", it) }
                specialization?.let { put("specialization", it) }
            }

            val result = httpClient.request("register", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val userJson = json.getJSONObject("user")
                    Result.success(parseUser(userJson))
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

            val result = httpClient.request("login", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val userJson = json.getJSONObject("user")
                    Result.success(parseUser(userJson))
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
            val result = httpClient.request("users/$userId", "GET")

            result.fold(
                onSuccess = { json ->
                    val userJson = json.getJSONObject("user")
                    Result.success(parseUser(userJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get user failed", e)
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("users", "GET")

            result.fold(
                onSuccess = { json ->
                    val usersArray = json.getJSONArray("users")
                    val users = mutableListOf<User>()

                    for (i in 0 until usersArray.length()) {
                        val userJson = usersArray.getJSONObject(i)
                        users.add(parseUser(userJson))
                    }

                    Result.success(users)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get all users failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateUser(
        userId: Int,
        fullName: String? = null,
        email: String? = null,
        phone: String? = null,
        address: String? = null,
        certification: String? = null,
        licenseNumber: String? = null,
        specialization: String? = null,
        userRole: String? = null,
        accountStatus: String? = null
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                fullName?.let { put("fullName", it) }
                email?.let { put("email", it) }
                phone?.let { put("phone", it) }
                address?.let { put("address", it) }
                certification?.let { put("certification", it) }
                licenseNumber?.let { put("licenseNumber", it) }
                specialization?.let { put("specialization", it) }
                userRole?.let { put("userRole", it) }
                accountStatus?.let { put("accountStatus", it) }
            }

            val result = httpClient.request("users/$userId", "PATCH", body)

            result.fold(
                onSuccess = { json ->
                    val userJson = json.getJSONObject("user")
                    Result.success(parseUser(userJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update user failed", e)
            Result.failure(e)
        }
    }

    suspend fun resetUserPassword(
        userId: Int,
        newPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("newPassword", newPassword)
            }

            val result = httpClient.request("users/$userId/reset-password", "PATCH", body)

            result.fold(
                onSuccess = { json ->
                    Result.success(json.getString("message"))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Reset password failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteUser(userId: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("users/$userId", "DELETE")

            result.fold(
                onSuccess = { json ->
                    Result.success(json.getString("message"))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Delete user failed", e)
            Result.failure(e)
        }
    }

    suspend fun emailExists(email: String): Boolean = false

    // ============= TEST TYPES FUNCTIONS =============

    suspend fun getTestTypes(): Result<List<TestType>> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("test-types", "GET")

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
            val result = httpClient.request("test-types/$testTypeId", "GET")

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

            val result = httpClient.request("bookings", "POST", body)

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
            val result = httpClient.request("bookings/client/$clientId", "GET")

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
            val result = httpClient.request("bookings/$bookingId", "GET")

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

            val result = httpClient.request("bookings/$bookingId/status", "PATCH", body)

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

            val result = httpClient.request("bookings/$bookingId/cancel", "PATCH", body)

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

    suspend fun getAllBookings(): Result<List<Booking>> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("bookings/all", "GET")

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
            Log.e(TAG, "Get all bookings failed", e)
            Result.failure(e)
        }
    }

    suspend fun assignEmployeeToBooking(
        bookingId: Int,
        employeeId: Int,
        actualDate: String,
        actualTime: String,
        updatedBy: Int
    ): Result<Booking> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("employeeId", employeeId)
                put("actualDate", actualDate)
                put("actualTime", actualTime)
                put("updatedBy", updatedBy)
            }

            val result = httpClient.request("bookings/$bookingId/assign", "PATCH", body)

            result.fold(
                onSuccess = { json ->
                    val bookingJson = json.getJSONObject("booking")
                    Result.success(parseBooking(bookingJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Assign employee failed", e)
            Result.failure(e)
        }
    }

    suspend fun getBookingsByEmployee(employeeId: Int): Result<List<Booking>> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("bookings/employee/$employeeId", "GET")

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
            Log.e(TAG, "Get employee bookings failed", e)
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

            val result = httpClient.request("invoices", "POST", body)

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
            val result = httpClient.request("invoices/$invoiceId", "GET")

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
            val result = httpClient.request("invoices/booking/$bookingId", "GET")

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

    suspend fun getInvoiceBalance(invoiceId: Int): Result<InvoiceBalance> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("invoices/$invoiceId/balance", "GET")

            result.fold(
                onSuccess = { json ->
                    val balanceJson = json.getJSONObject("balance")
                    Result.success(InvoiceBalance(
                        invoiceId = balanceJson.getInt("InvoiceID"),
                        totalAmount = balanceJson.getDouble("TotalAmount"),
                        amountPaid = balanceJson.getDouble("AmountPaid"),
                        balance = balanceJson.getDouble("Balance")
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get invoice balance failed", e)
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

            val result = httpClient.request("payments", "POST", body)

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
            val result = httpClient.request("payments/invoice/$invoiceId", "GET")

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
            val result = httpClient.request("payments/booking/$bookingId", "GET")

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

    // ============= TESTS FUNCTIONS =============

    suspend fun createTest(
        bookingId: Int,
        clientId: Int,
        employeeId: Int,
        testTypeId: Int,
        testDate: String,
        testTime: String,
        testLocation: String?,
        examineeName: String,
        examineeDetails: String?,
        examinerName: String,
        examinerEmail: String,
        examinerPhone: String?,
        resultSummary: String,
        testOutcome: String,
        internalNotes: String?,
        createdBy: Int
    ): Result<Test> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("bookingId", bookingId)
                put("clientId", clientId)
                put("employeeId", employeeId)
                put("testTypeId", testTypeId)
                put("testDate", testDate)
                put("testTime", testTime)
                put("testLocation", testLocation)
                put("examineeName", examineeName)
                put("examineeDetails", examineeDetails)
                put("examinerName", examinerName)
                put("examinerEmail", examinerEmail)
                put("examinerPhone", examinerPhone)
                put("resultSummary", resultSummary)
                put("testOutcome", testOutcome)
                put("internalNotes", internalNotes)
                put("createdBy", createdBy)
            }

            val result = httpClient.request("tests", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val testJson = json.getJSONObject("test")
                    Result.success(parseTest(testJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Create test failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTestById(testId: Int): Result<Test> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("tests/$testId", "GET")

            result.fold(
                onSuccess = { json ->
                    val testJson = json.getJSONObject("test")
                    Result.success(parseTest(testJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get test failed", e)
            Result.failure(e)
        }
    }

    suspend fun getAllTests(): Result<List<Test>> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("tests", "GET")

            result.fold(
                onSuccess = { json ->
                    val testsArray = json.getJSONArray("tests")
                    val tests = mutableListOf<Test>()

                    for (i in 0 until testsArray.length()) {
                        val testJson = testsArray.getJSONObject(i)
                        tests.add(parseTest(testJson))
                    }

                    Result.success(tests)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get all tests failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTestByBooking(bookingId: Int): Result<Test> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("tests/booking/$bookingId", "GET")

            result.fold(
                onSuccess = { json ->
                    val testJson = json.getJSONObject("test")
                    Result.success(parseTest(testJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get test by booking failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTestsByClient(clientId: Int): Result<List<Test>> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("tests/client/$clientId", "GET")

            result.fold(
                onSuccess = { json ->
                    val testsArray = json.getJSONArray("tests")
                    val tests = mutableListOf<Test>()

                    for (i in 0 until testsArray.length()) {
                        val testJson = testsArray.getJSONObject(i)
                        tests.add(parseTest(testJson))
                    }

                    Result.success(tests)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get client tests failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTestsByEmployee(employeeId: Int): Result<List<Test>> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("tests/employee/$employeeId", "GET")

            result.fold(
                onSuccess = { json ->
                    val testsArray = json.getJSONArray("tests")
                    val tests = mutableListOf<Test>()

                    for (i in 0 until testsArray.length()) {
                        val testJson = testsArray.getJSONObject(i)
                        tests.add(parseTest(testJson))
                    }

                    Result.success(tests)
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get employee tests failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateTest(
        testId: Int,
        testDate: String? = null,
        testTime: String? = null,
        testLocation: String? = null,
        examineeName: String? = null,
        examineeDetails: String? = null,
        examinerName: String? = null,
        examinerEmail: String? = null,
        examinerPhone: String? = null,
        resultSummary: String? = null,
        testOutcome: String? = null,
        testStatus: String? = null,
        internalNotes: String? = null,
        updatedBy: Int? = null
    ): Result<Test> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                testDate?.let { put("testDate", it) }
                testTime?.let { put("testTime", it) }
                testLocation?.let { put("testLocation", it) }
                examineeName?.let { put("examineeName", it) }
                examineeDetails?.let { put("examineeDetails", it) }
                examinerName?.let { put("examinerName", it) }
                examinerEmail?.let { put("examinerEmail", it) }
                examinerPhone?.let { put("examinerPhone", it) }
                resultSummary?.let { put("resultSummary", it) }
                testOutcome?.let { put("testOutcome", it) }
                testStatus?.let { put("testStatus", it) }
                internalNotes?.let { put("internalNotes", it) }
                updatedBy?.let { put("updatedBy", it) }
            }

            val result = httpClient.request("tests/$testId", "PATCH", body)

            result.fold(
                onSuccess = { json ->
                    val testJson = json.getJSONObject("test")
                    Result.success(parseTest(testJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update test failed", e)
            Result.failure(e)
        }
    }

    suspend fun completeTest(
        testId: Int,
        updatedBy: Int
    ): Result<Test> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("updatedBy", updatedBy)
            }

            val result = httpClient.request("tests/$testId/complete", "PATCH", body)

            result.fold(
                onSuccess = { json ->
                    val testJson = json.getJSONObject("test")
                    Result.success(parseTest(testJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Complete test failed", e)
            Result.failure(e)
        }
    }

    suspend fun releaseTest(
        testId: Int,
        releasedBy: Int
    ): Result<Test> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("releasedBy", releasedBy)
            }

            val result = httpClient.request("tests/$testId/release", "PATCH", body)

            result.fold(
                onSuccess = { json ->
                    val testJson = json.getJSONObject("test")
                    Result.success(parseTest(testJson))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Release test failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTestReport(testId: Int): Result<TestReport> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("tests/$testId/report", "GET")

            result.fold(
                onSuccess = { json ->
                    val reportJson = json.getJSONObject("report")
                    Result.success(TestReport(
                        testId = reportJson.getInt("TestID"),
                        testDate = reportJson.getString("TestDate"),
                        testTime = reportJson.getString("TestTime"),
                        testLocation = reportJson.optString("TestLocation", null),
                        examineeName = reportJson.getString("ExamineeName"),
                        examineeDetails = reportJson.optString("ExamineeDetails", null),
                        examinerName = reportJson.getString("ExaminerName"),
                        examinerEmail = reportJson.getString("ExaminerEmail"),
                        examinerPhone = reportJson.optString("ExaminerPhone", null),
                        resultSummary = reportJson.getString("ResultSummary"),
                        testOutcome = reportJson.getString("TestOutcome"),
                        testStatus = reportJson.getString("TestStatus"),
                        completedAt = reportJson.optString("CompletedAt", null),
                        releasedAt = reportJson.optString("ReleasedAt", null),
                        testName = reportJson.getString("TestName"),
                        testDescription = reportJson.optString("TestDescription", null),
                        bookingReference = reportJson.getString("BookingReference")
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Get test report failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteTest(testId: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = httpClient.request("tests/$testId", "DELETE")

            result.fold(
                onSuccess = { json ->
                    Result.success(json.getString("message"))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Delete test failed", e)
            Result.failure(e)
        }
    }

    // ============= PAYMENT INITIATION FUNCTIONS =============

    suspend fun initiatePayFastPayment(
        bookingId: Int,
        invoiceId: Int,
        amount: Double,
        description: String,
        clientName: String,
        clientEmail: String
    ): Result<PaymentInitiation> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("bookingId", bookingId)
                put("invoiceId", invoiceId)
                put("amount", amount)
                put("description", description)
                put("clientName", clientName)
                put("clientEmail", clientEmail)
            }

            val result = httpClient.request("payments/payfast/initiate", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val paymentUrl = json.getString("paymentUrl")
                    val paymentData = json.getJSONObject("paymentData")
                    val paymentReference = json.getString("paymentReference")

                    val dataMap = mutableMapOf<String, String>()
                    paymentData.keys().forEach { key ->
                        dataMap[key] = paymentData.getString(key)
                    }

                    Result.success(PaymentInitiation(
                        paymentUrl = paymentUrl,
                        paymentData = dataMap,
                        paymentReference = paymentReference,
                        sessionId = null
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "PayFast initiate failed", e)
            Result.failure(e)
        }
    }

    suspend fun initiateOzowPayment(
        bookingId: Int,
        invoiceId: Int,
        amount: Double,
        description: String,
        clientName: String,
        clientEmail: String
    ): Result<PaymentInitiation> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("bookingId", bookingId)
                put("invoiceId", invoiceId)
                put("amount", amount)
                put("description", description)
                put("clientName", clientName)
                put("clientEmail", clientEmail)
            }

            val result = httpClient.request("payments/ozow/initiate", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val paymentUrl = json.getString("paymentUrl")
                    val paymentData = json.getJSONObject("paymentData")
                    val paymentReference = json.getString("paymentReference")

                    val dataMap = mutableMapOf<String, String>()
                    paymentData.keys().forEach { key ->
                        dataMap[key] = paymentData.get(key).toString()
                    }

                    Result.success(PaymentInitiation(
                        paymentUrl = paymentUrl,
                        paymentData = dataMap,
                        paymentReference = paymentReference,
                        sessionId = null
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ozow initiate failed", e)
            Result.failure(e)
        }
    }

    suspend fun initiateStripePayment(
        bookingId: Int,
        invoiceId: Int,
        amount: Double,
        description: String,
        clientName: String,
        clientEmail: String
    ): Result<PaymentInitiation> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("bookingId", bookingId)
                put("invoiceId", invoiceId)
                put("amount", amount)
                put("description", description)
                put("clientName", clientName)
                put("clientEmail", clientEmail)
            }

            val result = httpClient.request("payments/stripe/initiate", "POST", body)

            result.fold(
                onSuccess = { json ->
                    val sessionId = json.getString("sessionId")
                    val paymentUrl = json.getString("paymentUrl")
                    val paymentReference = json.getString("paymentReference")

                    Result.success(PaymentInitiation(
                        paymentUrl = paymentUrl,
                        paymentData = emptyMap(),
                        paymentReference = paymentReference,
                        sessionId = sessionId
                    ))
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Stripe initiate failed", e)
            Result.failure(e)
        }
    }

    @VisibleForTesting
    internal fun parseUser(json: JSONObject): User {
        return User(
            userId = json.getInt("UserID"),
            fullName = json.getString("FullName"),
            email = json.getString("Email"),
            phone = json.optString("Phone", null),
            userRole = json.getString("UserRole"),
            termsAccepted = json.optBoolean("TermsAccepted"),
            address = json.optString("Address", null),
            certification = json.optString("Certification", null),
            licenseNumber = json.optString("LicenseNumber", null),
            specialization = json.optString("Specialization", null),
            accountStatus = json.getString("AccountStatus")
        )
    }

    @VisibleForTesting
    internal fun parseBooking(json: JSONObject): Booking {
        return Booking(
            bookingId = json.getInt("BookingID"),
            bookingReference = json.getString("BookingReference"),
            clientId = json.getInt("ClientID"),
            employeeId = json.optInt("EmployeeID").takeIf { it != 0 },
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
            employeeName = json.optString("EmployeeName", null),
            clientName = json.optString("ClientName", null)
        )
    }

    @VisibleForTesting
    internal fun parseInvoice(json: JSONObject): Invoice {
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

    @VisibleForTesting
    internal fun parsePayment(json: JSONObject): Payment {
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

    @VisibleForTesting
    internal fun parseTest(json: JSONObject): Test {
        return Test(
            testId = json.getInt("TestID"),
            bookingId = json.getInt("BookingID"),
            clientId = json.getInt("ClientID"),
            employeeId = json.getInt("EmployeeID"),
            testTypeId = json.getInt("TestTypeID"),
            testDate = json.getString("TestDate"),
            testTime = json.getString("TestTime"),
            testLocation = json.optString("TestLocation", null),
            examineeName = json.getString("ExamineeName"),
            examineeDetails = json.optString("ExamineeDetails", null),
            examinerName = json.getString("ExaminerName"),
            examinerEmail = json.getString("ExaminerEmail"),
            examinerPhone = json.optString("ExaminerPhone", null),
            resultSummary = json.getString("ResultSummary"),
            testOutcome = json.getString("TestOutcome"),
            testStatus = json.getString("TestStatus"),
            completedAt = json.optString("CompletedAt", null),
            releasedAt = json.optString("ReleasedAt", null),
            internalNotes = json.optString("InternalNotes", null),
            createdAt = json.getString("CreatedAt"),
            updatedAt = json.getString("UpdatedAt"),
            bookingReference = json.optString("BookingReference", null),
            clientName = json.optString("ClientName", null),
            employeeName = json.optString("EmployeeName", null),
            testName = json.optString("TestName", null)
        )
    }}