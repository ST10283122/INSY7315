package com.example.insy_7315.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.insy_7315.models.User
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SessionManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        // Setup mock behavior
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.clear()).thenReturn(mockEditor)

        sessionManager = SessionManager(mockContext)
    }

    // ============= SAVE USER TESTS =============

    @Test
    fun testSaveUser_savesAllFields() {
        // Given
        val user = User(
            userId = 123,
            fullName = "John Doe",
            email = "john@example.com",
            phone = "123-456-7890",
            userRole = "Client",
            termsAccepted = true,
            address = "123 Main St",
            certification = "Certified",
            licenseNumber = "LIC123",
            specialization = "Testing",
            accountStatus = "Active"
        )

        // When
        sessionManager.saveUser(user)

        // Then
        verify(mockEditor).putInt("userId", 123)
        verify(mockEditor).putString("fullName", "John Doe")
        verify(mockEditor).putString("email", "john@example.com")
        verify(mockEditor).putString("userRole", "Client")
        verify(mockEditor).apply()
    }

    @Test
    fun testSaveUser_withNullOptionalFields() {
        // Given
        val user = User(
            userId = 456,
            fullName = "Jane Smith",
            email = "jane@example.com",
            phone = null,
            userRole = "Employee",
            termsAccepted = null,
            address = null,
            certification = null,
            licenseNumber = null,
            specialization = null,
            accountStatus = "Active"
        )

        // When
        sessionManager.saveUser(user)

        // Then
        verify(mockEditor).putInt("userId", 456)
        verify(mockEditor).putString("fullName", "Jane Smith")
        verify(mockEditor).putString("email", "jane@example.com")
        verify(mockEditor).putString("userRole", "Employee")
        verify(mockEditor).apply()
    }

    // ============= GET USER TESTS =============

    @Test
    fun testGetUser_whenUserExists_returnsUser() {
        // Given
        `when`(mockSharedPreferences.getInt("userId", -1)).thenReturn(123)
        `when`(mockSharedPreferences.getString("fullName", "")).thenReturn("John Doe")
        `when`(mockSharedPreferences.getString("email", "")).thenReturn("john@example.com")
        `when`(mockSharedPreferences.getString("userRole", "")).thenReturn("Client")

        // When
        val result = sessionManager.getUser()

        // Then
        assertNotNull(result)
        assertEquals(123, result?.userId)
        assertEquals("John Doe", result?.fullName)
        assertEquals("john@example.com", result?.email)
        assertEquals("Client", result?.userRole)
        assertEquals("Active", result?.accountStatus)

        // Optional fields should be null as per implementation
        assertNull(result?.phone)
        assertNull(result?.termsAccepted)
        assertNull(result?.address)
        assertNull(result?.certification)
        assertNull(result?.licenseNumber)
        assertNull(result?.specialization)
    }

    @Test
    fun testGetUser_whenNoUserId_returnsNull() {
        // Given
        `when`(mockSharedPreferences.getInt("userId", -1)).thenReturn(-1)

        // When
        val result = sessionManager.getUser()

        // Then
        assertNull(result)
    }

    @Test
    fun testGetUser_withEmptyStrings_handlesGracefully() {
        // Given
        `when`(mockSharedPreferences.getInt("userId", -1)).thenReturn(789)
        `when`(mockSharedPreferences.getString("fullName", "")).thenReturn("")
        `when`(mockSharedPreferences.getString("email", "")).thenReturn("")
        `when`(mockSharedPreferences.getString("userRole", "")).thenReturn("")

        // When
        val result = sessionManager.getUser()

        // Then
        assertNotNull(result)
        assertEquals(789, result?.userId)
        assertEquals("", result?.fullName)
        assertEquals("", result?.email)
        assertEquals("", result?.userRole)
        assertEquals("Active", result?.accountStatus)
    }

    // ============= LOGIN STATUS TESTS =============

    @Test
    fun testIsLoggedIn_whenUserIdExists_returnsTrue() {
        // Given
        `when`(mockSharedPreferences.getInt("userId", -1)).thenReturn(123)

        // When
        val result = sessionManager.isLoggedIn()

        // Then
        assertTrue(result)
    }

    @Test
    fun testIsLoggedIn_whenNoUserId_returnsFalse() {
        // Given
        `when`(mockSharedPreferences.getInt("userId", -1)).thenReturn(-1)

        // When
        val result = sessionManager.isLoggedIn()

        // Then
        assertFalse(result)
    }

    // ============= USER ROLE TESTS =============

    @Test
    fun testGetUserRole_whenRoleExists_returnsRole() {
        // Given
        `when`(mockSharedPreferences.getString("userRole", null)).thenReturn("Admin")

        // When
        val result = sessionManager.getUserRole()

        // Then
        assertEquals("Admin", result)
    }

    @Test
    fun testGetUserRole_whenNoRole_returnsNull() {
        // Given
        `when`(mockSharedPreferences.getString("userRole", null)).thenReturn(null)

        // When
        val result = sessionManager.getUserRole()

        // Then
        assertNull(result)
    }

    // ============= LOGOUT TESTS =============

    @Test
    fun testLogout_clearsAllPreferences() {
        // When
        sessionManager.logout()

        // Then
        verify(mockEditor).clear()
        verify(mockEditor).apply()
    }

    // ============= INTEGRATION-LIKE TESTS =============

    @Test
    fun testSaveAndGetUser_consistency() {
        // Given
        val user = User(
            userId = 999,
            fullName = "Test User",
            email = "test@example.com",
            phone = null,
            userRole = "Tester",
            termsAccepted = null,
            address = null,
            certification = null,
            licenseNumber = null,
            specialization = null,
            accountStatus = "Active"
        )

        // Setup getters to return what was "saved"
        `when`(mockSharedPreferences.getInt("userId", -1)).thenReturn(999)
        `when`(mockSharedPreferences.getString("fullName", "")).thenReturn("Test User")
        `when`(mockSharedPreferences.getString("email", "")).thenReturn("test@example.com")
        `when`(mockSharedPreferences.getString("userRole", "")).thenReturn("Tester")

        // When - simulate save then get
        sessionManager.saveUser(user)
        val retrievedUser = sessionManager.getUser()

        // Then
        assertNotNull(retrievedUser)
        assertEquals(user.userId, retrievedUser?.userId)
        assertEquals(user.fullName, retrievedUser?.fullName)
        assertEquals(user.email, retrievedUser?.email)
        assertEquals(user.userRole, retrievedUser?.userRole)
    }

    @Test
    fun testLoginLogoutFlow() {
        // Test login state changes
        `when`(mockSharedPreferences.getInt("userId", -1)).thenReturn(123)
        assertTrue(sessionManager.isLoggedIn())

        `when`(mockSharedPreferences.getInt("userId", -1)).thenReturn(-1)
        assertFalse(sessionManager.isLoggedIn())
    }

    // ============= EDGE CASE TESTS =============

    @Test
    fun testGetUser_withExtremeValues() {
        // Given
        `when`(mockSharedPreferences.getInt("userId", -1)).thenReturn(Int.MAX_VALUE)
        `when`(mockSharedPreferences.getString("fullName", "")).thenReturn("A".repeat(1000)) // Long name
        `when`(mockSharedPreferences.getString("email", "")).thenReturn("test@example.com")
        `when`(mockSharedPreferences.getString("userRole", "")).thenReturn("VeryLongRoleName")

        // When
        val result = sessionManager.getUser()

        // Then
        assertNotNull(result)
        assertEquals(Int.MAX_VALUE, result?.userId)
        assertEquals("A".repeat(1000), result?.fullName)
        assertEquals("VeryLongRoleName", result?.userRole)
    }

    @Test
    fun testMultipleOperations() {
        // Test that multiple operations don't interfere
        val user1 = User(
            userId = 1,
            fullName = "User One",
            email = "one@example.com",
            phone = null,
            userRole = "Role1",
            termsAccepted = null,
            address = null,
            certification = null,
            licenseNumber = null,
            specialization = null,
            accountStatus = "Active"
        )

        val user2 = User(
            userId = 2,
            fullName = "User Two",
            email = "two@example.com",
            phone = null,
            userRole = "Role2",
            termsAccepted = null,
            address = null,
            certification = null,
            licenseNumber = null,
            specialization = null,
            accountStatus = "Active"
        )

        // Save first user
        sessionManager.saveUser(user1)

        // Verify first user was saved
        verify(mockEditor).putInt("userId", 1)
        verify(mockEditor).putString("fullName", "User One")
        verify(mockEditor).putString("email", "one@example.com")
        verify(mockEditor).putString("userRole", "Role1")

        // Save second user (should overwrite)
        sessionManager.saveUser(user2)

        // Verify second user was saved
        verify(mockEditor).putInt("userId", 2)
        verify(mockEditor).putString("fullName", "User Two")
        verify(mockEditor).putString("email", "two@example.com")
        verify(mockEditor).putString("userRole", "Role2")
    }
}