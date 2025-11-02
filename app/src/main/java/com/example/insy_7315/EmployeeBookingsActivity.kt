package com.example.insy_7315

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.EmployeeBookingsBinding
import com.example.insy_7315.models.Booking
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.text.SimpleDateFormat
import java.util.*

class EmployeeBookingsActivity : AppCompatActivity() {
    private lateinit var binding: EmployeeBookingsBinding
    private var allBookings = listOf<Booking>()
    private var currentCalendar = Calendar.getInstance()
    private var selectedDate: String? = null
    private var employeeId: Int = 0
    private var employeeName: String = ""

    // Use EnrichedBooking (booking + amountPaid + balance)
    private val bookingsByDate = mutableMapOf<String, List<EnrichedBooking>>()

    companion object {
        private const val TAG = "EmployeeBookings"
    }

    // Small holder to carry payment info alongside booking
    data class EnrichedBooking(
        val booking: Booking,
        val amountPaid: Double,
        val balance: Double
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EmployeeBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get employee info from intent or session
        employeeId = intent.getIntExtra("USER_ID", 0)
        employeeName = intent.getStringExtra("USER_NAME") ?: "Employee"

        Log.d(TAG, "onCreate - Employee ID: $employeeId, Name: $employeeName")

        if (employeeId == 0) {
            Toast.makeText(this, "Invalid employee session", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupClickListeners()
        loadBookings()
    }

    private fun setupUI() {
        binding.employeeNameText.text = employeeName
        updateMonthDisplay()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.prevMonthBtn.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateMonthDisplay()
            generateCalendar()
        }

        binding.nextMonthBtn.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateMonthDisplay()
            generateCalendar()
        }

        // View toggle buttons
        binding.dayViewBtn.setOnClickListener {
            updateViewToggle("day")
            // TODO: Implement day view
        }

        binding.weekViewBtn.setOnClickListener {
            updateViewToggle("week")
            // TODO: Implement week view
        }

        binding.monthViewBtn.setOnClickListener {
            updateViewToggle("month")
        }
    }

    private fun updateViewToggle(selectedView: String) {
        // Reset all buttons
        resetButtonStyle(binding.dayViewBtn)
        resetButtonStyle(binding.weekViewBtn)
        resetButtonStyle(binding.monthViewBtn)

        // Set selected button style
        when (selectedView) {
            "day" -> setSelectedButtonStyle(binding.dayViewBtn)
            "week" -> setSelectedButtonStyle(binding.weekViewBtn)
            "month" -> setSelectedButtonStyle(binding.monthViewBtn)
        }
    }

