package com.example.insy_7315

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.ClientPaymentsBinding
import com.example.insy_7315.models.Payment
import com.example.insy_7315.utils.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ClientPaymentsActivity : AppCompatActivity() {
    private lateinit var binding: ClientPaymentsBinding
    private lateinit var sessionManager: SessionManager
    private var allPayments = listOf<Payment>()
    private var filteredPayments = listOf<Payment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientPaymentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupClickListeners()
        setupSearchListener()
        loadPayments()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupSearchListener() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterPayments(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Clear search when clear icon is clicked
        binding.searchLayout.setEndIconOnClickListener {
            binding.searchInput.text?.clear()
            filterPayments("")
        }
    }

    private fun filterPayments(query: String) {
        val searchQuery = query.trim().lowercase(Locale.getDefault())

        if (searchQuery.isEmpty()) {
            filteredPayments = allPayments
        } else {
            filteredPayments = allPayments.filter { payment ->
                // Search by payment reference
                payment.paymentReference.lowercase(Locale.getDefault()).contains(searchQuery) ||
                        // Search by payment amount
                        String.format("%.2f", payment.paymentAmount).contains(searchQuery) ||
                        // Search by payment type
                        payment.paymentType.lowercase(Locale.getDefault()).contains(searchQuery) ||
                        // Search by payment method
                        payment.paymentMethod.lowercase(Locale.getDefault()).contains(searchQuery) ||
                        // Search by status
                        payment.paymentStatus.lowercase(Locale.getDefault()).contains(searchQuery)
            }
        }

        displayPayments(filteredPayments)
        updateTotalAmount(filteredPayments)
    }

    private fun loadPayments() {
        val user = sessionManager.getUser()
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            // Get all bookings for this client
            val bookingsResult = DatabaseHelper.getBookingsByClient(user.userId)

            bookingsResult.onSuccess { bookings ->
                val allClientPayments = mutableListOf<Payment>()

                // For each booking, get payments
                bookings.forEach { booking ->
                    val paymentsResult = DatabaseHelper.getPaymentsByBooking(booking.bookingId)
                    paymentsResult.onSuccess { payments ->
                        allClientPayments.addAll(payments)
                    }
                }

                // Sort by date (most recent first)
                allPayments = allClientPayments.sortedByDescending { it.paymentDate }
                filteredPayments = allPayments

                displayPayments(filteredPayments)
                updateTotalAmount(filteredPayments)
            }.onFailure { error ->
                Toast.makeText(
                    this@ClientPaymentsActivity,
                    "Failed to load payments: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                displayEmptyState()
            }
        }
    }

    private fun displayPayments(payments: List<Payment>) {
        binding.paymentsContainer.removeAllViews()

        if (payments.isEmpty()) {
            if (binding.searchInput.text?.isNotEmpty() == true) {
                displaySearchEmptyState()
            } else {
                displayEmptyState()
            }
            return
        }

        val inflater = LayoutInflater.from(this)
        payments.forEach { payment ->
            val itemView = inflater.inflate(R.layout.item_payment, binding.paymentsContainer, false)

            val paymentIcon = itemView.findViewById<ImageView>(R.id.paymentIcon)
            val paymentTitle = itemView.findViewById<TextView>(R.id.paymentTitle)
            val paymentDate = itemView.findViewById<TextView>(R.id.paymentDate)
            val paymentAmount = itemView.findViewById<TextView>(R.id.paymentAmount)
            val paymentStatus = itemView.findViewById<TextView>(R.id.paymentStatus)
            val viewInvoiceButton = itemView.findViewById<TextView>(R.id.viewInvoiceButton)

            // Set payment details
            paymentTitle.text = "${payment.paymentType} - ${payment.paymentReference}"
            paymentDate.text = formatDateTime(payment.paymentDate)
            paymentAmount.text = "R ${String.format("%.2f", payment.paymentAmount)}"

            // Set status
            when (payment.paymentStatus.uppercase()) {
                "COMPLETED" -> {
                    paymentStatus.text = "Status: Paid"
                    paymentStatus.setTextColor(0xFF4CAF50.toInt())
                    paymentIcon.setColorFilter(0xFFD4AF37.toInt())
                    viewInvoiceButton.text = "View Invoice"
                }
                "PENDING" -> {
                    paymentStatus.text = "Status: Pending"
                    paymentStatus.setTextColor(0xFFFFA726.toInt())
                    paymentIcon.setColorFilter(0xFFFFA726.toInt())
                    viewInvoiceButton.text = "Pay Now"
                }
                "FAILED" -> {
                    paymentStatus.text = "Status: Failed"
                    paymentStatus.setTextColor(0xFFF44336.toInt())
                    paymentIcon.setColorFilter(0xFFF44336.toInt())
                    viewInvoiceButton.text = "Retry"
                }
                else -> {
                    paymentStatus.text = "Status: ${payment.paymentStatus}"
                    paymentStatus.setTextColor(0xFF9E9E9E.toInt())
                }
            }

            // View invoice button
            viewInvoiceButton.setOnClickListener {
                if (payment.paymentStatus.uppercase() == "COMPLETED") {
                    generateInvoicePDF(payment)
                } else {
                    Toast.makeText(this, "Payment action coming soon", Toast.LENGTH_SHORT).show()
                }
            }

            binding.paymentsContainer.addView(itemView)
        }
    }

    private fun updateTotalAmount(payments: List<Payment>) {
        val total = payments
            .filter { it.paymentStatus.uppercase() == "COMPLETED" }
            .sumOf { it.paymentAmount }

        binding.totalAmountText.text = "R ${String.format("%.2f", total)}"
    }

    private fun displayEmptyState() {
        binding.paymentsContainer.removeAllViews()

        val emptyView = TextView(this).apply {
            text = "No payment history found"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            gravity = android.view.Gravity.CENTER
            setPadding(dpToPx(32), dpToPx(64), dpToPx(32), dpToPx(64))
        }

        binding.paymentsContainer.addView(emptyView)
        binding.totalAmountText.text = "R 0.00"
    }

    private fun displaySearchEmptyState() {
        binding.paymentsContainer.removeAllViews()

        val emptyView = TextView(this).apply {
            text = "No payments found for \"${binding.searchInput.text}\""
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            gravity = android.view.Gravity.CENTER
            setPadding(dpToPx(32), dpToPx(64), dpToPx(32), dpToPx(64))
        }

        binding.paymentsContainer.addView(emptyView)
        binding.totalAmountText.text = "R 0.00"
    }

    private fun generateInvoicePDF(payment: Payment) {
        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@ClientPaymentsActivity,
                    "Generating invoice...",
                    Toast.LENGTH_SHORT
                ).show()

                // Call backend to generate invoice PDF
                val result = DatabaseHelper.httpClient.request("pdf/invoice/${payment.invoiceId}", "POST")

                result.fold(
                    onSuccess = { json ->
                        val pdfUrl = json.getString("pdfUrl")
                        showInvoiceSuccess(pdfUrl, payment)
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@ClientPaymentsActivity,
                            "Failed to generate invoice: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )

            } catch (e: Exception) {
                Toast.makeText(
                    this@ClientPaymentsActivity,
                    "Failed to generate invoice: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showInvoiceSuccess(pdfUrl: String, payment: Payment) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Invoice Generated Successfully")
            .setMessage("Your invoice has been generated and stored securely. You can access it using the link below.")
            .setPositiveButton("Open Invoice") { _, _ ->
                openPdfUrl(pdfUrl)
            }
            .setNeutralButton("Copy Link") { _, _ ->
                // Copy URL to clipboard
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Invoice URL", pdfUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Invoice link copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .create()

        alertDialog.show()
    }

    private fun openPdfUrl(pdfUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pdfUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "No app available to open PDF. Link copied to clipboard.",
                Toast.LENGTH_LONG
            ).show()
            // Fallback: copy to clipboard
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("PDF URL", pdfUrl)
            clipboard.setPrimaryClip(clip)
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val output = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            output.format(input.parse(dateString) ?: Date())
        } catch (e: Exception) {
            try {
                val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val output = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                output.format(input.parse(dateString) ?: Date())
            } catch (e: Exception) {
                dateString
            }
        }
    }

    private fun formatDateTime(dateString: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val output = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
            output.format(input.parse(dateString) ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}