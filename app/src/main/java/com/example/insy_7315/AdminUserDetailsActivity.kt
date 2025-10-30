package com.example.insy_7315

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.AdminUserDetailsBinding
import com.example.insy_7315.models.User
import kotlinx.coroutines.launch

class AdminUserDetailsActivity : AppCompatActivity() {
    private lateinit var binding: AdminUserDetailsBinding
    private var currentUser: User? = null
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminUserDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getIntExtra("USER_ID", -1)
        if (userId == -1) {
            Toast.makeText(this, "Invalid user ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        loadUserDetails()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.updateDetailsButton.setOnClickListener {
            showUpdateDetailsDialog()
        }

        binding.resetPasswordButton.setOnClickListener {
            showResetPasswordDialog()
        }

        binding.suspendAccountButton.setOnClickListener {
            currentUser?.let { user ->
                val newStatus = if (user.accountStatus == "Active") "Suspended" else "Active"
                val action = if (newStatus == "Suspended") "suspend" else "reactivate"
                showConfirmationDialog(
                    "Confirm ${action.capitalize()}",
                    "Are you sure you want to $action ${user.fullName}'s account?",
                    { updateAccountStatus(newStatus) }
                )
            }
        }

        binding.deleteAccountButton.setOnClickListener {
            currentUser?.let { user ->
                showConfirmationDialog(
                    "Confirm Deletion",
                    "Are you sure you want to permanently delete ${user.fullName}'s account? This action cannot be undone.",
                    { deleteUser() }
                )
            }
        }
    }

