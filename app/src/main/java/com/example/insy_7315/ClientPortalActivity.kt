package com.example.insy_7315

import android.os.Bundle
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
    }

    private fun setupUI() {
        // Get logged-in user info
        val user = sessionManager.getUser()

        // Display user name
        binding.userNameText.text = user?.fullName ?: "Guest"
    }
}