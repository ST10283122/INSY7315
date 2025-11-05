package com.example.insy_7315.utils

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordUtil {
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    // Hash password with salt
    fun hashPassword(password: String): String {
        val salt = generateSalt()
        val hash = pbkdf2(password, salt)
        return "$salt:$hash"
    }

    //(No date) Medium.com. Available at: https://markilott.medium.com/password-storage-basics-2aa9e1586f98 (Accessed: November 5, 2025).

    // Verify password against stored hash
    fun verifyPassword(password: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false

        val salt = parts[0]
        val hash = parts[1]
        val testHash = pbkdf2(password, salt)

        return hash == testHash
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt.toHex()
    }

    private fun pbkdf2(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.hexToBytes(), ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}