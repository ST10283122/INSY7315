package com.example.insy_7315

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.ClientCreateBookingsBinding
import com.example.insy_7315.models.InvoiceLineItem
import com.example.insy_7315.models.PaymentGateway
import com.example.insy_7315.models.PaymentOption
import com.example.insy_7315.models.TestType
import com.example.insy_7315.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ClientCreateBookingsActivity : AppCompatActivity() {
    private lateinit var binding: ClientCreateBookingsBinding
    private lateinit var sessionManager: SessionManager
    private var selectedTestType: TestType? = null
    private var selectedDate: String? = null
    private var selectedTime: String? = null
    private var selectedLocation: String? = null
    private var testTypes = listOf<TestType>()
    private var selectedPaymentOption: PaymentOption = PaymentOption.DEPOSIT

    private val locations = listOf(
        "Head Office - Secunda",
        "Johannesburg Branch",
        "Pretoria Branch",
        "Cape Town Branch",
        "Durban Branch",
        "Client Location (Additional Charges Apply)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientCreateBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupClickListeners()
        setupPaymentOptions()
        loadTestTypes()
        setupLocationDropdown()

        // Handle deep links from onCreate as well
        handleDeepLink(intent)
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.dateInput.setOnClickListener {
            showDatePicker()
        }

        binding.timeInput.setOnClickListener {
            showTimePicker()
        }

        binding.proceedButton.setOnClickListener {
            validateAndProceed()
        }

        binding.cancelButton.setOnClickListener {
            finish()
        }

        binding.paymentChoiceGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedPaymentOption = when (checkedId) {
                R.id.depositRadio -> PaymentOption.DEPOSIT
                R.id.fullRadio -> PaymentOption.FULL
                else -> PaymentOption.DEPOSIT
            }
            updatePaymentAmounts()
        }
    }

    private fun setupPaymentOptions() {
        binding.depositRadio.isChecked = true
        selectedPaymentOption = PaymentOption.DEPOSIT
    }

    private fun loadTestTypes() {
        lifecycleScope.launch {
            val result = DatabaseHelper.getTestTypes()
            result.onSuccess { types ->
                testTypes = types
                setupTestTypeDropdown(types)
            }.onFailure { error ->
                Toast.makeText(
                    this@ClientCreateBookingsActivity,
                    "Failed to load test types: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupTestTypeDropdown(testTypes: List<TestType>) {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            testTypes.map { it.testName }
        )
        binding.testTypeInput.setAdapter(adapter)

        binding.testTypeInput.setOnItemClickListener { _, _, position, _ ->
            selectedTestType = testTypes[position]
            updatePricingDisplay()
        }
    }

    private fun setupLocationDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            locations
        )
        binding.locationInput.setAdapter(adapter)

        binding.locationInput.setOnItemClickListener { _, _, position, _ ->
            selectedLocation = locations[position]
        }
    }

    private fun updatePricingDisplay() {
        selectedTestType?.let { testType ->
            val baseFee = testType.baseFee ?: 0.0
            val deposit = baseFee * 0.30

            binding.sessionFeeAmount.text = if (baseFee > 0) {
                "R ${String.format("%.2f", baseFee)}"
            } else {
                "Case Dependent"
            }

            binding.depositAmount.text = if (baseFee > 0) {
                "R ${String.format("%.2f", deposit)}"
            } else {
                "TBD"
            }

            updatePaymentAmounts()
        }
    }

    private fun updatePaymentAmounts() {
        selectedTestType?.let { testType ->
            val baseFee = testType.baseFee ?: 0.0
            val deposit = baseFee * 0.30

            val amount = when (selectedPaymentOption) {
                PaymentOption.DEPOSIT -> deposit
                PaymentOption.FULL -> baseFee
            }

            val buttonText = when (selectedPaymentOption) {
                PaymentOption.DEPOSIT -> "PROCEED TO PAYMENT (R ${String.format("%.2f", deposit)})"
                PaymentOption.FULL -> "PROCEED TO PAYMENT (R ${String.format("%.2f", baseFee)})"
            }

            binding.proceedButton.text = buttonText
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                selectedDate = formattedDate

                val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(formattedDate)
                binding.dateInput.setText(displayFormat.format(parsedDate ?: Date()))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePicker.datePicker.minDate = calendar.timeInMillis
        datePicker.show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val timePicker = TimePickerDialog(
            this,
            { _, hour, minute ->
                val formattedTime = String.format("%02d:%02d:00", hour, minute)
                selectedTime = formattedTime
                binding.timeInput.setText(String.format("%02d:%02d", hour, minute))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePicker.show()
    }

    private fun validateAndProceed() {
        val user = sessionManager.getUser()
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedTestType == null) {
            Toast.makeText(this, "Please select a test type", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedDate == null) {
            Toast.makeText(this, "Please select a preferred date", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedTime == null) {
            Toast.makeText(this, "Please select a preferred time", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedLocation == null) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
            return
        }

        val testType = selectedTestType!!
        if (testType.baseFee == null || testType.baseFee == 0.0) {
            Toast.makeText(
                this,
                "This test type requires a custom quote. Please contact us.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        showConfirmationDialog()
    }

    private fun showConfirmationDialog() {
        val testType = selectedTestType!!
        val baseFee = testType.baseFee!!
        val deposit = baseFee * 0.30

        val paymentAmount = when (selectedPaymentOption) {
            PaymentOption.DEPOSIT -> deposit
            PaymentOption.FULL -> baseFee
        }

        val message = """
            Test Type: ${testType.testName}
            Date: ${binding.dateInput.text}
            Time: ${binding.timeInput.text}
            Location: $selectedLocation
            
            Session Fee: R ${String.format("%.2f", baseFee)}
            ${if (selectedPaymentOption == PaymentOption.DEPOSIT)
            "Deposit (30%): R ${String.format("%.2f", deposit)}"
        else
            "Full Payment: R ${String.format("%.2f", baseFee)}"}
            
            Payment Method: Stripe (Credit/Debit Card)
            
            Continue to payment?
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Confirm Booking")
            .setMessage(message)
            .setPositiveButton("Proceed") { _, _ ->
                createBookingAndInitiatePayment()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createBookingAndInitiatePayment() {
        val user = sessionManager.getUser() ?: return
        val testType = selectedTestType ?: return
        val baseFee = testType.baseFee ?: return

        binding.proceedButton.isEnabled = false
        binding.proceedButton.text = "PROCESSING..."

        lifecycleScope.launch {
            try {
                val deposit = baseFee * 0.30
                val additionalNotes = binding.notesInput.text.toString()

                val bookingResult = DatabaseHelper.createBooking(
                    clientId = user.userId,
                    testTypeId = testType.testTypeId,
                    preferredDate = selectedDate!!,
                    preferredTime = selectedTime!!,
                    location = selectedLocation!!,
                    additionalNotes = additionalNotes.ifBlank { null },
                    sessionFee = baseFee,
                    depositRequired = deposit
                )

                bookingResult.onSuccess { booking ->
                    val invoiceResult = DatabaseHelper.createInvoice(
                        bookingId = booking.bookingId,
                        dueDate = selectedDate,
                        totalAmount = baseFee,
                        taxAmount = 0.0,
                        discountAmount = 0.0,
                        notes = "Payment for ${testType.testName}",
                        lineItems = listOf(
                            InvoiceLineItem(
                                description = testType.testName,
                                quantity = 1.0,
                                unitPrice = baseFee
                            )
                        )
                    )

                    invoiceResult.onSuccess { invoice ->
                        val paymentAmount = when (selectedPaymentOption) {
                            PaymentOption.DEPOSIT -> deposit
                            PaymentOption.FULL -> baseFee
                        }

                        initiateStripePayment(
                            bookingId = booking.bookingId,
                            invoiceId = invoice.invoiceId,
                            amount = paymentAmount,
                            clientName = user.fullName,
                            clientEmail = user.email
                        )
                    }.onFailure { error ->
                        runOnUiThread {
                            binding.proceedButton.isEnabled = true
                            updatePaymentAmounts()
                            Toast.makeText(
                                this@ClientCreateBookingsActivity,
                                "Failed to create invoice: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.onFailure { error ->
                    runOnUiThread {
                        binding.proceedButton.isEnabled = true
                        updatePaymentAmounts()
                        Toast.makeText(
                            this@ClientCreateBookingsActivity,
                            "Failed to create booking: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.proceedButton.isEnabled = true
                    updatePaymentAmounts()
                    Toast.makeText(
                        this@ClientCreateBookingsActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun initiateStripePayment(
        bookingId: Int,
        invoiceId: Int,
        amount: Double,
        clientName: String,
        clientEmail: String
    ) {
        val testType = selectedTestType!!
        val description = "${testType.testName} - Polygraph Test Payment"

        val result = DatabaseHelper.initiateStripePayment(
            bookingId, invoiceId, amount, description, clientName, clientEmail
        )

        result.onSuccess { paymentInitiation ->
            runOnUiThread {
                openStripePayment(paymentInitiation)
            }
        }.onFailure { error ->
            runOnUiThread {
                binding.proceedButton.isEnabled = true
                updatePaymentAmounts()
                Toast.makeText(
                    this@ClientCreateBookingsActivity,
                    "Failed to initiate payment: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openStripePayment(paymentInitiation: com.example.insy_7315.models.PaymentInitiation) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentInitiation.paymentUrl))
        startActivity(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "key-polygraph" && uri.host == "payment") {
                when (uri.lastPathSegment) {
                    "success" -> {
                        Toast.makeText(this, "Payment successful!", Toast.LENGTH_LONG).show()
                        val newIntent = Intent(this, ClientPortalActivity::class.java)
                        newIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(newIntent)
                        finish()
                    }
                    "cancel" -> {
                        Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show()
                        binding.proceedButton.isEnabled = true
                        updatePaymentAmounts()
                    }
                    "error" -> {
                        Toast.makeText(this, "Payment error occurred", Toast.LENGTH_LONG).show()
                        binding.proceedButton.isEnabled = true
                        updatePaymentAmounts()
                    }
                }
            }
        }
    }
}