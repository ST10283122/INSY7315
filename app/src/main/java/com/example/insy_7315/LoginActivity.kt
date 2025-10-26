package com.example.insy_7315

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.LoginBinding
import com.example.insy_7315.utils.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: LoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Check if coming from registration page
        val fromRegistration = intent.getBooleanExtra("FROM_REGISTRATION", false)

        // Only auto-login if NOT coming from registration and user is logged in
        if (!fromRegistration && sessionManager.isLoggedIn()) {
            navigateBasedOnRole()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Sign In button
        binding.signInButton.setOnClickListener {
            if (validateInputs()) {
                loginUser()
            }
        }

        // Register link - navigate to RegisterActivity
        binding.registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        // Forgot Password link (placeholder for now)
        binding.forgotPassword.setOnClickListener {
            Toast.makeText(this, "Forgot password feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInputs(): Boolean {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        // Validate email
        if (email.isEmpty()) {
            binding.emailLayout.error = "Email is required"
            binding.emailInput.requestFocus()
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.error = "Invalid email format"
            binding.emailInput.requestFocus()
            return false
        }
        binding.emailLayout.error = null

        // Validate password
        if (password.isEmpty()) {
            binding.passwordLayout.error = "Password is required"
            binding.passwordInput.requestFocus()
            return false
        }
        binding.passwordLayout.error = null

        return true
    }

    private fun loginUser() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        // Disable button to prevent double submission
        binding.signInButton.isEnabled = false
        binding.signInButton.text = "SIGNING IN..."

        lifecycleScope.launch {
            val result = DatabaseHelper.loginUser(email, password)

            result.onSuccess { user ->
                // Save user session
                sessionManager.saveUser(user)

                Toast.makeText(
                    this@LoginActivity,
                    "Welcome back, ${user.fullName}!",
                    Toast.LENGTH_SHORT
                ).show()

                // Navigate based on user role
                navigateBasedOnRole()
            }.onFailure { error ->
                Toast.makeText(
                    this@LoginActivity,
                    "Login failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.signInButton.isEnabled = true
                binding.signInButton.text = "SIGN IN"
            }
        }
    }

    private fun navigateBasedOnRole() {
        when (sessionManager.getUserRole()) {
            "Client" -> navigateToClientPortal()
            "Employee" -> navigateToEmployeePortal()
            "Admin" -> navigateToAdminPortal()
            else -> navigateToMainActivity()
        }
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToClientPortal() {
        startActivity(Intent(this, ClientPortalActivity::class.java))
        finish()
    }

    private fun navigateToEmployeePortal() {
        startActivity(Intent(this, EmployeePortalActivity::class.java))
        finish()
    }

    private fun navigateToAdminPortal() {
        startActivity(Intent(this, AdminPortalActivity::class.java))
        finish()
    }
}