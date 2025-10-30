package com.example.insy_7315

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.ClientCreateBookingsBinding
import com.example.insy_7315.models.InvoiceLineItem
import com.example.insy_7315.models.TestType
import com.example.insy_7315.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ClientCreateBookingsActivity : AppCompatActivity() {
    private lateinit var binding: ClientCreateBookingsBinding
    private lateinit var sessionManager: SessionManager

    private var testTypes = listOf<TestType>()
    private var selectedTestType: TestType? = null
    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var selectedLocation: String = ""

    private val locations = listOf(
        "Johannesburg - Head Office",
        "Pretoria - Branch Office",
        "Cape Town - Branch Office",
        "Durban - Branch Office",
        "Client Location (Additional Fee)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientCreateBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupClickListeners()
        loadTestTypes()
        setupLocationDropdown()
        setupPaymentOptionListeners()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish()
        }

        // Date Picker
        binding.dateInput.setOnClickListener {
            showDatePicker()
        }

        // Time Picker
        binding.timeInput.setOnClickListener {
            showTimePicker()
        }

        // Cancel Button
        binding.cancelButton.setOnClickListener {
            finish()
        }

        // Proceed Button
        binding.proceedButton.setOnClickListener {
            validateAndCreateBooking()
        }
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
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupTestTypeDropdown(types: List<TestType>) {
        val testTypeNames = types.map { it.testName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, testTypeNames)
        binding.testTypeInput.setAdapter(adapter)

        binding.testTypeInput.setOnItemClickListener { _, _, position, _ ->
            selectedTestType = types[position]
            updatePricing(types[position])
        }
    }

    private fun setupLocationDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, locations)
        binding.locationInput.setAdapter(adapter)

        binding.locationInput.setOnItemClickListener { _, _, position, _ ->
            selectedLocation = locations[position]
        }
    }

    private fun setupPaymentOptionListeners() {
        binding.paymentChoiceGroup.setOnCheckedChangeListener { _, _ ->
            updatePaymentButton()
        }
    }

    private fun updatePricing(testType: TestType) {
        if (testType.baseFee != null) {
            val sessionFee = testType.baseFee
            val deposit = sessionFee * 0.3

            binding.sessionFeeAmount.text = "R ${String.format("%.2f", sessionFee)}"
            binding.depositAmount.text = "R ${String.format("%.2f", deposit)}"

            binding.pricingCard.visibility = View.VISIBLE
            binding.paymentChoiceLabel.visibility = View.VISIBLE
            binding.paymentChoiceGroup.visibility = View.VISIBLE
            binding.paymentMethodLabel.visibility = View.VISIBLE
            binding.paymentMethodGroup.visibility = View.VISIBLE

            updatePaymentButton()
        } else {
            // Case dependent pricing
            binding.sessionFeeAmount.text = "TBD"
            binding.depositAmount.text = "TBD"
            binding.depositNote.text = "Quote will be provided after review"

            binding.pricingCard.visibility = View.VISIBLE
            binding.paymentChoiceLabel.visibility = View.GONE
            binding.paymentChoiceGroup.visibility = View.GONE
            binding.paymentMethodLabel.visibility = View.GONE
            binding.paymentMethodGroup.visibility = View.GONE

            binding.proceedButton.text = "SUBMIT BOOKING REQUEST"
        }
    }

    private fun updatePaymentButton() {
        selectedTestType?.let { testType ->
            if (testType.baseFee != null) {
                val amount = if (binding.depositRadio.isChecked) {
                    testType.baseFee * 0.3
                } else {
                    testType.baseFee
                }
                binding.proceedButton.text = "PROCEED TO PAYMENT (R ${String.format("%.2f", amount)})"
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                selectedDate = dateFormat.format(selectedCalendar.time)

                val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                binding.dateInput.setText(displayFormat.format(selectedCalendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Don't allow past dates
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()

        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val timeCalendar = Calendar.getInstance()
                timeCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                timeCalendar.set(Calendar.MINUTE, minute)

                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                selectedTime = timeFormat.format(timeCalendar.time)

                val displayFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                binding.timeInput.setText(displayFormat.format(timeCalendar.time))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        )

        timePickerDialog.show()
    }

    private fun validateAndCreateBooking() {
        // Validate test type
        if (selectedTestType == null) {
            binding.testTypeLayout.error = "Please select a test type"
            binding.testTypeInput.requestFocus()
            return
        }
        binding.testTypeLayout.error = null

        // Validate date
        if (selectedDate.isEmpty()) {
            binding.dateLayout.error = "Please select a date"
            binding.dateInput.requestFocus()
            return
        }
        binding.dateLayout.error = null

        // Validate time
        if (selectedTime.isEmpty()) {
            binding.timeLayout.error = "Please select a time"
            binding.timeInput.requestFocus()
            return
        }
        binding.timeLayout.error = null

        // Validate location
        if (selectedLocation.isEmpty()) {
            binding.locationLayout.error = "Please select a location"
            binding.locationInput.requestFocus()
            return
        }
        binding.locationLayout.error = null

        // Get user
        val user = sessionManager.getUser()
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Calculate amounts
        val testType = selectedTestType!!
        val sessionFee = testType.baseFee ?: 0.0
        val depositRequired = if (testType.baseFee != null) {
            if (binding.depositRadio.isChecked) {
                sessionFee * 0.3
            } else {
                sessionFee
            }
        } else {
            0.0
        }

        val additionalNotes = binding.notesInput.text.toString().trim()
        val paymentMethod = getSelectedPaymentMethod()
        val paymentGateway = getSelectedPaymentGateway()

        // Disable button
        binding.proceedButton.isEnabled = false
        binding.proceedButton.text = "PROCESSING..."

        lifecycleScope.launch {
            // Step 1: Create Booking
            val bookingResult = DatabaseHelper.createBooking(
                clientId = user.userId,
                testTypeId = testType.testTypeId,
                preferredDate = selectedDate,
                preferredTime = selectedTime,
                location = selectedLocation,
                additionalNotes = additionalNotes.ifEmpty { null },
                sessionFee = sessionFee,
                depositRequired = depositRequired
            )

            bookingResult.onSuccess { booking ->
                // Step 2: Create Invoice
                val invoiceResult = DatabaseHelper.createInvoice(
                    bookingId = booking.bookingId,
                    dueDate = null, // Immediate payment
                    totalAmount = sessionFee,
                    taxAmount = 0.0,
                    discountAmount = 0.0,
                    notes = "Invoice for ${testType.testName} - Booking ${booking.bookingReference}",
                    lineItems = listOf(
                        InvoiceLineItem(
                            description = testType.testName,
                            quantity = 1.0,
                            unitPrice = sessionFee
                        )
                    )
                )

                invoiceResult.onSuccess { invoice ->
                    // Step 3: Create Payment (simulated - gateway integration later)
                    if (depositRequired > 0) {
                        val paymentType = if (depositRequired >= sessionFee) "Full Payment" else "Deposit"

                        // Simulate payment success
                        val paymentResult = DatabaseHelper.createPayment(
                            invoiceId = invoice.invoiceId,
                            bookingId = booking.bookingId,
                            paymentAmount = depositRequired,
                            paymentMethod = paymentMethod,
                            paymentType = paymentType,
                            transactionId = "SIM_${System.currentTimeMillis()}", // Simulated transaction ID
                            paymentGateway = paymentGateway,
                            notes = "Payment via $paymentGateway (Simulated)",
                            createdBy = user.userId
                        )

                        paymentResult.onSuccess { payment ->
                            Toast.makeText(
                                this@ClientCreateBookingsActivity,
                                "Success!\n\nBooking: ${booking.bookingReference}\nInvoice: ${invoice.invoiceNumber}\nPayment: ${payment.paymentReference}",
                                Toast.LENGTH_LONG
                            ).show()

                            setResult(RESULT_OK)
                            finish()
                        }.onFailure { error ->
                            Toast.makeText(
                                this@ClientCreateBookingsActivity,
                                "Booking created but payment failed: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            binding.proceedButton.isEnabled = true
                            updatePaymentButton()
                        }
                    } else {
                        // No payment required (case dependent pricing)
                        Toast.makeText(
                            this@ClientCreateBookingsActivity,
                            "Booking request submitted!\n\nReference: ${booking.bookingReference}\nQuote will be sent shortly.",
                            Toast.LENGTH_LONG
                        ).show()

                        setResult(RESULT_OK)
                        finish()
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        this@ClientCreateBookingsActivity,
                        "Booking created but invoice failed: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.proceedButton.isEnabled = true
                    updatePaymentButton()
                }
            }.onFailure { error ->
                Toast.makeText(
                    this@ClientCreateBookingsActivity,
                    "Failed to create booking: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.proceedButton.isEnabled = true
                updatePaymentButton()
            }
        }
    }

    private fun getSelectedPaymentMethod(): String {
        return when {
            binding.payfastRadio.isChecked -> "Credit Card" // PayFast supports cards
            binding.ozowRadio.isChecked -> "Bank Transfer" // Ozow is instant EFT
            binding.stripeRadio.isChecked -> "Credit Card" // Stripe supports cards
            else -> "Credit Card"
        }
    }

    private fun getSelectedPaymentGateway(): String {
        return when {
            binding.payfastRadio.isChecked -> "PayFast"
            binding.ozowRadio.isChecked -> "Ozow"
            binding.stripeRadio.isChecked -> "Stripe"
            else -> "PayFast"
        }
    }
}