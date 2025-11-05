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
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.EmployeeHistoryBinding
import com.example.insy_7315.models.Test
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EmployeeHistoryActivity : AppCompatActivity() {
    private lateinit var binding: EmployeeHistoryBinding
    private var employeeId: Int = 0
    private var employeeName: String = ""
    private var allTests = listOf<Test>()
    private var filteredTests = listOf<Test>()

    companion object {
        private const val TAG = "EmployeeHistory"
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
        setupSearchListener()
        loadTestHistory()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupSearchListener() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTests(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.searchLayout.setEndIconOnClickListener {
            binding.searchInput.text?.clear()
            filterTests("")
        }
    }

    private fun filterTests(query: String) {
        val searchQuery = query.trim().lowercase(Locale.getDefault())

        if (searchQuery.isEmpty()) {
            filteredTests = allTests
        } else {
            filteredTests = allTests.filter { test ->
                // Search by client name
                (test.examineeName?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        (test.testName?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        (test.testOutcome?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        (test.testStatus?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        ("TEST-${test.testId}".lowercase(Locale.getDefault()).contains(searchQuery)) ||
                        (formatDate(test.testDate).lowercase(Locale.getDefault()).contains(searchQuery)) ||
                        (test.testLocation?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        (test.examinerName?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        (test.examinerEmail?.lowercase(Locale.getDefault())?.contains(searchQuery) == true)
            }
        }

        displayTests(filteredTests)
        updateStatistics(filteredTests)
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
                    filteredTests = allTests

                    updateStatistics(filteredTests)
                    displayTests(filteredTests)

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
            if (binding.searchInput.text?.isNotEmpty() == true) {
                binding.emptyState.visibility = View.GONE
                binding.searchEmptyState.visibility = View.VISIBLE
                binding.resultsList.visibility = View.GONE
                val searchEmptyText = binding.searchEmptyState.findViewById<TextView>(R.id.searchEmptyText)
                searchEmptyText.text = "No tests found for \"${binding.searchInput.text}\""
            } else {
                binding.emptyState.visibility = View.VISIBLE
                binding.searchEmptyState.visibility = View.GONE
                binding.resultsList.visibility = View.GONE
            }
        } else {
            binding.emptyState.visibility = View.GONE
            binding.searchEmptyState.visibility = View.GONE
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
        val testIdText = view.findViewById<TextView>(R.id.testIdText)
        testIdText.text = "#TEST-${test.testId}"
        val clientNameText = view.findViewById<TextView>(R.id.clientNameText)
        clientNameText.text = test.examineeName
        val testTypeText = view.findViewById<TextView>(R.id.testTypeText)
        testTypeText.text = test.testName ?: "Polygraph Test"
        val outcomeBadge = view.findViewById<TextView>(R.id.outcomeBadge)
        outcomeBadge.text = test.testOutcome.uppercase()
        outcomeBadge.setBackgroundColor(
            when (test.testOutcome) {
                "Pass", "No Deception Indicated" -> 0xFF4CAF50.toInt()
                "Fail", "Deception Indicated" -> 0xFFF44336.toInt()
                else -> 0xFFFFA726.toInt() // Inconclusive
            }
        )

        val testDateText = view.findViewById<TextView>(R.id.testDateText)
        testDateText.text = formatDate(test.testDate)

        val locationText = view.findViewById<TextView>(R.id.locationText)
        locationText.text = test.testLocation ?: "N/A"

        val statusText = view.findViewById<TextView>(R.id.statusText)
        statusText.text = test.testStatus

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

                // Call backend to generate PDF
                val result = DatabaseHelper.httpClient.request("pdf/test-report/${test.testId}", "POST")

                result.fold(
                    onSuccess = { json ->
                        val pdfUrl = json.getString("pdfUrl")
                        showPdfDownloadSuccess(pdfUrl, test)
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@EmployeeHistoryActivity,
                            "Failed to generate PDF: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )

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

    //Hammoush, A. (2023) Kotlin: Generate PDF file, Medium. Available at: https://medium.com/@ahmad.hamoush.785/kotlin-generate-pdf-file-55cbfea73c4c (Accessed: November 5, 2025).

    private fun showPdfDownloadSuccess(pdfUrl: String, test: Test) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("PDF Generated Successfully")
            .setMessage("The test report has been generated and stored securely. You can access it using the link below.")
            .setPositiveButton("Open PDF") { _, _ ->
                openPdfUrl(pdfUrl)
            }
            .setNeutralButton("Copy Link") { _, _ ->
                // Copy URL to clipboard
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
            // Fallback: copy to clipboard
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("PDF URL", pdfUrl)
            clipboard.setPrimaryClip(clip)
        }
    }

    override fun onResume() {
        super.onResume()
        loadTestHistory()
    }
}