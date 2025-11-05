package com.example.insy_7315

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.EmployeeTestDetailsBinding
import com.example.insy_7315.models.Booking
import com.example.insy_7315.models.Test
import com.example.insy_7315.models.User
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EmployeeTestDetailsActivity : AppCompatActivity() {
    private lateinit var binding: EmployeeTestDetailsBinding
    private var bookingId: Int = 0
    private var employeeId: Int = 0
    private var employeeName: String = ""
    private var booking: Booking? = null
    private var existingTest: Test? = null
    private var client: User? = null

    companion object {
        private const val TAG = "EmployeeTestDetails"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EmployeeTestDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookingId = intent.getIntExtra("BOOKING_ID", 0)
        employeeId = intent.getIntExtra("EMPLOYEE_ID", 0)
        employeeName = intent.getStringExtra("EMPLOYEE_NAME") ?: "Employee"

        Log.d(TAG, "onCreate - Booking ID: $bookingId, Employee ID: $employeeId")

        if (bookingId == 0 || employeeId == 0) {
            Toast.makeText(this, "Invalid booking information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        loadBookingDetails()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.saveTestBtn.setOnClickListener {
            saveTest()
        }
    }

    private fun loadBookingDetails() {
        lifecycleScope.launch {
            try {
                val bookingResult = DatabaseHelper.getBookingById(bookingId)
                bookingResult.onSuccess { bookingData ->
                    booking = bookingData
                    populateBookingInfo(bookingData)
                    val clientResult = DatabaseHelper.getUserById(bookingData.clientId)
                    clientResult.onSuccess { clientData ->
                        client = clientData
                    }
                    val testResult = DatabaseHelper.getTestByBooking(bookingId)
                    testResult.onSuccess { testData ->
                        existingTest = testData
                        populateTestData(testData)
                    }.onFailure {
                        prepopulateFromBooking(bookingData)
                    }

                    loadPaymentStatus(bookingData.bookingId)

                }.onFailure { error ->
                    Log.e(TAG, "Failed to load booking", error)
                    Toast.makeText(
                        this@EmployeeTestDetailsActivity,
                        "Failed to load booking: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading booking", e)
                Toast.makeText(
                    this@EmployeeTestDetailsActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun populateBookingInfo(booking: Booking) {
        binding.bookingReference.text = "Booking ID: ${booking.bookingReference}"
        binding.clientInfo.text = "Client: ${booking.clientName ?: "Unknown"}"
        binding.testTypeInfo.text = "Test Type: ${booking.testName ?: "Unknown"}"
        binding.locationInfo.text = "Location: ${booking.location}"
        binding.examinerInfo.text = "Examiner: $employeeName"

        val dateTime = "${booking.actualDate ?: booking.preferredDate} • ${booking.actualTime ?: booking.preferredTime}"
        binding.scheduledDateTime.text = "Scheduled: $dateTime"
    }

    private fun prepopulateFromBooking(booking: Booking) {
        // Prepopulate test fields with booking data
        val date = booking.actualDate ?: booking.preferredDate
        val time = booking.actualTime ?: booking.preferredTime

        binding.testDate.setText(formatDateForInput(date))
        binding.testTime.setText(formatTimeForInput(time))
        binding.testLocation.setText(booking.location)
        binding.examineeName.setText(booking.clientName ?: "")
    }

    private fun populateTestData(test: Test) {
        // Populate all fields with existing test data
        binding.testDate.setText(test.testDate)
        binding.testTime.setText(test.testTime)
        binding.testLocation.setText(test.testLocation ?: "")
        binding.examineeName.setText(test.examineeName)
        binding.examineeDetails.setText(test.examineeDetails ?: "")
        binding.resultSummary.setText(test.resultSummary)
        binding.internalNotes.setText(test.internalNotes ?: "")

        when (test.testOutcome) {
            "Pass" -> binding.outcomePass.isChecked = true
            "Fail" -> binding.outcomeFail.isChecked = true
            "Inconclusive" -> binding.outcomeInconclusive.isChecked = true
            "No Deception Indicated" -> binding.outcomeNDI.isChecked = true
            "Deception Indicated" -> binding.outcomeDI.isChecked = true
        }

        when (test.testStatus) {
            "Draft" -> binding.statusDraft.isChecked = true
            "Completed" -> binding.statusCompleted.isChecked = true
            "Reviewed" -> binding.statusReviewed.isChecked = true
            "Released" -> binding.statusReleased.isChecked = true
        }
    }

    private fun loadPaymentStatus(bookingId: Int) {
        lifecycleScope.launch {
            try {
                val invoicesResult = DatabaseHelper.getInvoicesByBooking(bookingId)
                invoicesResult.onSuccess { invoices ->
                    if (invoices.isNotEmpty()) {
                        val invoice = invoices.first()
                        val balanceResult = DatabaseHelper.getInvoiceBalance(invoice.invoiceId)
                        balanceResult.onSuccess { balance ->
                            val status = when {
                                balance.balance <= 0.0 -> "FULLY PAID"
                                balance.amountPaid > 0.0 -> "DEPOSIT PAID"
                                else -> "PENDING PAYMENT"
                            }
                            binding.paymentStatusInfo.text = "Payment Status: $status"
                            binding.paymentStatusInfo.setTextColor(
                                when (status) {
                                    "FULLY PAID" -> 0xFF4CAF50.toInt()
                                    "DEPOSIT PAID" -> 0xFFFFA726.toInt()
                                    else -> 0xFFF44336.toInt()
                                }
                            )
                        }
                    } else {
                        binding.paymentStatusInfo.text = "Payment Status: NO INVOICE"
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load payment status", e)
            }
        }
    }

    private fun saveTest() {
        // Validate required fields
        val testDate = binding.testDate.text.toString().trim()
        val testTime = binding.testTime.text.toString().trim()
        val examineeName = binding.examineeName.text.toString().trim()
        val resultSummary = binding.resultSummary.text.toString().trim()

        if (testDate.isEmpty() || testTime.isEmpty() || examineeName.isEmpty() || resultSummary.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Get selected outcome
        val testOutcome = when (binding.outcomeRadioGroup.checkedRadioButtonId) {
            R.id.outcomePass -> "Pass"
            R.id.outcomeFail -> "Fail"
            R.id.outcomeInconclusive -> "Inconclusive"
            R.id.outcomeNDI -> "No Deception Indicated"
            R.id.outcomeDI -> "Deception Indicated"
            else -> {
                Toast.makeText(this, "Please select a test outcome", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Get selected status
        val testStatus = when (binding.statusRadioGroup.checkedRadioButtonId) {
            R.id.statusDraft -> "Draft"
            R.id.statusCompleted -> "Completed"
            R.id.statusReviewed -> "Reviewed"
            R.id.statusReleased -> "Released"
            else -> "Draft"
        }

        val testLocation = binding.testLocation.text.toString().trim().ifEmpty { null }
        val examineeDetails = binding.examineeDetails.text.toString().trim().ifEmpty { null }
        val internalNotes = binding.internalNotes.text.toString().trim().ifEmpty { null }

        lifecycleScope.launch {
            try {
                val employeeResult = DatabaseHelper.getUserById(employeeId)
                employeeResult.onSuccess { employee ->
                    val booking = this@EmployeeTestDetailsActivity.booking
                    if (booking == null) {
                        Toast.makeText(
                            this@EmployeeTestDetailsActivity,
                            "Booking data not loaded",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@onSuccess
                    }

                    // Disable save button while processing
                    binding.saveTestBtn.isEnabled = false
                    binding.saveTestBtn.text = "Saving..."

                    if (existingTest == null) {
                        // Create new test
                        val createResult = DatabaseHelper.createTest(
                            bookingId = bookingId,
                            clientId = booking.clientId,
                            employeeId = employeeId,
                            testTypeId = booking.testTypeId,
                            testDate = testDate,
                            testTime = testTime,
                            testLocation = testLocation,
                            examineeName = examineeName,
                            examineeDetails = examineeDetails,
                            examinerName = employee.fullName,
                            examinerEmail = employee.email,
                            examinerPhone = employee.phone,
                            resultSummary = resultSummary,
                            testOutcome = testOutcome,
                            internalNotes = internalNotes,
                            createdBy = employeeId
                        )

                        createResult.onSuccess { test ->
                            // If status is not Draft, update the status
                            if (testStatus != "Draft") {
                                updateTestStatus(test.testId, testStatus)
                            } else {
                                Toast.makeText(
                                    this@EmployeeTestDetailsActivity,
                                    "Test created successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        }.onFailure { error ->
                            Log.e(TAG, "Failed to create test", error)
                            Toast.makeText(
                                this@EmployeeTestDetailsActivity,
                                "Failed to create test: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            binding.saveTestBtn.isEnabled = true
                            binding.saveTestBtn.text = "Save Test"
                        }
                    } else {
                        // Update existing test
                        val updateResult = DatabaseHelper.updateTest(
                            testId = existingTest!!.testId,
                            testDate = testDate,
                            testTime = testTime,
                            testLocation = testLocation,
                            examineeName = examineeName,
                            examineeDetails = examineeDetails,
                            examinerName = employee.fullName,
                            examinerEmail = employee.email,
                            examinerPhone = employee.phone,
                            resultSummary = resultSummary,
                            testOutcome = testOutcome,
                            testStatus = testStatus,
                            internalNotes = internalNotes,
                            updatedBy = employeeId
                        )

                        updateResult.onSuccess {
                            Toast.makeText(
                                this@EmployeeTestDetailsActivity,
                                "Test updated successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }.onFailure { error ->
                            Log.e(TAG, "Failed to update test", error)
                            Toast.makeText(
                                this@EmployeeTestDetailsActivity,
                                "Failed to update test: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            binding.saveTestBtn.isEnabled = true
                            binding.saveTestBtn.text = "Save Test"
                        }
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Failed to load employee details", error)
                    Toast.makeText(
                        this@EmployeeTestDetailsActivity,
                        "Failed to load employee details: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.saveTestBtn.isEnabled = true
                    binding.saveTestBtn.text = "Save Test"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception saving test", e)
                Toast.makeText(
                    this@EmployeeTestDetailsActivity,
                    "Error saving test: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.saveTestBtn.isEnabled = true
                binding.saveTestBtn.text = "Save Test"
            }
        }
    }

    private suspend fun updateTestStatus(testId: Int, status: String) {
        when (status) {
            "Completed" -> {
                val result = DatabaseHelper.completeTest(testId, employeeId)
                result.onSuccess {
                    Toast.makeText(
                        this@EmployeeTestDetailsActivity,
                        "Test marked as completed",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }.onFailure { error ->
                    Log.e(TAG, "Failed to mark test as completed", error)
                    Toast.makeText(
                        this@EmployeeTestDetailsActivity,
                        "Failed to update status: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.saveTestBtn.isEnabled = true
                    binding.saveTestBtn.text = "Save Test"
                }
            }
            "Released" -> {
                val result = DatabaseHelper.releaseTest(testId, employeeId)
                result.onSuccess {
                    Toast.makeText(
                        this@EmployeeTestDetailsActivity,
                        "Test released successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }.onFailure { error ->
                    Log.e(TAG, "Failed to release test", error)
                    Toast.makeText(
                        this@EmployeeTestDetailsActivity,
                        "Failed to release test: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.saveTestBtn.isEnabled = true
                    binding.saveTestBtn.text = "Save Test"
                }
            }
            else -> {
                // For Draft and Reviewed, status is already set in the update
                Toast.makeText(
                    this@EmployeeTestDetailsActivity,
                    "Test saved successfully",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun formatDateForInput(dateString: String): String {
        return try {
            if (dateString.contains("T")) {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = isoFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } else {
                if (dateString.length >= 10) dateString.substring(0, 10) else dateString
            }
        } catch (e: Exception) {
            Log.w(TAG, "Date formatting failed for $dateString", e)
            dateString
        }
    }

    private fun formatTimeForInput(timeString: String): String {
        return try {
            if (timeString.contains("T")) {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = isoFormat.parse(timeString)
                outputFormat.format(date ?: Date())
            } else {
                // Extract HH:mm from time string
                if (timeString.length >= 5) timeString.substring(0, 5) else timeString
            }
        } catch (e: Exception) {
            Log.w(TAG, "Time formatting failed for $timeString", e)
            timeString
        }
    }
}