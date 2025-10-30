package com.example.insy_7315

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.insy_7315.databinding.EmployeePortalBinding
import com.example.insy_7315.utils.SessionManager

class EmployeePortalActivity : AppCompatActivity() {
    private lateinit var binding: EmployeePortalBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = EmployeePortalBinding.inflate(layoutInflater)
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
        // Get logged-in user info
        val user = sessionManager.getUser()

        // Manage Appointments Card
        binding.manageAppointmentsCard.setOnClickListener {
            if (user != null) {
                val intent = Intent(this, EmployeeBookingsActivity::class.java)
                intent.putExtra("USER_ID", user.userId)
                intent.putExtra("USER_NAME", user.fullName)
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    "Session expired. Please login again.",
                    Toast.LENGTH_SHORT
                ).show()
                // Optionally navigate back to login
                // navigateToLogin()
            }
        }

        // Past Tests Card
        binding.pastTestsCard.setOnClickListener {
            // TODO: Navigate to Past Tests activity when implemented
            Toast.makeText(
                this,
                "Past Tests - Coming Soon",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI in case data changed
        setupUI()
    }
}