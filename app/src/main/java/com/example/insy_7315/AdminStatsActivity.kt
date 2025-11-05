package com.example.insy_7315

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.*

class AdminStatsActivity : AppCompatActivity() {

    private lateinit var backButton: ImageView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var contentContainer: LinearLayout

    // Financial stats
    private lateinit var totalRevenue: TextView
    private lateinit var totalRevenueSubtext: TextView
    private lateinit var pendingPayments: TextView
    private lateinit var pendingPaymentsSubtext: TextView
    private lateinit var avgSessionValue: TextView
    private lateinit var depositsCollected: TextView
    private lateinit var depositsSubtext: TextView

    // Operational stats
    private lateinit var totalBookings: TextView
    private lateinit var confirmedBookings: TextView
    private lateinit var totalUsers: TextView
    private lateinit var activeUsers: TextView
    private lateinit var totalTests: TextView
    private lateinit var completedTests: TextView
    private lateinit var testTypes: TextView
    private lateinit var activeTestTypes: TextView

    // Test type breakdown
    private lateinit var testTypeBreakdownContainer: LinearLayout

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_stats)

        try {
            initializeViews()
            setupClickListeners()
            loadStatistics()
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun initializeViews() {
        try {
            backButton = findViewById(R.id.backButton)
            loadingIndicator = findViewById(R.id.loadingIndicator)
            contentContainer = findViewById(R.id.contentContainer)

            // Financial
            totalRevenue = findViewById(R.id.totalRevenue)
            totalRevenueSubtext = findViewById(R.id.totalRevenueSubtext)
            pendingPayments = findViewById(R.id.pendingPayments)
            pendingPaymentsSubtext = findViewById(R.id.pendingPaymentsSubtext)
            avgSessionValue = findViewById(R.id.avgSessionValue)
            depositsCollected = findViewById(R.id.depositsCollected)
            depositsSubtext = findViewById(R.id.depositsSubtext)

            // Operational
            totalBookings = findViewById(R.id.totalBookings)
            confirmedBookings = findViewById(R.id.confirmedBookings)
            totalUsers = findViewById(R.id.totalUsers)
            activeUsers = findViewById(R.id.activeUsers)
            totalTests = findViewById(R.id.totalTests)
            completedTests = findViewById(R.id.completedTests)
            testTypes = findViewById(R.id.testTypes)
            activeTestTypes = findViewById(R.id.activeTestTypes)

            // Test type breakdown
            testTypeBreakdownContainer = findViewById(R.id.testTypeBreakdownContainer)
        } catch (e: Exception) {
            Toast.makeText(this, "Error finding views: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            throw e
        }
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun loadStatistics() {
        showLoading()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Fetch all data in parallel
                val bookingsResult = withContext(Dispatchers.IO) {
                    try {
                        DatabaseHelper.getAllBookings()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Result.failure(e)
                    }
                }

                val usersResult = withContext(Dispatchers.IO) {
                    try {
                        DatabaseHelper.getAllUsers()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Result.failure(e)
                    }
                }

                val testsResult = withContext(Dispatchers.IO) {
                    try {
                        DatabaseHelper.getAllTests()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Result.failure(e)
                    }
                }

                val testTypesResult = withContext(Dispatchers.IO) {
                    try {
                        DatabaseHelper.getTestTypes()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Result.failure(e)
                    }
                }

                // Process financial statistics
                calculateFinancialStats(bookingsResult, testsResult)

                // Process operational statistics
                calculateOperationalStats(
                    bookingsResult,
                    usersResult,
                    testsResult,
                    testTypesResult
                )

                // Generate test type breakdown
                generateTestTypeBreakdown(bookingsResult, testTypesResult)

                hideLoading()

            } catch (e: Exception) {
                e.printStackTrace()
                hideLoading()
                showError("Failed to load statistics: ${e.message}")
            }
        }
    }

    private suspend fun calculateFinancialStats(
        bookingsResult: Result<List<Booking>>,
        testsResult: Result<List<Test>>
    ) {
        withContext(Dispatchers.Main) {
            try {
                if (bookingsResult.isSuccess) {
                    val bookings = bookingsResult.getOrNull() ?: emptyList()

                    // Calculate total revenue from session fees
                    val totalRev = bookings.sumOf { it.sessionFee ?: 0.0 }
                    totalRevenue.text = currencyFormat.format(totalRev)
                    totalRevenueSubtext.text = "${bookings.size} total bookings"

                    // Calculate pending payments (bookings not completed or cancelled)
                    val pendingBookings = bookings.filter {
                        it.bookingStatus !in listOf("Completed", "Cancelled")
                    }
                    val pendingAmount = pendingBookings.sumOf { it.sessionFee ?: 0.0 }
                    pendingPayments.text = currencyFormat.format(pendingAmount)
                    pendingPaymentsSubtext.text = "${pendingBookings.size} outstanding invoices"

                    // Calculate average session value
                    val avgValue = if (bookings.isNotEmpty()) {
                        totalRev / bookings.size
                    } else {
                        0.0
                    }
                    avgSessionValue.text = currencyFormat.format(avgValue)

                    // Calculate deposits collected
                    val deposits = bookings.sumOf { it.depositRequired ?: 0.0 }
                    depositsCollected.text = currencyFormat.format(deposits)
                    val depositPercentage = if (totalRev > 0) {
                        ((deposits / totalRev) * 100).toInt()
                    } else {
                        0
                    }
                    depositsSubtext.text = "$depositPercentage% advance payments"
                } else {
                    showError("Failed to load booking data: ${bookingsResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Error calculating financial stats: ${e.message}")
            }
        }
    }

    private suspend fun calculateOperationalStats(
        bookingsResult: Result<List<Booking>>,
        usersResult: Result<List<User>>,
        testsResult: Result<List<Test>>,
        testTypesResult: Result<List<TestType>>
    ) {
        withContext(Dispatchers.Main) {
            try {
                // Bookings statistics
                if (bookingsResult.isSuccess) {
                    val bookings = bookingsResult.getOrNull() ?: emptyList()
                    totalBookings.text = "${bookings.size} bookings"

                    val confirmed = bookings.count { it.bookingStatus == "Confirmed" }
                    confirmedBookings.text = "$confirmed"
                }

                // Users statistics
                if (usersResult.isSuccess) {
                    val users = usersResult.getOrNull() ?: emptyList()
                    totalUsers.text = "${users.size} users"

                    val active = users.count { it.accountStatus == "Active" }
                    activeUsers.text = "$active"
                }

                // Tests statistics
                if (testsResult.isSuccess) {
                    val tests = testsResult.getOrNull() ?: emptyList()
                    totalTests.text = "${tests.size} tests"

                    val completed = tests.count { it.testStatus == "Completed" }
                    completedTests.text = "$completed"
                }

                // Test types statistics
                if (testTypesResult.isSuccess) {
                    val types = testTypesResult.getOrNull() ?: emptyList()
                    testTypes.text = "${types.size} test types"

                    val active = types.count { it.isActive }
                    activeTestTypes.text = "$active"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("Error calculating operational stats: ${e.message}")
            }
        }
    }

    private suspend fun generateTestTypeBreakdown(
        bookingsResult: Result<List<Booking>>,
        testTypesResult: Result<List<TestType>>
    ) {
        withContext(Dispatchers.Main) {
            try {
                testTypeBreakdownContainer.removeAllViews()

                if (bookingsResult.isSuccess && testTypesResult.isSuccess) {
                    val bookings = bookingsResult.getOrNull() ?: emptyList()
                    val types = testTypesResult.getOrNull() ?: emptyList()

                    if (bookings.isEmpty()) {
                        val noDataText = TextView(this@AdminStatsActivity).apply {
                            text = "No booking data available"
                            textSize = 14f
                            setTextColor(Color.parseColor("#999999"))
                            gravity = android.view.Gravity.CENTER
                            setPadding(0, dpToPx(20), 0, dpToPx(20))
                        }
                        testTypeBreakdownContainer.addView(noDataText)
                        return@withContext
                    }

                    val typeCountMap = mutableMapOf<Int, Int>()
                    bookings.forEach { booking ->
                        val count = typeCountMap.getOrDefault(booking.testTypeId, 0)
                        typeCountMap[booking.testTypeId] = count + 1
                    }

                    val typeNameMap = types.associate { it.testTypeId to it.testName }

                    val sortedTypes = typeCountMap.entries.sortedByDescending { it.value }

                    val colors = listOf(
                        "#D4AF37", "#4CAF50", "#2196F3", "#FFA726", "#E91E63", "#9C27B0"
                    )

                    sortedTypes.forEachIndexed { index, entry ->
                        val testTypeName = typeNameMap[entry.key] ?: "Unknown"
                        val count = entry.value
                        val percentage = ((count.toDouble() / bookings.size) * 100).toInt()
                        val color = colors[index % colors.size]

                        val itemView = createTestTypeBreakdownItem(
                            testTypeName,
                            count,
                            percentage,
                            color
                        )

                        testTypeBreakdownContainer.addView(itemView)

                        if (index < sortedTypes.size - 1) {
                            val divider = View(this@AdminStatsActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    resources.displayMetrics.density.toInt()
                                ).apply {
                                    setMargins(0, dpToPx(12), 0, dpToPx(12))
                                }
                                setBackgroundColor(Color.parseColor("#333333"))
                            }
                            testTypeBreakdownContainer.addView(divider)
                        }
                    }
                } else {
                    val errorText = TextView(this@AdminStatsActivity).apply {
                        text = "Failed to load test type data"
                        textSize = 14f
                        setTextColor(Color.parseColor("#999999"))
                        gravity = android.view.Gravity.CENTER
                        setPadding(0, dpToPx(20), 0, dpToPx(20))
                    }
                    testTypeBreakdownContainer.addView(errorText)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorText = TextView(this@AdminStatsActivity).apply {
                    text = "Error generating breakdown: ${e.message}"
                    textSize = 14f
                    setTextColor(Color.parseColor("#999999"))
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, dpToPx(20), 0, dpToPx(20))
                }
                testTypeBreakdownContainer.removeAllViews()
                testTypeBreakdownContainer.addView(errorText)
            }
        }
    }

    private fun createTestTypeBreakdownItem(
        name: String,
        count: Int,
        percentage: Int,
        color: String
    ): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))

            // Test type name
            addView(TextView(this@AdminStatsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                text = name
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            })

            // Count and percentage
            addView(TextView(this@AdminStatsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(12)
                }
                text = "$count ($percentage%)"
                textSize = 14f
                setTextColor(Color.parseColor(color))
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            })

            // Color bar
            addView(View(this@AdminStatsActivity).apply {
                val maxWidth = dpToPx(60)
                val actualWidth = (maxWidth * percentage / 100).coerceAtLeast(dpToPx(8))
                layoutParams = LinearLayout.LayoutParams(
                    actualWidth,
                    dpToPx(8)
                )
                setBackgroundColor(Color.parseColor(color))
            })
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showLoading() {
        loadingIndicator.visibility = View.VISIBLE
        contentContainer.visibility = View.GONE
    }

    private fun hideLoading() {
        loadingIndicator.visibility = View.GONE
        contentContainer.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}