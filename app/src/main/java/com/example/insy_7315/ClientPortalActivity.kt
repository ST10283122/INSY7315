package com.example.insy_7315

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.insy_7315.databinding.ClientPortalBinding
import com.example.insy_7315.utils.SessionManager

class ClientPortalActivity : AppCompatActivity() {
    private lateinit var binding: ClientPortalBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientPortalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        // Get logged-in user info
        val user = sessionManager.getUser()

        // Display user name
        binding.userNameText.text = user?.fullName ?: "Guest"
    }

    private fun setupClickListeners() {
        // Book Session - Navigate to ClientBookingsActivity
        binding.bookSessionCard.setOnClickListener {
            startActivity(Intent(this, ClientBookingsActivity::class.java))
        }

        // In ClientDashboardActivity or ClientPortalActivity
        binding.testHistoryCard.setOnClickListener {
            val user = sessionManager.getUser()
            if (user != null) {
                val intent = Intent(this, ClientTestsActivity::class.java).apply {
                    putExtra("USER_ID", user.userId)
                    putExtra("USER_NAME", user.fullName)
                }
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    "Session expired. Please login again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.paymentHistoryCard.setOnClickListener {
            startActivity(Intent(this, ClientPaymentsActivity::class.java))
        }
    }
}