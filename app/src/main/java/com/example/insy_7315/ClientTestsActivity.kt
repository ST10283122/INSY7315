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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.ClientTestsBinding
import com.example.insy_7315.models.Test
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ClientTestsActivity : AppCompatActivity() {
    private lateinit var binding: ClientTestsBinding
    private var clientId: Int = 0
    private var clientName: String = ""
    private var allTests = listOf<Test>()
    private var filteredTests = listOf<Test>()

    companion object {
        private const val TAG = "ClientTests"
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

        // Clear search when clear icon is clicked
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
                // Search by test type/name
                (test.testName?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        // Search by examiner name
                        (test.examinerName?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        // Search by test outcome
                        (test.testOutcome?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        // Search by test status
                        (test.testStatus?.lowercase(Locale.getDefault())?.contains(searchQuery) == true) ||
                        // Search by test ID
                        ("TEST-${test.testId}".lowercase(Locale.getDefault()).contains(searchQuery)) ||
                        // Search by date
                        (formatDate(test.testDate).lowercase(Locale.getDefault()).contains(searchQuery)) ||
                        // Search by location
                        (test.testLocation?.lowercase(Locale.getDefault())?.contains(searchQuery) == true)
            }
        }

        displayTests(filteredTests)
        updateStatistics(filteredTests)
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
                    filteredTests = allTests

                    updateStatistics(filteredTests)
                    displayTests(filteredTests)

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
            if (binding.searchInput.text?.isNotEmpty() == true) {
                // Show search empty state
                binding.emptyState.visibility = View.GONE
                binding.searchEmptyState.visibility = View.VISIBLE
                binding.historyList.visibility = View.GONE

                // Update search empty text
                val searchEmptyText = binding.searchEmptyState.findViewById<TextView>(R.id.searchEmptyText)
                searchEmptyText.text = "No tests found for \"${binding.searchInput.text}\""
            } else {
                // Show regular empty state
                binding.emptyState.visibility = View.VISIBLE
                binding.searchEmptyState.visibility = View.GONE
                binding.historyList.visibility = View.GONE
            }
        } else {
            binding.emptyState.visibility = View.GONE
            binding.searchEmptyState.visibility = View.GONE
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

                // Call backend to generate PDF
                val result = DatabaseHelper.httpClient.request("pdf/test-report/${test.testId}", "POST")

                result.fold(
                    onSuccess = { json ->
                        val pdfUrl = json.getString("pdfUrl")
                        showPdfDownloadSuccess(pdfUrl, test)
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@ClientTestsActivity,
                            "Failed to generate PDF: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )

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

    private fun showPdfDownloadSuccess(pdfUrl: String, test: Test) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("PDF Generated Successfully")
            .setMessage("Your test report has been generated and stored securely. You can access it using the link below.")
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