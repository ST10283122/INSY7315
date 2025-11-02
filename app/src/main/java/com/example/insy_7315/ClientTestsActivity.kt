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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.ClientTestsBinding
import com.example.insy_7315.models.Test
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ClientTestsActivity : AppCompatActivity() {
    private lateinit var binding: ClientTestsBinding
    private var clientId: Int = 0
    private var clientName: String = ""
    private var allTests = listOf<Test>()

    companion object {
        private const val TAG = "ClientTests"
        private const val STORAGE_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientTestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        clientId = intent.getIntExtra("USER_ID", 0)
        clientName = intent.getStringExtra("USER_NAME") ?: "Client"

        Log.d(TAG, "onCreate - Client ID: $clientId")

        if (clientId == 0) {
            Toast.makeText(this, "Invalid client session", Toast.LENGTH_SHORT).show()
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
                Log.d(TAG, "Loading test history for client ID: $clientId")

                val result = DatabaseHelper.getTestsByClient(clientId)

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
                        this@ClientTestsActivity,
                        "Failed to load test history: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading test history", e)
                Toast.makeText(
                    this@ClientTestsActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateStatistics(tests: List<Test>) {
        val totalTests = tests.size
        val completedTests = tests.count { it.testStatus == "Released" }

        binding.totalTestsCount.text = totalTests.toString()
        binding.completedTestsCount.text = completedTests.toString()
    }

    private fun displayTests(tests: List<Test>) {
        binding.historyList.removeAllViews()

        if (tests.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.historyList.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.historyList.visibility = View.VISIBLE

            tests.forEach { test ->
                val itemView = inflateTestItem(test)
                binding.historyList.addView(itemView)
            }
        }
    }

    private fun inflateTestItem(test: Test): View {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_client_test, binding.historyList, false)

        // PDF Icon
        val pdfIcon = view.findViewById<ImageView>(R.id.pdfIcon)

        // Status Badge
        val statusBadge = view.findViewById<TextView>(R.id.statusBadge)
        statusBadge.text = test.testStatus.uppercase()
        statusBadge.setBackgroundColor(
            when (test.testStatus) {
                "Released" -> 0xFFD4AF37.toInt()
                "Completed" -> 0xFF4CAF50.toInt()
                "Reviewed" -> 0xFFFFA726.toInt()
                else -> 0xFF999999.toInt()
            }
        )

        // Test Type
        val testTypeText = view.findViewById<TextView>(R.id.testTypeText)
        testTypeText.text = test.testName ?: "Polygraph Test"

        // Test ID
        val testIdText = view.findViewById<TextView>(R.id.testIdText)
        testIdText.text = "Test ID: #TEST-${test.testId}"

        // Test Date
        val testDateText = view.findViewById<TextView>(R.id.testDateText)
        testDateText.text = formatDate(test.testDate)

        // Examiner
        val examinerText = view.findViewById<TextView>(R.id.examinerText)
        examinerText.text = test.examinerName

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
            val outputFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
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
                    this@ClientTestsActivity,
                    "Generating PDF report...",
                    Toast.LENGTH_SHORT
                ).show()

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
                val page = pdfDocument.startPage(pageInfo)

                drawPdfContent(page.canvas, test)

                pdfDocument.finishPage(page)

                // Save PDF
                val fileName = "Polygraph_Report_${test.testId}_${System.currentTimeMillis()}.pdf"
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )

                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }

                pdfDocument.close()

                Toast.makeText(
                    this@ClientTestsActivity,
                    "PDF downloaded: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()

                Log.d(TAG, "PDF saved to: ${file.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate PDF", e)
                Toast.makeText(
                    this@ClientTestsActivity,
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

        // Confidentiality Notice
        yPosition += 20f
        paint.textSize = 10f
        paint.color = 0xFF666666.toInt()
        paint.isFakeBoldText = true
        canvas.drawText("CONFIDENTIALITY NOTICE", 50f, yPosition, paint)
        yPosition += 20f

        paint.isFakeBoldText = false
        paint.textSize = 9f

        val confidentialityText = "This report contains confidential information and is intended solely for the person(s) named above. " +
                "Any unauthorized review, use, disclosure, or distribution is prohibited. If you have received this report in error, " +
                "please contact the examiner immediately."

        val confWords = confidentialityText.split(" ")
        line = ""

        for (word in confWords) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            val textWidth = paint.measureText(testLine)

            if (textWidth > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, 50f, yPosition, paint)
                yPosition += 15f
                line = word
            } else {
                line = testLine
            }
        }

        if (line.isNotEmpty()) {
            canvas.drawText(line, 50f, yPosition, paint)
            yPosition += 25f
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