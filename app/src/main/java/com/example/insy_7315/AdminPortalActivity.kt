package com.example.insy_7315

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.insy_7315.databinding.AdminPortalBinding
import com.example.insy_7315.utils.SessionManager

class AdminPortalActivity : AppCompatActivity() {
    private lateinit var binding: AdminPortalBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminPortalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupUI()
        setupClickListeners()
    }

    private fun setupUI() {
        // Get logged-in user info
        val user = sessionManager.getUser()

        // Display user name
        binding.userNameText.text = user?.fullName ?: "Admin User"
    }

    private fun setupClickListeners() {
        // Manage Users Card - Navigate to AdminCreateUserActivity
        binding.manageUsersCard.setOnClickListener {
            val intent = Intent(this, AdminUsersActivity::class.java)
            startActivity(intent)
        }

        // Manage Cases Card - Navigate to AdminCasesActivity
        binding.manageCasesCard.setOnClickListener {
            val intent = Intent(this, AdminCasesActivity::class.java)
            startActivity(intent)
        }

        // Analytics Card - Add functionality later
        binding.analyticsCard.setOnClickListener {
            // TODO: Implement Analytics functionality
            // For now, show a placeholder message
            android.widget.Toast.makeText(
                this,
                "Analytics feature coming soon",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}