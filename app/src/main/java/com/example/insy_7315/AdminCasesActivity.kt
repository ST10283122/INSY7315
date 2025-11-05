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
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.AdminCasesBinding
import com.example.insy_7315.models.Booking
import com.example.insy_7315.models.Test
import com.example.insy_7315.models.User
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AdminCasesActivity : AppCompatActivity() {
    private lateinit var binding: AdminCasesBinding
    private var allBookings = listOf<Booking>()
    private var filteredBookings = listOf<Booking>()
    private var allEmployees = listOf<User>()
    private var allTests = listOf<Test>()
    private var filteredTests = listOf<Test>()
    private var pendingPageSize = 10
    private var pendingCurrentPage = 1
    private var archivedPageSize = 10
    private var archivedCurrentPage = 1
    private val adminUserId = 1
    companion object {
        private const val TAG = "AdminCases"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminCasesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupSearch()
        loadData()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.loadMorePendingButton.setOnClickListener {
            pendingCurrentPage++
            displayPendingCases()
        }

        binding.loadMoreArchivedButton.setOnClickListener {
            archivedCurrentPage++
            displayArchivedTests()
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterData(s.toString())
            }
        })
    }

    private fun loadData() {
        lifecycleScope.launch {
            val bookingsResult = DatabaseHelper.getAllBookings()
            bookingsResult.onSuccess { bookings ->
                allBookings = bookings
                filterData("")
                updateStatistics()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminCasesActivity,
                    "Failed to load bookings: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

            // Load employees for assignment
            val usersResult = DatabaseHelper.getAllUsers()
            usersResult.onSuccess { users ->
                allEmployees = users.filter { it.userRole == "Employee" && it.accountStatus == "Active" }
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminCasesActivity,
                    "Failed to load employees: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

            // Load all tests
            val testsResult = DatabaseHelper.getAllTests()
            testsResult.onSuccess { tests ->
                allTests = tests.filter { it.testStatus == "Released" }
                filterData("")
                updateStatistics()
            }.onFailure { error ->
                Log.e(TAG, "Failed to load tests", error)
                Toast.makeText(this@AdminCasesActivity,
                    "Failed to load tests: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun filterData(query: String) {
        filteredBookings = if (query.isEmpty()) {
            allBookings
        } else {
            allBookings.filter { booking ->
                booking.bookingReference.contains(query, ignoreCase = true) ||
                        booking.testName?.contains(query, ignoreCase = true) == true ||
                        booking.location.contains(query, ignoreCase = true) ||
                        booking.clientName?.contains(query, ignoreCase = true) == true
            }
        }

        filteredTests = if (query.isEmpty()) {
            allTests
        } else {
            allTests.filter { test ->
                test.examineeName.contains(query, ignoreCase = true) ||
                        test.testName?.contains(query, ignoreCase = true) == true ||
                        test.examinerName.contains(query, ignoreCase = true) ||
                        test.testId.toString().contains(query)
            }
        }

        pendingCurrentPage = 1
        archivedCurrentPage = 1
        displayPendingCases()
        displayArchivedTests()
    }

    private fun updateStatistics() {
        val pendingCount = allBookings.count { it.bookingStatus == "Pending" }
        val confirmedCount = allBookings.count { it.bookingStatus == "Confirmed" }
        val completedCount = allBookings.count { it.bookingStatus == "Completed" }
        val archivedCount = allTests.count { it.testStatus == "Released" }

        binding.pendingCount.text = pendingCount.toString()
        binding.confirmedCount.text = confirmedCount.toString()
        binding.completedCount.text = completedCount.toString()
        binding.archivedCount.text = archivedCount.toString()
    }

    private fun displayPendingCases() {
        binding.pendingCasesList.removeAllViews()

        val pendingCases = filteredBookings.filter {
            it.bookingStatus == "Pending" || it.bookingStatus == "Confirmed"
        }

        if (pendingCases.isEmpty()) {
            val emptyView = createEmptyTextView("No pending cases found")
            binding.pendingCasesList.addView(emptyView)
            binding.loadMorePendingButton.visibility = View.GONE
            return
        }

        val casesToShow = pendingCases.take(pendingPageSize * pendingCurrentPage)
        val inflater = LayoutInflater.from(this)

        casesToShow.forEachIndexed { index, booking ->
            val caseView = inflater.inflate(R.layout.item_pending_case, binding.pendingCasesList, false)

            val bookingReference = caseView.findViewById<TextView>(R.id.bookingReference)
            val clientInfo = caseView.findViewById<TextView>(R.id.clientInfo)
            val dateTime = caseView.findViewById<TextView>(R.id.dateTime)
            val statusBadge = caseView.findViewById<TextView>(R.id.statusBadge)
            val examinerName = caseView.findViewById<TextView>(R.id.examinerName)
            val assignButton = caseView.findViewById<MaterialButton>(R.id.assignButton)

            bookingReference.text = booking.bookingReference
            clientInfo.text = "${booking.testName ?: "Unknown Test"}"

            val formattedDate = formatDate(booking.preferredDate)
            dateTime.text = "$formattedDate at ${booking.preferredTime}"

            statusBadge.text = booking.bookingStatus.uppercase()
            statusBadge.setBackgroundColor(getStatusColor(booking.bookingStatus))

            if (booking.employeeId != null && booking.employeeName != null) {
                examinerName.text = booking.employeeName
                examinerName.setTextColor(0xFF4CAF50.toInt())
                assignButton.text = "REASSIGN"
                assignButton.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.transparent)
                assignButton.setTextColor(0xFFD4AF37.toInt())
                assignButton.strokeColor = ContextCompat.getColorStateList(this, R.color.gold)
                assignButton.strokeWidth = 2
            } else {
                examinerName.text = "Not Assigned"
                examinerName.setTextColor(0xFFF44336.toInt())
                assignButton.text = "ASSIGN"
                assignButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gold)
                assignButton.setTextColor(0xFF000000.toInt())
            }

            assignButton.setOnClickListener {
                showAssignExaminerDialog(booking)
            }

            caseView.setOnClickListener {
                Toast.makeText(this, "View details for ${booking.bookingReference}", Toast.LENGTH_SHORT).show()
            }

            binding.pendingCasesList.addView(caseView)

            if (index < casesToShow.size - 1) {
                addDivider(binding.pendingCasesList)
            }
        }
        binding.loadMorePendingButton.visibility = if (casesToShow.size < pendingCases.size) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun displayArchivedTests() {
        binding.archivedTestsList.removeAllViews()

        if (filteredTests.isEmpty()) {
            binding.archivedEmptyState.visibility = View.VISIBLE
            binding.archivedTestsList.visibility = View.GONE
            binding.loadMoreArchivedButton.visibility = View.GONE
            return
        } else {
            binding.archivedEmptyState.visibility = View.GONE
            binding.archivedTestsList.visibility = View.VISIBLE
        }

        val testsToShow = filteredTests.take(archivedPageSize * archivedCurrentPage)
        val inflater = LayoutInflater.from(this)

        testsToShow.forEach { test ->
            val testView = inflater.inflate(R.layout.item_archived_test, binding.archivedTestsList, false)

            val testIdText = testView.findViewById<TextView>(R.id.testIdText)
            val clientNameText = testView.findViewById<TextView>(R.id.clientNameText)
            val testTypeText = testView.findViewById<TextView>(R.id.testTypeText)
            val outcomeBadge = testView.findViewById<TextView>(R.id.outcomeBadge)
            val testDateText = testView.findViewById<TextView>(R.id.testDateText)
            val examinerText = testView.findViewById<TextView>(R.id.examinerText)
            val statusText = testView.findViewById<TextView>(R.id.statusText)
            val downloadBtn = testView.findViewById<MaterialButton>(R.id.downloadPdfBtn)

            testIdText.text = "#TEST-${test.testId}"
            clientNameText.text = test.examineeName
            testTypeText.text = test.testName ?: "Polygraph Test"

            outcomeBadge.text = test.testOutcome.uppercase()
            outcomeBadge.setBackgroundColor(
                when (test.testOutcome) {
                    "Pass", "No Deception Indicated" -> 0xFF4CAF50.toInt()
                    "Fail", "Deception Indicated" -> 0xFFF44336.toInt()
                    else -> 0xFFFFA726.toInt()
                }
            )

            testDateText.text = formatDate(test.testDate)
            examinerText.text = test.examinerName
            statusText.text = test.testStatus

            downloadBtn.setOnClickListener {
                generateAndDownloadPdf(test)
            }

            binding.archivedTestsList.addView(testView)
        }

        binding.loadMoreArchivedButton.visibility = if (testsToShow.size < filteredTests.size) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun generateAndDownloadPdf(test: Test) {
        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@AdminCasesActivity,
                    "Generating PDF report...",
                    Toast.LENGTH_SHORT
                ).show()

                // Call backend to generate PDF
                val result = DatabaseHelper.httpClient.request("pdf/test-report/${test.testId}", "POST")

                result.fold(
                    onSuccess = { json ->
                        val pdfUrl = json.getString("pdfUrl")
                        showPdfDownloadSuccess(pdfUrl, test)
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@AdminCasesActivity,
                            "Failed to generate PDF: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate PDF", e)
                Toast.makeText(
                    this@AdminCasesActivity,
                    "Failed to generate PDF: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showPdfDownloadSuccess(pdfUrl: String, test: Test) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("PDF Generated Successfully")
            .setMessage("The admin test report has been generated and stored securely in Azure Blob Storage. You can access it using the link below.")
            .setPositiveButton("Open PDF") { _, _ ->
                openPdfUrl(pdfUrl)
            }
            .setNeutralButton("Copy Link") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("PDF URL", pdfUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "PDF link copied to clipboard", Toast.LENGTH_SHORT).show()
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
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("PDF URL", pdfUrl)
            clipboard.setPrimaryClip(clip)
        }
    }

    private fun showAssignExaminerDialog(booking: Booking) {
        if (allEmployees.isEmpty()) {
            Toast.makeText(this, "No active employees available", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_assign_examiner, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.examinersRadioGroup)
        val actualDateInput = dialogView.findViewById<EditText>(R.id.actualDateInput)
        val actualTimeInput = dialogView.findViewById<EditText>(R.id.actualTimeInput)

        if (booking.actualDate != null) {
            actualDateInput.setText(booking.actualDate)
        } else {
            actualDateInput.setText(booking.preferredDate)
        }

        if (booking.actualTime != null) {
            actualTimeInput.setText(booking.actualTime)
        } else {
            actualTimeInput.setText(booking.preferredTime)
        }

        allEmployees.forEach { employee ->
            val radioButton = RadioButton(this).apply {
                text = "${employee.fullName} - ${employee.specialization ?: "General"}"
                id = employee.userId
                textSize = 16f
                setPadding(16, 16, 16, 16)
                if (booking.employeeId == employee.userId) {
                    isChecked = true
                }
            }
            radioGroup.addView(radioButton)
        }

        AlertDialog.Builder(this)
            .setTitle("Assign Examiner to ${booking.bookingReference}")
            .setView(dialogView)
            .setPositiveButton("Assign") { _, _ ->
                val selectedEmployeeId = radioGroup.checkedRadioButtonId

                if (selectedEmployeeId == -1) {
                    Toast.makeText(this, "Please select an examiner", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val actualDate = actualDateInput.text.toString().trim()
                val actualTime = actualTimeInput.text.toString().trim()

                if (actualDate.isEmpty() || actualTime.isEmpty()) {
                    Toast.makeText(this, "Please enter actual date and time", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Validate date format
                if (!isValidDate(actualDate)) {
                    Toast.makeText(this, "Invalid date format. Use YYYY-MM-DD", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Validate time format
                if (!isValidTime(actualTime)) {
                    Toast.makeText(this, "Invalid time format. Use HH:MM", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                assignExaminer(booking.bookingId, selectedEmployeeId, actualDate, actualTime)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun assignExaminer(bookingId: Int, employeeId: Int, actualDate: String, actualTime: String) {
        lifecycleScope.launch {
            val result = DatabaseHelper.assignEmployeeToBooking(
                bookingId = bookingId,
                employeeId = employeeId,
                actualDate = actualDate,
                actualTime = actualTime,
                updatedBy = adminUserId
            )

            result.onSuccess { updatedBooking ->
                Toast.makeText(
                    this@AdminCasesActivity,
                    "Examiner assigned successfully",
                    Toast.LENGTH_SHORT
                ).show()

                // Update the booking in the list
                allBookings = allBookings.map {
                    if (it.bookingId == updatedBooking.bookingId) updatedBooking else it
                }

                filterData(binding.searchInput.text.toString())
                updateStatistics()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminCasesActivity,
                    "Failed to assign examiner: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun isValidDate(date: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            format.isLenient = false
            format.parse(date)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidTime(time: String): Boolean {
        return try {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            format.isLenient = false
            format.parse(time)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getStatusColor(status: String): Int = when (status.uppercase()) {
        "PENDING" -> 0xFFFFA726.toInt()
        "CONFIRMED" -> 0xFF4CAF50.toInt()
        "COMPLETED" -> 0xFFD4AF37.toInt()
        "CANCELLED" -> 0xFFF44336.toInt()
        else -> 0xFF9E9E9E.toInt()
    }

    private fun createEmptyTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(32), dpToPx(16), dpToPx(32))
        }
    }

    private fun addDivider(parent: LinearLayout) {
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                marginStart = dpToPx(16)
                marginEnd = dpToPx(16)
            }
            setBackgroundColor(0xFF333333.toInt())
        }
        parent.addView(divider)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        loadData()
    }
}