    private fun resetButtonStyle(button: MaterialButton) {
        button.setTextColor(0xFF999999.toInt())
        button.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.transparent)
    }

    private fun setSelectedButtonStyle(button: MaterialButton) {
        button.setTextColor(0xFF000000.toInt())
        button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gold)
    }

    private fun updateMonthDisplay() {
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.currentMonth.text = dateFormat.format(currentCalendar.time)
    }

    private fun loadBookings() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading bookings for employee ID: $employeeId")

                // Use the employee-specific endpoint
                val result = DatabaseHelper.getBookingsByEmployee(employeeId)

                result.onSuccess { bookings ->
                    Log.d(TAG, "Successfully loaded ${bookings.size} bookings from server")

                    // Filter only bookings assigned to this employee and status confirmed/completed
                    val relevantBookings = bookings.filter {
                        val matches = it.employeeId == employeeId &&
                                (it.bookingStatus.equals("Confirmed", true) || it.bookingStatus.equals("Completed", true))
                        if (!matches) {
                            Log.d(TAG, "Filtered out booking ${it.bookingReference}: " +
                                    "employeeId=${it.employeeId} (expected $employeeId), " +
                                    "status=${it.bookingStatus}")
                        }
                        matches
                    }

                    Log.d(TAG, "After filtering: ${relevantBookings.size} bookings remain")

                    // Enrich each booking with payment info concurrently
                    val enrichedList = relevantBookings.map { booking ->
                        async {
                            var amountPaid = 0.0
                            var balance = booking.sessionFee

                            val invoicesResult = DatabaseHelper.getInvoicesByBooking(booking.bookingId)
                            invoicesResult.onSuccess { invoices ->
                                if (invoices.isNotEmpty()) {
                                    val invoice = invoices.first()
                                    val balanceResult = DatabaseHelper.getInvoiceBalance(invoice.invoiceId)
                                    balanceResult.onSuccess { bal ->
                                        amountPaid = bal.amountPaid
                                        balance = bal.balance
                                    }.onFailure { e ->
                                        Log.w(TAG, "Failed to fetch invoice balance for invoice ${invoice.invoiceId}", e)
                                    }
                                } else {
                                    // No invoices -> everything unpaid
                                    amountPaid = 0.0
                                    balance = booking.sessionFee
                                }
                            }.onFailure { e ->
                                Log.w(TAG, "Failed to fetch invoices for booking ${booking.bookingId}", e)
                            }

                            EnrichedBooking(booking, amountPaid, balance)
                        }
                    }.awaitAll()

                    // Group enriched bookings by normalized date
                    bookingsByDate.clear()
                    enrichedList.forEach { enriched ->
                        val booking = enriched.booking
                        val rawDate = booking.actualDate ?: booking.preferredDate

                        val normalizedDate = try {
                            if (rawDate.contains("T")) {
                                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }
                                val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val parsedDate = isoFormat.parse(rawDate)
                                outputFormat.format(parsedDate ?: Date())
                            } else {
                                // assume already "yyyy-MM-dd"
                                rawDate
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Date parsing failed for $rawDate, extracting date part", e)
                            if (rawDate.length >= 10) rawDate.substring(0, 10) else rawDate
                        }

                        val list = bookingsByDate[normalizedDate]?.toMutableList() ?: mutableListOf()
                        list.add(enriched)
                        bookingsByDate[normalizedDate] = list
                    }

                    Log.d(TAG, "Bookings grouped by ${bookingsByDate.size} unique dates:")
                    bookingsByDate.forEach { (date, bookingList) ->
                        Log.d(TAG, "  $date: ${bookingList.size} booking(s)")
                    }

                    generateCalendar()

                    // Show today's appointments by default
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    Log.d(TAG, "Today's date: $today")
                    displayAppointmentsForDate(today)

                }.onFailure { error ->
                    Log.e(TAG, "Failed to load bookings", error)
                    Toast.makeText(
                        this@EmployeeBookingsActivity,
                        "Failed to load bookings: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in loadBookings", e)
                Toast.makeText(
                    this@EmployeeBookingsActivity,
                    "Error loading bookings: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun generateCalendar() {
        Log.d(TAG, "Generating calendar...")
        binding.calendarGrid.removeAllViews()

        val calendar = currentCalendar.clone() as Calendar
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayString = dateFormat.format(Date())

        Log.d(TAG, "Calendar month: ${dateFormat.format(calendar.time)}")
        Log.d(TAG, "First day of week offset: $firstDayOfWeek")
        Log.d(TAG, "Days in month: $daysInMonth")
        Log.d(TAG, "Today: $todayString")

        // Create week rows
        var currentWeekLayout: LinearLayout? = null
        var dayCount = 0

        // Add empty cells for days before month starts
        for (i in 0 until firstDayOfWeek) {
            if (dayCount % 7 == 0) {
                currentWeekLayout = createWeekLayout()
                binding.calendarGrid.addView(currentWeekLayout)
            }
            currentWeekLayout?.addView(createEmptyDayCell())
            dayCount++
        }

        // Add days of the month
        for (day in 1..daysInMonth) {
            if (dayCount % 7 == 0) {
                currentWeekLayout = createWeekLayout()
                binding.calendarGrid.addView(currentWeekLayout)
            }

            calendar.set(Calendar.DAY_OF_MONTH, day)
            val dateString = dateFormat.format(calendar.time)
            val bookingsForDay = bookingsByDate[dateString]
            val isToday = dateString == todayString

            if (bookingsForDay != null && bookingsForDay.isNotEmpty()) {
                Log.d(TAG, "Day $day ($dateString) has ${bookingsForDay.size} booking(s)")
            }

            val dayCell = createDayCell(day, bookingsForDay, isToday, dateString)
            currentWeekLayout?.addView(dayCell)
            dayCount++
        }

        // Fill remaining cells
        while (dayCount % 7 != 0) {
            currentWeekLayout?.addView(createEmptyDayCell())
            dayCount++
        }

        Log.d(TAG, "Calendar generation complete")
    }

    private fun createWeekLayout(): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            weightSum = 7f
        }
    }

    private fun createEmptyDayCell(): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(48),
                1f
            )
        }
    }

    // Adjusted to accept EnrichedBooking list
    private fun createDayCell(
        day: Int,
        bookings: List<EnrichedBooking>?,
        isToday: Boolean,
        dateString: String
    ): FrameLayout {
        val cell = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(48),
                1f
            )
            isClickable = true
            isFocusable = true

            // Set background based on booking status
            if (!bookings.isNullOrEmpty()) {
                setBackgroundColor(0xFFD4AF37.toInt())
                Log.d(TAG, "Setting gold background for day $day")
            } else if (isToday) {
                setBackgroundResource(R.drawable.today_cell_background)
                Log.d(TAG, "Setting today background for day $day")
            }
        }

        val container = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }

        val dayText = TextView(this).apply {
            text = day.toString()
            textSize = 14f
            setTextColor(
                if (!bookings.isNullOrEmpty()) 0xFF000000.toInt()
                else if (isToday) 0xFFD4AF37.toInt()
                else 0xFFFFFFFF.toInt()
            )
            if (!bookings.isNullOrEmpty() || isToday) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
        container.addView(dayText)

        // Add dots for bookings
        if (!bookings.isNullOrEmpty()) {
            val dotsLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dpToPx(2), 0, 0)
            }

            val dotCount = minOf(bookings.size, 3)
            for (i in 0 until dotCount) {
                val dot = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(4),
                        dpToPx(4)
                    ).apply {
                        if (i > 0) marginStart = dpToPx(2)
                    }
                    setBackgroundColor(0xFF000000.toInt())
                }
                dotsLayout.addView(dot)
            }
            container.addView(dotsLayout)
        }

        cell.addView(container)

        cell.setOnClickListener {
            Log.d(TAG, "Clicked on day $day ($dateString)")
            selectedDate = dateString
            displayAppointmentsForDate(dateString)
        }

        return cell
    }

    private fun displayAppointmentsForDate(dateString: String) {
        Log.d(TAG, "Displaying appointments for: $dateString")
        val enrichedBookings = bookingsByDate[dateString] ?: emptyList()
        Log.d(TAG, "Found ${enrichedBookings.size} booking(s) for this date")

        // Update selected day label
        val displayDate = try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date()).uppercase()
        } catch (e: Exception) {
            Log.e(TAG, "Date formatting error", e)
            dateString.uppercase()
        }

        binding.selectedDayLabel.text = "APPOINTMENTS - $displayDate"

        // Clear existing appointments
        binding.appointmentsList.removeAllViews()

        if (enrichedBookings.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No appointments scheduled for this day"
                textSize = 14f
                setTextColor(0xFF999999.toInt())
                gravity = android.view.Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(32), dpToPx(16), dpToPx(32))
            }
            binding.appointmentsList.addView(emptyView)
            Log.d(TAG, "Showing empty state")
        } else {
            enrichedBookings
                .sortedBy { it.booking.actualTime ?: it.booking.preferredTime }
                .forEach { enriched ->
                    Log.d(TAG, "Adding appointment view for: ${enriched.booking.bookingReference}")
                    val appointmentView = inflateAppointmentItem(enriched)
                    binding.appointmentsList.addView(appointmentView)
                }
        }
    }

    // Now accepts an EnrichedBooking so we can read amountPaid and balance
    private fun inflateAppointmentItem(enriched: EnrichedBooking): View {
        val booking = enriched.booking

        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_appointment, binding.appointmentsList, false)

        val timeText = view.findViewById<TextView>(R.id.appointmentTime)
        val paymentStatus = view.findViewById<TextView>(R.id.paymentStatus)
        val clientName = view.findViewById<TextView>(R.id.clientName)
        val testType = view.findViewById<TextView>(R.id.testType)
        val location = view.findViewById<TextView>(R.id.location)

        // Set appointment time display
        val rawTime = booking.actualTime ?: booking.preferredTime
        val displayTime = try {
            if (rawTime.contains("T")) {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val parsedTime = isoFormat.parse(rawTime)
                timeFormat.format(parsedTime ?: Date())
            } else {
                rawTime
            }
        } catch (e: Exception) {
            Log.w(TAG, "Time parsing failed for $rawTime", e)
            rawTime
        }

        Log.d(TAG, "Setting time: $rawTime -> $displayTime")
        timeText.text = displayTime

        // Determine payment status using actual amountPaid / balance
        val amountPaid = enriched.amountPaid
        val balance = enriched.balance

        val status = when {
            balance <= 0.0 -> "FULLY PAID"
            amountPaid > 0.0 -> "DEPOSIT PAID"
            else -> "PENDING PAYMENT"
        }

        paymentStatus.text = status
        paymentStatus.setBackgroundColor(when (status) {
            "FULLY PAID" -> 0xFF4CAF50.toInt()
            "DEPOSIT PAID" -> 0xFFFFA726.toInt()
            else -> 0xFFF44336.toInt()
        })

        clientName.text = booking.clientName ?: "Unknown Client"
        testType.text = booking.testName ?: "Unknown Test"
        location.text = booking.location

        Log.d(TAG, "Appointment item inflated: ${booking.clientName} at $displayTime (paid=$amountPaid, balance=$balance)")

        view.setOnClickListener {
            val intent = Intent(this, EmployeeTestDetailsActivity::class.java).apply {
                putExtra("BOOKING_ID", booking.bookingId)
                putExtra("EMPLOYEE_ID", employeeId)
                putExtra("EMPLOYEE_NAME", employeeName)
            }
            startActivity(intent)
        }

        return view
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
        loadBookings()
    }
}
