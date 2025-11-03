package com.example.insy_7315

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.ClientDetailsBookingsBinding
import com.example.insy_7315.models.Booking
import com.example.insy_7315.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BookingDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ClientDetailsBookingsBinding
    private lateinit var sessionManager: SessionManager
    private var booking: Booking? = null
    private var invoiceId: Int? = null
    private var remainingBalance: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientDetailsBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        val bookingId = intent.getIntExtra("BOOKING_ID", -1)
        if (bookingId == -1) {
            Toast.makeText(this, "Invalid booking", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        loadBookingDetails(bookingId)
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.payBalanceButton.setOnClickListener {
            payWithStripe()
        }

        binding.rescheduleButton.setOnClickListener {
            Toast.makeText(this, "Reschedule feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.cancelBookingButton.setOnClickListener {
            showCancelDialog()
        }
    }

    private fun loadBookingDetails(bookingId: Int) {
        lifecycleScope.launch {
            val result = DatabaseHelper.getBookingById(bookingId)

            result.onSuccess { bookingData ->
                booking = bookingData
                displayBookingDetails(bookingData)
                loadPaymentInfo(bookingData.bookingId)
            }.onFailure { error ->
                Toast.makeText(
                    this@BookingDetailsActivity,
                    "Failed to load booking: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun loadPaymentInfo(bookingId: Int) {
        lifecycleScope.launch {
            val invoicesResult = DatabaseHelper.getInvoicesByBooking(bookingId)

            invoicesResult.onSuccess { invoices ->
                if (invoices.isNotEmpty()) {
                    val invoice = invoices[0]
                    invoiceId = invoice.invoiceId

                    val balanceResult = DatabaseHelper.getInvoiceBalance(invoice.invoiceId)
                    balanceResult.onSuccess { balance ->
                        remainingBalance = balance.balance
                        updatePaymentUI(balance.totalAmount, balance.amountPaid, balance.balance)
                    }
                }
            }
        }
    }

    private fun displayBookingDetails(booking: Booking) {
        binding.bookingId.text = booking.bookingReference
        binding.statusBadge.text = booking.bookingStatus.uppercase()
        binding.statusBadge.setBackgroundColor(getStatusColor(booking.bookingStatus))

        binding.testTypeValue.text = booking.testName ?: "N/A"

        val dateTimeStr = "${formatDate(booking.preferredDate)} at ${formatTime(booking.preferredTime)}"
        binding.dateTimeValue.text = dateTimeStr

        binding.locationValue.text = booking.location
        binding.locationAddress.text = "Contact office for detailed directions"

        if (booking.employeeName != null) {
            binding.examinerValue.text = booking.employeeName
            binding.examinerDetails.text = "Certified Polygraph Examiner"
        } else {
            binding.examinerValue.text = "Not Yet Assigned"
            binding.examinerDetails.text = "Examiner will be assigned soon"
        }

        if (!booking.additionalNotes.isNullOrEmpty()) {
            binding.notesCard.visibility = View.VISIBLE
            binding.notesLabel.visibility = View.VISIBLE
            binding.notesText.text = booking.additionalNotes
        } else {
            binding.notesCard.visibility = View.GONE
            binding.notesLabel.visibility = View.GONE
        }

        if (booking.bookingStatus.uppercase() == "CANCELLED" ||
            booking.bookingStatus.uppercase() == "COMPLETED") {
            binding.actionsLabel.visibility = View.GONE
            binding.rescheduleButton.visibility = View.GONE
            binding.cancelBookingButton.visibility = View.GONE
        }
    }

    private fun updatePaymentUI(totalAmount: Double, amountPaid: Double, balance: Double) {
        binding.sessionFeeValue.text = "R ${String.format("%.2f", totalAmount)}"
        binding.depositPaidValue.text = "- R ${String.format("%.2f", amountPaid)}"
        binding.balanceDueValue.text = "R ${String.format("%.2f", balance)}"

        val paymentStatus = when {
            balance <= 0 -> "Fully Paid"
            amountPaid > 0 -> "Deposit Paid"
            else -> "Unpaid"
        }
        binding.paymentStatusValue.text = paymentStatus

        val statusBadge = when {
            balance <= 0 -> "FULLY PAID"
            amountPaid > 0 -> "DEPOSIT PAID"
            else -> "UNPAID"
        }
        binding.paymentStatusBadge.text = statusBadge
        binding.paymentStatusBadge.setBackgroundColor(getPaymentStatusColor(balance, totalAmount, amountPaid))

        if (balance > 0) {
            binding.payBalanceButton.visibility = View.VISIBLE
            binding.payBalanceButton.text = "PAY WITH STRIPE (R ${String.format("%.2f", balance)})"
        } else {
            binding.payBalanceButton.visibility = View.GONE
        }
    }

    private fun payWithStripe() {
        val currentBooking = booking ?: return
        val currentInvoiceId = invoiceId ?: return
        val user = sessionManager.getUser() ?: return

        if (remainingBalance <= 0) {
            Toast.makeText(this, "No balance due", Toast.LENGTH_SHORT).show()
            return
        }

        binding.payBalanceButton.isEnabled = false
        binding.payBalanceButton.text = "INITIATING PAYMENT..."

        lifecycleScope.launch {
            val paymentResult = DatabaseHelper.initiateStripePayment(
                bookingId = currentBooking.bookingId,
                invoiceId = currentInvoiceId,
                amount = remainingBalance,
                description = "Polygraph Test - ${currentBooking.testName ?: "Service"}",
                clientName = user.fullName,
                clientEmail = user.email
            )

            paymentResult.onSuccess { paymentInitiation ->
                // Open Stripe Checkout in browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentInitiation.paymentUrl))
                startActivity(intent)

                Toast.makeText(
                    this@BookingDetailsActivity,
                    "Opening Stripe payment page...",
                    Toast.LENGTH_LONG
                ).show()

                // Re-enable button after a delay
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(3000) // Wait 3 seconds
                    binding.payBalanceButton.isEnabled = true
                    binding.payBalanceButton.text = "PAY WITH STRIPE (R ${String.format("%.2f", remainingBalance)})"
                }

            }.onFailure { error ->
                Toast.makeText(
                    this@BookingDetailsActivity,
                    "Payment initiation failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.payBalanceButton.isEnabled = true
                binding.payBalanceButton.text = "PAY WITH STRIPE (R ${String.format("%.2f", remainingBalance)})"
            }
        }
    }

    private fun showCancelDialog() {
        val currentBooking = booking ?: return
        val user = sessionManager.getUser() ?: return

        AlertDialog.Builder(this)
            .setTitle("Cancel Booking")
            .setMessage("Are you sure you want to cancel this booking?\n\nBooking: ${currentBooking.bookingReference}")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                cancelBooking(currentBooking.bookingId, user.userId)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun cancelBooking(bookingId: Int, userId: Int) {
        lifecycleScope.launch {
            val result = DatabaseHelper.cancelBooking(
                bookingId = bookingId,
                cancellationReason = "Cancelled by client",
                cancelledBy = userId
            )

            result.onSuccess {
                Toast.makeText(
                    this@BookingDetailsActivity,
                    "Booking cancelled successfully",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@BookingDetailsActivity,
                    "Failed to cancel: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getStatusColor(status: String): Int {
        return when (status.uppercase()) {
            "CONFIRMED" -> 0xFF4CAF50.toInt()
            "PENDING" -> 0xFFB8960F.toInt()
            "COMPLETED" -> 0xFF2196F3.toInt()
            "CANCELLED" -> 0xFFF44336.toInt()
            else -> 0xFF9E9E9E.toInt()
        }
    }

    private fun getPaymentStatusColor(balance: Double, totalAmount: Double, amountPaid: Double): Int {
        return when {
            balance <= 0 -> 0xFF4CAF50.toInt()
            amountPaid > 0 -> 0xFFFFA726.toInt()
            else -> 0xFF9E9E9E.toInt()
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val output = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            output.format(input.parse(dateString) ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun formatTime(timeString: String): String {
        return try {
            val parts = timeString.split(":")
            val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hours)
                set(Calendar.MINUTE, minutes)
            }
            val output = SimpleDateFormat("h:mm a", Locale.getDefault())
            output.format(calendar.time)
        } catch (e: Exception) {
            timeString
        }
    }
}