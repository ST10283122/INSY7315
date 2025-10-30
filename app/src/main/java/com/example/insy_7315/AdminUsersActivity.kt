package com.example.insy_7315

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.insy_7315.database.DatabaseHelper
import com.example.insy_7315.databinding.AdminUsersBinding
import com.example.insy_7315.models.User
import kotlinx.coroutines.launch

class AdminUsersActivity : AppCompatActivity() {
    private lateinit var binding: AdminUsersBinding
    private var allUsers = listOf<User>()
    private var filteredEmployees = listOf<User>()
    private var filteredClients = listOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AdminUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupSearch()
        loadUsers()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }
        binding.addUserButton.setOnClickListener {
            startActivity(Intent(this, AdminCreateUserActivity::class.java))
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterUsers(s.toString())
            }
        })
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            val result = DatabaseHelper.getAllUsers()
            result.onSuccess { users ->
                allUsers = users
                filterUsers("")
                updateStatistics()
            }.onFailure { error ->
                Toast.makeText(
                    this@AdminUsersActivity,
                    "Failed to load users: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                displayEmptyState()
            }
        }
    }

    private fun filterUsers(query: String) {
        val employees = allUsers.filter { it.userRole == "Employee" }
        val clients = allUsers.filter { it.userRole == "Client" }

        filteredEmployees = if (query.isEmpty()) {
            employees
        } else {
            employees.filter { user ->
                user.fullName.contains(query, ignoreCase = true) ||
                        user.email.contains(query, ignoreCase = true) ||
                        user.phone?.contains(query, ignoreCase = true) == true
            }
        }

        filteredClients = if (query.isEmpty()) {
            clients
        } else {
            clients.filter { user ->
                user.fullName.contains(query, ignoreCase = true) ||
                        user.email.contains(query, ignoreCase = true) ||
                        user.phone?.contains(query, ignoreCase = true) == true
            }
        }

        displayUsers()
    }

    private fun updateStatistics() {
        binding.employeeCount.text = allUsers.count { it.userRole == "Employee" }.toString()
        binding.clientCount.text = allUsers.count { it.userRole == "Client" }.toString()
    }

    private fun displayUsers() {
        displayEmployees()
        displayClients()
    }

    private fun displayEmployees() {
        binding.employeesList.removeAllViews()
        if (filteredEmployees.isEmpty()) {
            val emptyView = createEmptyTextView("No employees found")
            binding.employeesList.addView(emptyView)
            return
        }

        val inflater = LayoutInflater.from(this)
        filteredEmployees.forEachIndexed { index, user ->
            val userView = inflater.inflate(R.layout.item_user_card, binding.employeesList, false)

            // Correct reference from inflated item view
            val userName = userView.findViewById<TextView>(R.id.userName)
            val userEmail = userView.findViewById<TextView>(R.id.userEmail)
            val userPhone = userView.findViewById<TextView>(R.id.userPhone)
            val userStatusBadge = userView.findViewById<TextView>(R.id.userStatusBadge)

            userName.text = user.fullName
            userEmail.text = user.email
            userPhone.text = user.phone ?: "No phone"

            userStatusBadge.text = user.accountStatus.uppercase()
            userStatusBadge.setBackgroundColor(getStatusColor(user.accountStatus))

            userView.setOnClickListener {
                val intent = Intent(this@AdminUsersActivity, AdminUserDetailsActivity::class.java)
                intent.putExtra("USER_ID", user.userId)
                startActivity(intent)
            }

            binding.employeesList.addView(userView)
            if (index < filteredEmployees.size - 1) addDivider(binding.employeesList)
        }
    }

    private fun displayClients() {
        binding.clientsList.removeAllViews()
        if (filteredClients.isEmpty()) {
            val emptyView = createEmptyTextView("No clients found")
            binding.clientsList.addView(emptyView)
            return
        }

        val inflater = LayoutInflater.from(this)
        filteredClients.forEachIndexed { index, user ->
            val userView = inflater.inflate(R.layout.item_user_card, binding.clientsList, false)

            val userName = userView.findViewById<TextView>(R.id.userName)
            val userEmail = userView.findViewById<TextView>(R.id.userEmail)
            val userPhone = userView.findViewById<TextView>(R.id.userPhone)
            val userStatusBadge = userView.findViewById<TextView>(R.id.userStatusBadge)

            userName.text = user.fullName
            userEmail.text = user.email
            userPhone.text = user.phone ?: "No phone"

            userStatusBadge.text = user.accountStatus.uppercase()
            userStatusBadge.setBackgroundColor(getStatusColor(user.accountStatus))

            userView.setOnClickListener {
                val intent = Intent(this@AdminUsersActivity, AdminUserDetailsActivity::class.java)
                intent.putExtra("USER_ID", user.userId)
                startActivity(intent)
            }

            binding.clientsList.addView(userView)
            if (index < filteredClients.size - 1) addDivider(binding.clientsList)
        }
    }

    private fun displayEmptyState() {
        binding.employeesList.removeAllViews()
        binding.clientsList.removeAllViews()
        val emptyView = createEmptyTextView("Failed to load users")
        binding.employeesList.addView(emptyView)
    }

    private fun createEmptyTextView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            gravity = Gravity.CENTER
            setPadding(dpToPx(16), dpToPx(32), dpToPx(16), dpToPx(32))
        }
    }

    private fun addDivider(parent: LinearLayout) {
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                marginStart = dpToPx(16)
                marginEnd = dpToPx(16)
            }
            setBackgroundColor(0xFF333333.toInt())
        }
        parent.addView(divider)
    }

    private fun getStatusColor(status: String): Int = when (status.uppercase()) {
        "ACTIVE" -> 0xFF4CAF50.toInt()
        "INACTIVE" -> 0xFF9E9E9E.toInt()
        "SUSPENDED" -> 0xFFFFA726.toInt()
        else -> 0xFF9E9E9E.toInt()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        loadUsers()
    }
}