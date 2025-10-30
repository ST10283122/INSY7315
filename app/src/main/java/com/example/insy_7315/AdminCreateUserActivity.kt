package com.example.insy_7315

import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.AdminUsersCreateBinding
import kotlinx.coroutines.launch

class AdminCreateUserActivity : AppCompatActivity() {
    private lateinit var binding: AdminUsersCreateBinding

    // User role options
    private val userRoles = arrayOf("Employee", "Client", "Admin")
    private val accountStatuses = arrayOf("Active", "Inactive", "Pending")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminUsersCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDropdowns()
        setupClickListeners()
    }

    private fun setupDropdowns() {
        // Setup user role dropdown
        val roleAdapter = ArrayAdapter(this, R.layout.dropdown_menu_item, userRoles)
        binding.userRoleInput.setAdapter(roleAdapter)

        // Setup account status dropdown
        val statusAdapter = ArrayAdapter(this, R.layout.dropdown_menu_item, accountStatuses)
        binding.accountStatusInput.setAdapter(statusAdapter)

        // Set default values
        binding.userRoleInput.setText(userRoles[0], false) // Default to Employee
        binding.accountStatusInput.setText(accountStatuses[0], false) // Default to Active
    }

    private fun setupClickListeners() {
        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }

        // Cancel button
        binding.cancelButton.setOnClickListener {
            finish()
        }

        // Create user button
        binding.createUserButton.setOnClickListener {
            if (validateInputs()) {
                createEmployeeUser()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val name = binding.fullNameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val phone = binding.phoneInput.text.toString().trim()
        val userRole = binding.userRoleInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        val confirmPassword = binding.confirmPasswordInput.text.toString()
        val accountStatus = binding.accountStatusInput.text.toString().trim()

        // Validate full name
        if (name.isEmpty()) {
            binding.fullNameLayout.error = "Full name is required"
            binding.fullNameInput.requestFocus()
            return false
        }
        binding.fullNameLayout.error = null

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

        // Validate user role
        if (userRole.isEmpty() || !userRoles.contains(userRole)) {
            binding.userRoleLayout.error = "Please select a valid user role"
            binding.userRoleInput.requestFocus()
            return false
        }
        binding.userRoleLayout.error = null

        // Validate password
        if (password.isEmpty()) {
            binding.passwordLayout.error = "Password is required"
            binding.passwordInput.requestFocus()
            return false
        }
        if (password.length < 8) {
            binding.passwordLayout.error = "Password must be at least 8 characters"
            binding.passwordInput.requestFocus()
            return false
        }
        if (!isValidPassword(password)) {
            binding.passwordLayout.error = "Password must contain uppercase, lowercase, number, and special character"
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

        // Validate account status
        if (accountStatus.isEmpty() || !accountStatuses.contains(accountStatus)) {
            binding.accountStatusLayout.error = "Please select a valid account status"
            binding.accountStatusInput.requestFocus()
            return false
        }
        binding.accountStatusLayout.error = null

        return true
    }

    private fun isValidPassword(password: String): Boolean {
        val passwordRegex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{8,}\$".toRegex()
        return password.matches(passwordRegex)
    }

    private fun createEmployeeUser() {
        val name = binding.fullNameInput.text.toString().trim()
        val email = binding.emailInput.text.toString().trim()
        val phone = binding.phoneInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val address = binding.addressInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val userRole = binding.userRoleInput.text.toString().trim()
        val certification = binding.certificationInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val license = binding.licenseInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val specialization = binding.specializationInput.text.toString().trim().takeIf { it.isNotEmpty() }
        val password = binding.passwordInput.text.toString()

        // Disable button to prevent double submission
        binding.createUserButton.isEnabled = false
        binding.createUserButton.text = "CREATING USER..."

        lifecycleScope.launch {
            val result = DatabaseHelper.registerUser(
                fullName = name,
                email = email,
                phone = phone,
                passwordHash = password,
                userRole = userRole,
                termsAccepted = true,
                address = address,
                certification = certification,
                licenseNumber = license,
                specialization = specialization
            )

            result.onSuccess { user ->
                Toast.makeText(
                    this@AdminCreateUserActivity,
                    "User created successfully!",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminCreateUserActivity,
                    "Failed to create user: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                binding.createUserButton.isEnabled = true
                binding.createUserButton.text = "CREATE USER"
            }
        }
    }
}
