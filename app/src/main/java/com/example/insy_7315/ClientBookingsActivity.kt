package com.example.insy_7315

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.ClientBookingsBinding
import com.example.insy_7315.models.Booking
import com.example.insy_7315.utils.SessionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.text.SimpleDateFormat
import java.util.*

class ClientBookingsActivity : AppCompatActivity() {

    private lateinit var binding: ClientBookingsBinding
    private lateinit var sessionManager: SessionManager
    private var allBookings = listOf<Booking>()
    private var filteredBookings = listOf<Booking>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        setupClickListeners()
        setupSearch()
        loadBookings()
    }

    override fun onResume() {
        super.onResume()
        loadBookings() // Refresh data when returning to this screen
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.addBookingButton.setOnClickListener {
            startActivity(Intent(this, ClientCreateBookingsActivity::class.java))
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterBookings(s.toString())
            }
        })
    }

    private fun loadBookings() {
        val user = sessionManager.getUser()
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val result = DatabaseHelper.getBookingsByClient(user.userId)
            result.onSuccess { bookings ->
                // Now fetch invoice + balance info for each booking
                val enrichedBookings = bookings.map { booking ->
                    async {
                        val invoicesResult = DatabaseHelper.getInvoicesByBooking(booking.bookingId)
                        var totalAmount = booking.sessionFee
                        var amountPaid = 0.0
                        var balance = booking.sessionFee

                        invoicesResult.onSuccess { invoices ->
                            if (invoices.isNotEmpty()) {
                                val invoice = invoices.first()
                                val balanceResult = DatabaseHelper.getInvoiceBalance(invoice.invoiceId)
                                balanceResult.onSuccess { bal ->
                                    totalAmount = bal.totalAmount
                                    amountPaid = bal.amountPaid
                                    balance = bal.balance
                                }
                            }
                        }

                        Triple(booking, amountPaid, balance)
                    }
                }.awaitAll()

                allBookings = enrichedBookings.map { it.first }
                filteredBookings = allBookings
                displayBookings(enrichedBookings)
            }.onFailure { error ->
                Toast.makeText(
                    this@ClientBookingsActivity,
                    "Failed to load bookings: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                displayEmptyState()
            }
        }
    }

    private fun filterBookings(query: String) {
        val lowerQuery = query.lowercase(Locale.getDefault())
        filteredBookings = if (query.isEmpty()) {
            allBookings
        } else {
            allBookings.filter { booking ->
                booking.bookingReference.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                        booking.testName?.lowercase(Locale.getDefault())?.contains(lowerQuery) == true ||
                        booking.preferredDate.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                        booking.location.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                        booking.bookingStatus.lowercase(Locale.getDefault()).contains(lowerQuery)
            }
        }

        // refetch payment info for filtered set
        lifecycleScope.launch {
            val enriched = filteredBookings.map { booking ->
                async {
                    val invoicesResult = DatabaseHelper.getInvoicesByBooking(booking.bookingId)
                    var totalAmount = booking.sessionFee
                    var amountPaid = 0.0
                    var balance = booking.sessionFee

                    invoicesResult.onSuccess { invoices ->
                        if (invoices.isNotEmpty()) {
                            val invoice = invoices.first()
                            val balanceResult = DatabaseHelper.getInvoiceBalance(invoice.invoiceId)
                            balanceResult.onSuccess { bal ->
                                totalAmount = bal.totalAmount
                                amountPaid = bal.amountPaid
                                balance = bal.balance
                            }
                        }
                    }

                    Triple(booking, amountPaid, balance)
                }
            }.awaitAll()
            displayBookings(enriched)
        }
    }

    private fun displayBookings(enrichedBookings: List<Triple<Booking, Double, Double>>) {
        binding.bookingsList.removeAllViews()
        if (enrichedBookings.isEmpty()) {
            displayEmptyState()
            return
        }

        val inflater = LayoutInflater.from(this)
        enrichedBookings.forEach { (booking, amountPaid, balance) ->
            val cardView = inflater.inflate(R.layout.item_booking_card, binding.bookingsList, false)

            val bookingId = cardView.findViewById<TextView>(R.id.bookingId)
            val testType = cardView.findViewById<TextView>(R.id.testType)
            val bookingStatus = cardView.findViewById<TextView>(R.id.bookingStatus)
            val dateCol = cardView.findViewById<LinearLayout>(R.id.dateCol)
            val timeCol = cardView.findViewById<LinearLayout>(R.id.timeCol)
            val locationCol = cardView.findViewById<LinearLayout>(R.id.locationCol)
            val examinerCol = cardView.findViewById<LinearLayout>(R.id.examinerCol)
            val paymentRow = cardView.findViewById<LinearLayout>(R.id.paymentRow)
            val viewDetails = cardView.findViewById<MaterialButton>(R.id.viewDetails)

            bookingId.text = booking.bookingReference
            testType.text = booking.testName ?: "N/A"
            bookingStatus.text = booking.bookingStatus.uppercase()
            bookingStatus.setBackgroundColor(ContextCompat.getColor(this, getStatusColor(booking.bookingStatus)))

            addInfoItem(dateCol, "DATE", formatDate(booking.preferredDate))
            addInfoItem(timeCol, "TIME", formatTime(booking.preferredTime))
            addInfoItem(locationCol, "LOCATION", booking.location)
            addInfoItem(examinerCol, "EXAMINER", booking.employeeName ?: "Not Assigned")
            addPaymentRow(paymentRow, amountPaid, balance)

            viewDetails.setOnClickListener {
                val intent = Intent(this, BookingDetailsActivity::class.java)
                intent.putExtra("BOOKING_ID", booking.bookingId)
                startActivity(intent)
            }

            binding.bookingsList.addView(cardView)
        }
    }

    private fun addInfoItem(parent: LinearLayout, label: String, value: String) {
        parent.removeAllViews()
        val lbl = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(0xFF999999.toInt())
        }
        val valText = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        parent.addView(lbl)
        parent.addView(valText)
    }

    private fun addPaymentRow(parent: LinearLayout, amountPaid: Double, balance: Double) {
        parent.removeAllViews()

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_info_details)
            setColorFilter(0xFFD4AF37.toInt())
            layoutParams = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20))
        }

        val label = TextView(this).apply {
            text = "Payment Status"
            textSize = 13f
            setTextColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(10)
            }
        }

        val (statusText, statusColorRes) = getDynamicPaymentStatus(amountPaid, balance)

        val status = TextView(this).apply {
            text = statusText
            textSize = 11f
            setBackgroundColor(ContextCompat.getColor(this@ClientBookingsActivity, statusColorRes))
            setTextColor(0xFF000000.toInt())
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
        }

        parent.addView(icon)
        parent.addView(label)
        parent.addView(status)
    }

    private fun displayEmptyState() {
        binding.bookingsList.removeAllViews()
        val text = TextView(this).apply {
            text = "No bookings found"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            gravity = android.view.Gravity.CENTER
            setPadding(dpToPx(32), dpToPx(64), dpToPx(32), dpToPx(64))
        }
        binding.bookingsList.addView(text)
    }

    private fun getDynamicPaymentStatus(amountPaid: Double, balance: Double): Pair<String, Int> {
        return when {
            balance <= 0 -> "FULLY PAID" to android.R.color.holo_green_light
            amountPaid > 0 -> "DEPOSIT PAID" to android.R.color.holo_orange_light
            else -> "UNPAID" to android.R.color.darker_gray
        }
    }

    private fun getStatusColor(status: String): Int {
        return when (status.uppercase()) {
            "CONFIRMED" -> android.R.color.holo_green_light
            "PENDING" -> android.R.color.holo_orange_light
            "COMPLETED" -> android.R.color.holo_blue_light
            "CANCELLED" -> android.R.color.holo_red_light
            else -> android.R.color.darker_gray
        }
    }

    private fun formatDate(dateString: String): String = try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val output = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        output.format(input.parse(dateString) ?: Date())
    } catch (e: Exception) { dateString }

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

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