    private fun loadUserDetails() {
        lifecycleScope.launch {
            val result = DatabaseHelper.getUserById(userId)
            result.onSuccess { user ->
                currentUser = user
                displayUserDetails(user)
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminUserDetailsActivity,
                    "Failed to load user: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun displayUserDetails(user: User) {
        binding.apply {
            userName.text = user.fullName
            userRole.text = user.userRole.uppercase()
            userId.text = "USR-${user.userId}"
            userEmail.text = user.email
            userPhone.text = user.phone ?: "No phone"
            userAddress.text = user.address ?: "No address provided"

            statusBadge.text = user.accountStatus.uppercase()
            statusBadge.setBackgroundColor(getStatusColor(user.accountStatus))

            // Update button text based on status
            suspendAccountButton.text = if (user.accountStatus == "Active") {
                "SUSPEND ACCOUNT"
            } else {
                "REACTIVATE ACCOUNT"
            }

            // Show/hide professional info based on role
            if (user.userRole == "Employee") {
                professionalLabel.visibility = View.VISIBLE
                professionalCard.visibility = View.VISIBLE

                // Update professional info card views
                val certificationText = professionalCard.findViewById<android.widget.TextView>(
                    com.example.insy_7315.R.id.certificationText
                )
                val licenseText = professionalCard.findViewById<android.widget.TextView>(
                    com.example.insy_7315.R.id.licenseText
                )
                val specializationText = professionalCard.findViewById<android.widget.TextView>(
                    com.example.insy_7315.R.id.specializationText
                )

                certificationText?.text = user.certification ?: "Not specified"
                licenseText?.text = user.licenseNumber ?: "Not specified"
                specializationText?.text = user.specialization ?: "Not specified"
            } else {
                professionalLabel.visibility = View.GONE
                professionalCard.visibility = View.GONE
            }
        }
    }

    private fun showUpdateDetailsDialog() {
        val user = currentUser ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_update_user, null)
        val fullNameInput = dialogView.findViewById<EditText>(R.id.fullNameInput)
        val emailInput = dialogView.findViewById<EditText>(R.id.emailInput)
        val phoneInput = dialogView.findViewById<EditText>(R.id.phoneInput)
        val addressInput = dialogView.findViewById<EditText>(R.id.addressInput)
        val certificationInput = dialogView.findViewById<EditText>(R.id.certificationInput)
        val licenseInput = dialogView.findViewById<EditText>(R.id.licenseInput)
        val specializationInput = dialogView.findViewById<EditText>(R.id.specializationInput)
        val professionalSection = dialogView.findViewById<LinearLayout>(R.id.professionalSection)

        // Pre-fill with current values
        fullNameInput.setText(user.fullName)
        emailInput.setText(user.email)
        phoneInput.setText(user.phone)
        addressInput.setText(user.address)

        // Show professional fields only for employees
        if (user.userRole == "Employee") {
            professionalSection.visibility = View.VISIBLE
            certificationInput.setText(user.certification)
            licenseInput.setText(user.licenseNumber)
            specializationInput.setText(user.specialization)
        } else {
            professionalSection.visibility = View.GONE
        }

        AlertDialog.Builder(this)
            .setTitle("Update User Details")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val fullName = fullNameInput.text.toString().trim()
                val email = emailInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                val address = addressInput.text.toString().trim()
                val certification = certificationInput.text.toString().trim()
                val license = licenseInput.text.toString().trim()
                val specialization = specializationInput.text.toString().trim()

                if (fullName.isEmpty() || email.isEmpty()) {
                    Toast.makeText(this, "Name and email are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                updateUserDetails(
                    fullName = fullName,
                    email = email,
                    phone = phone.ifEmpty { null },
                    address = address.ifEmpty { null },
                    certification = if (user.userRole == "Employee") certification.ifEmpty { null } else null,
                    licenseNumber = if (user.userRole == "Employee") license.ifEmpty { null } else null,
                    specialization = if (user.userRole == "Employee") specialization.ifEmpty { null } else null
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetPasswordDialog() {
        val user = currentUser ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_reset_password, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.passwordInput)
        val confirmPasswordInput = dialogView.findViewById<EditText>(R.id.confirmPasswordInput)

        AlertDialog.Builder(this)
            .setTitle("Reset Password for ${user.fullName}")
            .setView(dialogView)
            .setPositiveButton("Reset") { _, _ ->
                val password = passwordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                if (password.isEmpty()) {
                    Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (password.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (password != confirmPassword) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                resetPassword(password)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onConfirm() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun updateUserDetails(
        fullName: String,
        email: String,
        phone: String?,
        address: String?,
        certification: String?,
        licenseNumber: String?,
        specialization: String?
    ) {
        lifecycleScope.launch {
            val result = DatabaseHelper.updateUser(
                userId = userId,
                fullName = fullName,
                email = email,
                phone = phone,
                address = address,
                certification = certification,
                licenseNumber = licenseNumber,
                specialization = specialization
            )

            result.onSuccess { user ->
                currentUser = user
                displayUserDetails(user)
                Toast.makeText(
                    this@AdminUserDetailsActivity,
                    "User details updated successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminUserDetailsActivity,
                    "Failed to update user: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun resetPassword(newPassword: String) {
        lifecycleScope.launch {
            val result = DatabaseHelper.resetUserPassword(userId, newPassword)

            result.onSuccess { message ->
                Toast.makeText(
                    this@AdminUserDetailsActivity,
                    "Password reset successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminUserDetailsActivity,
                    "Failed to reset password: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateAccountStatus(newStatus: String) {
        lifecycleScope.launch {
            val result = DatabaseHelper.updateUser(
                userId = userId,
                accountStatus = newStatus
            )

            result.onSuccess { user ->
                currentUser = user
                displayUserDetails(user)
                val action = if (newStatus == "Suspended") "suspended" else "reactivated"
                Toast.makeText(
                    this@AdminUserDetailsActivity,
                    "Account $action successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminUserDetailsActivity,
                    "Failed to update status: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun deleteUser() {
        lifecycleScope.launch {
            val result = DatabaseHelper.deleteUser(userId)

            result.onSuccess { message ->
                Toast.makeText(
                    this@AdminUserDetailsActivity,
                    "User deleted successfully",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminUserDetailsActivity,
                    "Failed to delete user: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getStatusColor(status: String): Int = when (status.uppercase()) {
        "ACTIVE" -> 0xFF4CAF50.toInt()
        "INACTIVE" -> 0xFF9E9E9E.toInt()
        "SUSPENDED" -> 0xFFFFA726.toInt()
        else -> 0xFF9E9E9E.toInt()
    }
}