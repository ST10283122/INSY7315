package com.example.insy_7315

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.ClientPaymentsBinding
import com.example.insy_7315.models.Payment
import com.example.insy_7315.utils.SessionManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ClientPaymentsActivity : AppCompatActivity() {
    private lateinit var binding: ClientPaymentsBinding
    private lateinit var sessionManager: SessionManager
    private var allPayments = listOf<Payment>()
    private val STORAGE_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientPaymentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupClickListeners()
        loadPayments()
        checkStoragePermission()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.filterButton.setOnClickListener {
            Toast.makeText(this, "Filter feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
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

                displayPayments(allPayments)
                updateTotalAmount(allPayments)
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
            displayEmptyState()
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

    private fun generateInvoicePDF(payment: Payment) {
        lifecycleScope.launch {
            // Get invoice details
            val invoiceResult = DatabaseHelper.getInvoiceById(payment.invoiceId)

            invoiceResult.onSuccess { invoice ->
                // Get booking details
                val bookingResult = DatabaseHelper.getBookingById(payment.bookingId)

                bookingResult.onSuccess { booking ->
                    try {
                        val pdfDocument = PdfDocument()
                        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
                        val page = pdfDocument.startPage(pageInfo)
                        val canvas = page.canvas

                        drawInvoice(canvas, invoice, booking, payment)

                        pdfDocument.finishPage(page)

                        // Save PDF
                        val fileName = "Invoice_${invoice.invoiceNumber}.pdf"
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloadsDir, fileName)

                        FileOutputStream(file).use { outputStream ->
                            pdfDocument.writeTo(outputStream)
                        }

                        pdfDocument.close()

                        Toast.makeText(
                            this@ClientPaymentsActivity,
                            "Invoice saved to Downloads/$fileName",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ClientPaymentsActivity,
                            "Failed to generate PDF: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun drawInvoice(canvas: Canvas, invoice: com.example.insy_7315.models.Invoice,
                            booking: com.example.insy_7315.models.Booking, payment: Payment) {
        val paint = Paint().apply {
            textSize = 12f
            color = Color.BLACK
        }

        val titlePaint = Paint().apply {
            textSize = 24f
            color = Color.parseColor("#D4AF37")
            isFakeBoldText = true
        }

        var yPos = 50f

        // Header
        canvas.drawText("KEY POLYGRAPH AND INVESTIGATIONS", 50f, yPos, titlePaint)
        yPos += 30f
        canvas.drawText("Unlocking the truth", 50f, yPos, paint)
        yPos += 50f

        // Invoice details
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("INVOICE", 50f, yPos, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Invoice #: ${invoice.invoiceNumber}", 400f, yPos, paint)
        yPos += 20f
        canvas.drawText("Date: ${formatDate(invoice.invoiceDate)}", 400f, yPos, paint)
        yPos += 40f

        // Booking details
        canvas.drawText("Booking Reference: ${booking.bookingReference}", 50f, yPos, paint)
        yPos += 20f
        canvas.drawText("Test Type: ${booking.testName}", 50f, yPos, paint)
        yPos += 20f
        canvas.drawText("Date: ${formatDate(booking.preferredDate)}", 50f, yPos, paint)
        yPos += 40f

        // Line items
        canvas.drawLine(50f, yPos, 545f, yPos, paint)
        yPos += 20f

        paint.isFakeBoldText = true
        canvas.drawText("Description", 50f, yPos, paint)
        canvas.drawText("Amount", 450f, yPos, paint)
        yPos += 5f
        canvas.drawLine(50f, yPos, 545f, yPos, paint)
        yPos += 20f

        paint.isFakeBoldText = false
        canvas.drawText(booking.testName ?: "Service", 50f, yPos, paint)
        canvas.drawText("R ${String.format("%.2f", invoice.totalAmount)}", 450f, yPos, paint)
        yPos += 30f

        // Totals
        canvas.drawLine(50f, yPos, 545f, yPos, paint)
        yPos += 20f

        canvas.drawText("Subtotal:", 350f, yPos, paint)
        canvas.drawText("R ${String.format("%.2f", invoice.totalAmount)}", 450f, yPos, paint)
        yPos += 20f

        canvas.drawText("Tax:", 350f, yPos, paint)
        canvas.drawText("R ${String.format("%.2f", invoice.taxAmount)}", 450f, yPos, paint)
        yPos += 20f

        canvas.drawText("Discount:", 350f, yPos, paint)
        canvas.drawText("R ${String.format("%.2f", invoice.discountAmount)}", 450f, yPos, paint)
        yPos += 30f

        paint.isFakeBoldText = true
        paint.textSize = 14f
        canvas.drawText("Total:", 350f, yPos, paint)
        canvas.drawText("R ${String.format("%.2f", invoice.totalAmount)}", 450f, yPos, paint)
        yPos += 40f

        // Payment info
        paint.isFakeBoldText = false
        paint.textSize = 12f
        canvas.drawText("Payment Reference: ${payment.paymentReference}", 50f, yPos, paint)
        yPos += 20f
        canvas.drawText("Payment Method: ${payment.paymentMethod}", 50f, yPos, paint)
        yPos += 20f
        canvas.drawText("Payment Date: ${formatDateTime(payment.paymentDate)}", 50f, yPos, paint)
        yPos += 20f
        canvas.drawText("Status: ${payment.paymentStatus}", 50f, yPos, paint)

        // Footer
        yPos = 800f
        paint.textSize = 10f
        paint.color = Color.GRAY
        canvas.drawText("Thank you for your business!", 50f, yPos, paint)
        yPos += 15f
        canvas.drawText("Secure • Confidential • Professional", 50f, yPos, paint)
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