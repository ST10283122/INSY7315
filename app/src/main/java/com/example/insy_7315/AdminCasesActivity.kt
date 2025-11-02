package com.example.insy_7315

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.AdminCasesBinding
import com.example.insy_7315.models.Booking
import com.example.insy_7315.models.Test
import com.example.insy_7315.models.User
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
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
    private val adminUserId = 1 // Replace with actual admin user ID from session

    companion object {
        private const val TAG = "AdminCases"
        private const val STORAGE_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminCasesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupSearch()
        checkStoragePermission()
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

    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            // Load bookings
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
        // Filter bookings
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

        // Filter tests
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

        // Show/hide load more button
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

        // Show/hide load more button
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

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
                val page = pdfDocument.startPage(pageInfo)

                drawPdfContent(page.canvas, test)

                pdfDocument.finishPage(page)

                // Save PDF
                val fileName = "Admin_Test_Report_${test.testId}_${System.currentTimeMillis()}.pdf"
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )

                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }

                pdfDocument.close()

                Toast.makeText(
                    this@AdminCasesActivity,
                    "PDF downloaded: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()

                Log.d(TAG, "PDF saved to: ${file.absolutePath}")

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

    private fun drawPdfContent(canvas: Canvas, test: Test) {
        val paint = Paint()
        var yPosition = 50f

        // Header
        paint.textSize = 24f
        paint.color = 0xFFD4AF37.toInt()
        paint.isFakeBoldText = true
        canvas.drawText("KEY POLYGRAPH AND INVESTIGATIONS", 50f, yPosition, paint)
        yPosition += 40f

        paint.textSize = 18f
        paint.isFakeBoldText = false
        canvas.drawText("POLYGRAPH EXAMINATION REPORT", 50f, yPosition, paint)
        yPosition += 30f

        paint.textSize = 14f
        paint.color = 0xFF9C27B0.toInt()
        canvas.drawText("ADMINISTRATIVE COPY", 50f, yPosition, paint)
        yPosition += 50f

        // Divider line
        paint.color = 0xFFD4AF37.toInt()
        canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
        yPosition += 30f

        // Report Details
        paint.textSize = 12f
        paint.color = 0xFF000000.toInt()

        canvas.drawText("Report ID: #TEST-${test.testId}", 50f, yPosition, paint)
        yPosition += 25f

        canvas.drawText("Examination Date: ${formatDate(test.testDate)}", 50f, yPosition, paint)
        yPosition += 25f

        canvas.drawText("Examination Time: ${test.testTime}", 50f, yPosition, paint)
        yPosition += 25f

        if (test.testLocation != null) {
            canvas.drawText("Location: ${test.testLocation}", 50f, yPosition, paint)
            yPosition += 25f
        }

        canvas.drawText("Test Type: ${test.testName ?: "Polygraph Examination"}", 50f, yPosition, paint)
        yPosition += 40f

        // Examinee Information
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("EXAMINEE INFORMATION", 50f, yPosition, paint)
        yPosition += 25f

        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${test.examineeName}", 50f, yPosition, paint)
        yPosition += 25f

        if (test.examineeDetails != null) {
            // Word wrap for examinee details
            val maxWidth = 495f
            val words = test.examineeDetails.split(" ")
            var line = ""

            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                val textWidth = paint.measureText(testLine)

                if (textWidth > maxWidth && line.isNotEmpty()) {
                    canvas.drawText(line, 50f, yPosition, paint)
                    yPosition += 20f
                    line = word
                } else {
                    line = testLine
                }
            }

            if (line.isNotEmpty()) {
                canvas.drawText(line, 50f, yPosition, paint)
                yPosition += 25f
            }
        }
        yPosition += 20f

        // Examiner Information
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("EXAMINER INFORMATION", 50f, yPosition, paint)
        yPosition += 25f

        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${test.examinerName}", 50f, yPosition, paint)
        yPosition += 25f

        canvas.drawText("Email: ${test.examinerEmail}", 50f, yPosition, paint)
        yPosition += 25f

        if (test.examinerPhone != null) {
            canvas.drawText("Phone: ${test.examinerPhone}", 50f, yPosition, paint)
            yPosition += 25f
        }
        yPosition += 20f

        // Test Outcome
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("EXAMINATION RESULT", 50f, yPosition, paint)
        yPosition += 25f

        paint.textSize = 16f
        paint.color = when (test.testOutcome) {
            "Pass", "No Deception Indicated" -> 0xFF4CAF50.toInt()
            "Fail", "Deception Indicated" -> 0xFFF44336.toInt()
            else -> 0xFFFFA726.toInt()
        }
        canvas.drawText(test.testOutcome.uppercase(), 50f, yPosition, paint)
        yPosition += 40f

        // Result Summary
        paint.color = 0xFF000000.toInt()
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("SUMMARY", 50f, yPosition, paint)
        yPosition += 25f

        paint.textSize = 11f
        paint.isFakeBoldText = false

        // Word wrap for result summary
        val maxWidth = 495f
        val words = test.resultSummary.split(" ")
        var line = ""

        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            val textWidth = paint.measureText(testLine)

            if (textWidth > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, 50f, yPosition, paint)
                yPosition += 20f
                line = word
            } else {
                line = testLine
            }
        }

        if (line.isNotEmpty()) {
            canvas.drawText(line, 50f, yPosition, paint)
            yPosition += 30f
        }

        // Internal Notes (Admin only)
        if (test.internalNotes != null) {
            yPosition += 20f
            paint.textSize = 14f
            paint.isFakeBoldText = true
            paint.color = 0xFF9C27B0.toInt()
            canvas.drawText("INTERNAL NOTES (CONFIDENTIAL)", 50f, yPosition, paint)
            yPosition += 25f

            paint.textSize = 10f
            paint.isFakeBoldText = false
            paint.color = 0xFF000000.toInt()

            val notesWords = test.internalNotes.split(" ")
            line = ""

            for (word in notesWords) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                val textWidth = paint.measureText(testLine)

                if (textWidth > maxWidth && line.isNotEmpty()) {
                    canvas.drawText(line, 50f, yPosition, paint)
                    yPosition += 18f
                    line = word
                } else {
                    line = testLine
                }
            }

            if (line.isNotEmpty()) {
                canvas.drawText(line, 50f, yPosition, paint)
                yPosition += 25f
            }
        }

        // Footer
        yPosition = 780f
        paint.textSize = 10f
        paint.color = 0xFF666666.toInt()
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER

        canvas.drawText("ADMINISTRATIVE REPORT - CONFIDENTIAL", 297.5f, yPosition, paint)
        yPosition += 15f
        canvas.drawText("This report contains internal notes and is for administrative use only", 297.5f, yPosition, paint)
        yPosition += 15f
        canvas.drawText("Generated on: ${SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(Date())}", 297.5f, yPosition, paint)

        paint.textAlign = Paint.Align.LEFT
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

        // Pre-fill with current values if reassigning
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

        // Add radio buttons for each employee
        allEmployees.forEach { employee ->
            val radioButton = RadioButton(this).apply {
                text = "${employee.fullName} - ${employee.specialization ?: "General"}"
                id = employee.userId
                textSize = 16f
                setPadding(16, 16, 16, 16)

                // Pre-select current examiner if reassigning
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Storage permission granted")
            } else {
                Toast.makeText(
                    this,
                    "Storage permission is required to download PDFs",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}