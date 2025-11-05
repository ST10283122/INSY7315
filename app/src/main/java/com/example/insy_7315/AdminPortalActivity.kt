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
        val user = sessionManager.getUser()

        binding.userNameText.text = user?.fullName ?: "Admin User"
    }

    private fun setupClickListeners() {
        binding.manageUsersCard.setOnClickListener {
            val intent = Intent(this, AdminUsersActivity::class.java)
            startActivity(intent)
        }

        binding.manageCasesCard.setOnClickListener {
            val intent = Intent(this, AdminCasesActivity::class.java)
            startActivity(intent)
        }

        binding.analyticsCard.setOnClickListener {
            val intent = Intent(this, AdminStatsActivity::class.java)
            startActivity(intent)
        }
    }
}