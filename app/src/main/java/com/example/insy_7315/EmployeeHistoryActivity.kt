package com.example.insy_7315

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.EmployeeHistoryBinding
import com.example.insy_7315.models.Test
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class EmployeeHistoryActivity : AppCompatActivity() {
    private lateinit var binding: EmployeeHistoryBinding
    private var employeeId: Int = 0
    private var employeeName: String = ""
    private var allTests = listOf<Test>()

    companion object {
        private const val TAG = "EmployeeHistory"
        private const val STORAGE_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EmployeeHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        employeeId = intent.getIntExtra("USER_ID", 0)
        employeeName = intent.getStringExtra("USER_NAME") ?: "Employee"

        Log.d(TAG, "onCreate - Employee ID: $employeeId")

        if (employeeId == 0) {
            Toast.makeText(this, "Invalid employee session", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        checkStoragePermission()
        loadTestHistory()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }
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

    private fun loadTestHistory() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading test history for employee ID: $employeeId")

                val result = DatabaseHelper.getTestsByEmployee(employeeId)

                result.onSuccess { tests ->
                    Log.d(TAG, "Successfully loaded ${tests.size} tests")

                    // Filter only Released tests (don't show drafts/in-progress)
                    val releasedTests = tests.filter { it.testStatus == "Released" }
                    allTests = releasedTests.sortedByDescending { it.testDate }

                    updateStatistics(releasedTests)
                    displayTests(allTests)

                }.onFailure { error ->
                    Log.e(TAG, "Failed to load test history", error)
                    Toast.makeText(
                        this@EmployeeHistoryActivity,
                        "Failed to load test history: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading test history", e)
                Toast.makeText(
                    this@EmployeeHistoryActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateStatistics(tests: List<Test>) {
        val totalTests = tests.size

        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val testsThisMonth = tests.count { test ->
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val testDate = dateFormat.parse(test.testDate)
                val testCalendar = Calendar.getInstance().apply { time = testDate ?: Date() }
                testCalendar.get(Calendar.MONTH) == currentMonth &&
                        testCalendar.get(Calendar.YEAR) == currentYear
            } catch (e: Exception) {
                false
            }
        }

        val testsThisYear = tests.count { test ->
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val testDate = dateFormat.parse(test.testDate)
                val testCalendar = Calendar.getInstance().apply { time = testDate ?: Date() }
                testCalendar.get(Calendar.YEAR) == currentYear
            } catch (e: Exception) {
                false
            }
        }

        binding.totalTestsCount.text = totalTests.toString()
        binding.thisMonthCount.text = testsThisMonth.toString()
        binding.thisYearCount.text = testsThisYear.toString()
    }

    private fun displayTests(tests: List<Test>) {
        binding.resultsList.removeAllViews()

        if (tests.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.resultsList.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.resultsList.visibility = View.VISIBLE

            tests.forEach { test ->
                val itemView = inflateTestItem(test)
                binding.resultsList.addView(itemView)
            }
        }
    }

    private fun inflateTestItem(test: Test): View {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_test_result, binding.resultsList, false)

        // Test ID
        val testIdText = view.findViewById<TextView>(R.id.testIdText)
        testIdText.text = "#TEST-${test.testId}"

        // Client Name
        val clientNameText = view.findViewById<TextView>(R.id.clientNameText)
        clientNameText.text = test.examineeName

        // Test Type
        val testTypeText = view.findViewById<TextView>(R.id.testTypeText)
        testTypeText.text = test.testName ?: "Polygraph Test"

        // Outcome Badge
        val outcomeBadge = view.findViewById<TextView>(R.id.outcomeBadge)
        outcomeBadge.text = test.testOutcome.uppercase()
        outcomeBadge.setBackgroundColor(
            when (test.testOutcome) {
                "Pass", "No Deception Indicated" -> 0xFF4CAF50.toInt()
                "Fail", "Deception Indicated" -> 0xFFF44336.toInt()
                else -> 0xFFFFA726.toInt() // Inconclusive
            }
        )

        // Test Date
        val testDateText = view.findViewById<TextView>(R.id.testDateText)
        testDateText.text = formatDate(test.testDate)

        // Location
        val locationText = view.findViewById<TextView>(R.id.locationText)
        locationText.text = test.testLocation ?: "N/A"

        // Status
        val statusText = view.findViewById<TextView>(R.id.statusText)
        statusText.text = test.testStatus

        // Download Button
        val downloadBtn = view.findViewById<MaterialButton>(R.id.downloadPdfBtn)
        downloadBtn.setOnClickListener {
            generateAndDownloadPdf(test)
        }

        return view
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }

    private fun generateAndDownloadPdf(test: Test) {
        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@EmployeeHistoryActivity,
                    "Generating PDF report...",
                    Toast.LENGTH_SHORT
                ).show()

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
                val page = pdfDocument.startPage(pageInfo)

                drawPdfContent(page.canvas, test)

                pdfDocument.finishPage(page)

                // Save PDF
                val fileName = "Test_Report_${test.testId}_${System.currentTimeMillis()}.pdf"
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )

                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }

                pdfDocument.close()

                Toast.makeText(
                    this@EmployeeHistoryActivity,
                    "PDF downloaded: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()

                Log.d(TAG, "PDF saved to: ${file.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate PDF", e)
                Toast.makeText(
                    this@EmployeeHistoryActivity,
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
        yPosition += 50f

        // Divider line
        paint.color = 0xFFD4AF37.toInt()
        canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
        yPosition += 30f

        // Report Details
        paint.textSize = 12f
        paint.color = 0xFF000000.toInt()

        canvas.drawText("Test ID: #TEST-${test.testId}", 50f, yPosition, paint)
        yPosition += 25f

        canvas.drawText("Test Date: ${formatDate(test.testDate)}", 50f, yPosition, paint)
        yPosition += 25f

        canvas.drawText("Test Time: ${test.testTime}", 50f, yPosition, paint)
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
            canvas.drawText("Details: ${test.examineeDetails}", 50f, yPosition, paint)
            yPosition += 25f
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
        canvas.drawText("TEST OUTCOME", 50f, yPosition, paint)
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
        canvas.drawText("RESULT SUMMARY", 50f, yPosition, paint)
        yPosition += 25f

        paint.textSize = 11f
        paint.isFakeBoldText = false

        // Word wrap for result summary
        val maxWidth = 495f // 545 - 50
        val words = test.resultSummary.split(" ")
        var line = ""

        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            val textWidth = paint.measureText(testLine)

            if (textWidth > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, 50f, yPosition, paint)
                yPosition += 20f
                line = word

                // Check if we need a new page
                if (yPosition > 780f) {
                    // For simplicity, we'll just continue on same page
                    // In production, you'd create a new page here
                }
            } else {
                line = testLine
            }
        }

        if (line.isNotEmpty()) {
            canvas.drawText(line, 50f, yPosition, paint)
            yPosition += 30f
        }

        // Footer
        yPosition = 780f
        paint.textSize = 10f
        paint.color = 0xFF666666.toInt()
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.CENTER

        canvas.drawText("This is an official report from Key Polygraph and Investigations", 297.5f, yPosition, paint)
        yPosition += 15f
        canvas.drawText("For inquiries, please contact the examiner using the information above", 297.5f, yPosition, paint)
        yPosition += 15f
        canvas.drawText("Report generated on: ${SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(Date())}", 297.5f, yPosition, paint)

        paint.textAlign = Paint.Align.LEFT
    }

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
        loadTestHistory()
    }
}