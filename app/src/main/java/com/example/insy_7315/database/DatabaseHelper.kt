package com.example.insy_7315.database
import com.example.insy_7315.BuildConfig
import android.util.Log
import com.example.insy_7315.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import com.example.insy_7315.utils.PasswordUtil

object DatabaseHelper {
    private const val TAG = "DatabaseHelper"

    // Get connection to Azure SQL
    private suspend fun getConnection(): Connection = withContext(Dispatchers.IO) {
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
        DriverManager.getConnection(BuildConfig.DB_CONNECTION_STRING)
    }

    // Register new user
    suspend fun registerUser(
        fullName: String,
        email: String,
        phone: String?,
        passwordHash: String,
        userRole: String,
        termsAccepted: Boolean = false,
        address: String? = null,
        certification: String? = null,
        licenseNumber: String? = null,
        specialization: String? = null
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    INSERT INTO Users (FullName, Email, Phone, PasswordHash, UserRole, 
                                      TermsAccepted, Address, Certification, LicenseNumber, Specialization)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                    SELECT SCOPE_IDENTITY() AS UserID;
                """

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, fullName)
                    stmt.setString(2, email)
                    stmt.setString(3, phone)
                    stmt.setString(4, passwordHash)
                    stmt.setString(5, userRole)
                    stmt.setBoolean(6, termsAccepted)
                    stmt.setString(7, address)
                    stmt.setString(8, certification)
                    stmt.setString(9, licenseNumber)
                    stmt.setString(10, specialization)

                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val userId = rs.getInt("UserID")
                        Result.success(User(
                            userId = userId,
                            fullName = fullName,
                            email = email,
                            phone = phone,
                            userRole = userRole,
                            termsAccepted = termsAccepted,
                            address = address,
                            certification = certification,
                            licenseNumber = licenseNumber,
                            specialization = specialization,
                            accountStatus = "Active"
                        ))
                    } else {
                        Result.failure(Exception("Failed to get user ID"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            Result.failure(e)
        }
    }

    // Login user
    suspend fun loginUser(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT UserID, FullName, Email, Phone, PasswordHash, UserRole, 
                           TermsAccepted, Address, Certification, LicenseNumber, 
                           Specialization, AccountStatus, CreatedAt
                    FROM Users
                    WHERE Email = ? AND AccountStatus = 'Active'
                """

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, email)
                    val rs = stmt.executeQuery()

                    if (rs.next()) {
                        val storedHash = rs.getString("PasswordHash")

                        // Verify password (you'll implement this in PasswordUtil)
                        if (PasswordUtil.verifyPassword(password, storedHash)) {
                            Result.success(rs.toUser())
                        } else {
                            Result.failure(Exception("Invalid credentials"))
                        }
                    } else {
                        Result.failure(Exception("User not found"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            Result.failure(e)
        }
    }

    // Get user by ID
    suspend fun getUserById(userId: Int): Result<User> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT UserID, FullName, Email, Phone, PasswordHash, UserRole, 
                           TermsAccepted, Address, Certification, LicenseNumber, 
                           Specialization, AccountStatus, CreatedAt
                    FROM Users
                    WHERE UserID = ?
                """

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, userId)
                    val rs = stmt.executeQuery()

                    if (rs.next()) {
                        Result.success(rs.toUser())
                    } else {
                        Result.failure(Exception("User not found"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get user failed", e)
            Result.failure(e)
        }
    }

    // Check if email exists
    suspend fun emailExists(email: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = "SELECT COUNT(*) as count FROM Users WHERE Email = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, email)
                    val rs = stmt.executeQuery()
                    rs.next() && rs.getInt("count") > 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Email check failed", e)
            false
        }
    }

    // Update user profile
    suspend fun updateUser(
        userId: Int,
        fullName: String?,
        phone: String?,
        address: String?,
        certification: String?,
        licenseNumber: String?,
        specialization: String?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    UPDATE Users 
                    SET FullName = COALESCE(?, FullName),
                        Phone = COALESCE(?, Phone),
                        Address = COALESCE(?, Address),
                        Certification = COALESCE(?, Certification),
                        LicenseNumber = COALESCE(?, LicenseNumber),
                        Specialization = COALESCE(?, Specialization)
                    WHERE UserID = ?
                """

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, fullName)
                    stmt.setString(2, phone)
                    stmt.setString(3, address)
                    stmt.setString(4, certification)
                    stmt.setString(5, licenseNumber)
                    stmt.setString(6, specialization)
                    stmt.setInt(7, userId)

                    val rowsAffected = stmt.executeUpdate()
                    Result.success(rowsAffected > 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update user failed", e)
            Result.failure(e)
        }
    }

    // Change password
    suspend fun changePassword(userId: Int, newPasswordHash: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                getConnection().use { conn ->
                    val sql = "UPDATE Users SET PasswordHash = ? WHERE UserID = ?"
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, newPasswordHash)
                        stmt.setInt(2, userId)
                        val rowsAffected = stmt.executeUpdate()
                        Result.success(rowsAffected > 0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Change password failed", e)
                Result.failure(e)
            }
        }

    // Deactivate account
    suspend fun deactivateAccount(userId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = "UPDATE Users SET AccountStatus = 'Inactive' WHERE UserID = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, userId)
                    val rowsAffected = stmt.executeUpdate()
                    Result.success(rowsAffected > 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deactivate account failed", e)
            Result.failure(e)
        }
    }

    // Get all users by role (Admin function)
    suspend fun getUsersByRole(role: String): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT UserID, FullName, Email, Phone, PasswordHash, UserRole, 
                           TermsAccepted, Address, Certification, LicenseNumber, 
                           Specialization, AccountStatus, CreatedAt
                    FROM Users
                    WHERE UserRole = ?
                    ORDER BY CreatedAt DESC
                """

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, role)
                    val rs = stmt.executeQuery()
                    val users = mutableListOf<User>()

                    while (rs.next()) {
                        users.add(rs.toUser())
                    }

                    Result.success(users)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get users by role failed", e)
            Result.failure(e)
        }
    }

    // Extension function to convert ResultSet to User
    private fun ResultSet.toUser() = User(
        userId = getInt("UserID"),
        fullName = getString("FullName"),
        email = getString("Email"),
        phone = getString("Phone"),
        userRole = getString("UserRole"),
        termsAccepted = getBoolean("TermsAccepted"),
        address = getString("Address"),
        certification = getString("Certification"),
        licenseNumber = getString("LicenseNumber"),
        specialization = getString("Specialization"),
        accountStatus = getString("AccountStatus")
    )
}