package com.example.insy_7315

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.RegistrationBinding
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: RegistrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = RegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Register button
        binding.registerButton.setOnClickListener {
            if (validateInputs()) {
                registerUser()
            }
        }

        binding.loginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.putExtra("FROM_REGISTRATION", true)
            startActivity(intent)
            finish()
        }
    }

    private fun validateInputs(): Boolean {
        val name = binding.nameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val phone = binding.phoneInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.confirmPasswordInput.text.toString()
        val termsAccepted = binding.termsCheckbox.isChecked

        // Validate full name
        if (name.isEmpty()) {
            binding.nameLayout.error = "Full name is required"
            binding.nameInput.requestFocus()
            return false
        }
        binding.nameLayout.error = null

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

        // Validate phone
        if (phone.isEmpty()) {
            binding.phoneLayout.error = "Phone number is required"
            binding.phoneInput.requestFocus()
            return false
        }
        if (phone.length < 10) {
            binding.phoneLayout.error = "Invalid phone number"
            binding.phoneInput.requestFocus()
            return false
        }
        binding.phoneLayout.error = null

        // Validate password
        if (password.isEmpty()) {
            binding.passwordLayout.error = "Password is required"
            binding.passwordInput.requestFocus()
            return false
        }
        if (password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            binding.passwordInput.requestFocus()
            return false
        }
        binding.passwordLayout.error = null

        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordLayout.error = "Please confirm password"
            binding.confirmPasswordInput.requestFocus()
            return false
        }
        if (password != confirmPassword) {
            binding.confirmPasswordLayout.error = "Passwords do not match"
            binding.confirmPasswordInput.requestFocus()
            return false
        }
        binding.confirmPasswordLayout.error = null

        // Validate terms checkbox
        if (!termsAccepted) {
            Toast.makeText(this, "Please accept the Terms and Conditions", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun registerUser() {
        val name = binding.nameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val phone = binding.phoneInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        binding.registerButton.isEnabled = false
        binding.registerButton.text = "CREATING ACCOUNT..."

        lifecycleScope.launch {
            // Register the user as a Client
            val result = DatabaseHelper.registerUser(
                fullName = name,
                email = email,
                phone = phone,
                passwordHash = password, // Send plain password, backend will hash
                userRole = "Client",
                termsAccepted = true
            )

            result.onSuccess { user ->
                Toast.makeText(
                    this@RegisterActivity,
                    "Account created successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@RegisterActivity,
                    "Registration failed: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.registerButton.isEnabled = true
                binding.registerButton.text = "CREATE ACCOUNT"
            }
        }
    }
}