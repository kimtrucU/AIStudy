package com.aistudy.aistudy.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Salted SHA-256 password hashing.
 * Stored format: "sha256$<saltBase64>$<hashBase64>".
 * Legacy plaintext passwords (stored before hashing was added) are still accepted by [matches].
 */
object PasswordHasher {

    private const val PREFIX = "sha256"
    private const val SALT_BYTES = 16

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return format(salt, digest(salt, password))
    }

    fun matches(password: String, stored: String?): Boolean {
        if (stored.isNullOrEmpty()) return false
        val parts = stored.split('$')
        if (parts.size != 3 || parts[0] != PREFIX) {
            // Legacy plaintext record
            return MessageDigest.isEqual(password.toByteArray(), stored.toByteArray())
        }
        return try {
            val salt = Base64.getDecoder().decode(parts[1])
            val expected = Base64.getDecoder().decode(parts[2])
            MessageDigest.isEqual(expected, digest(salt, password))
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun digest(salt: ByteArray, password: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(password.toByteArray(Charsets.UTF_8))
    }

    private fun format(salt: ByteArray, hash: ByteArray): String {
        val enc = Base64.getEncoder()
        return "$PREFIX$${enc.encodeToString(salt)}$${enc.encodeToString(hash)}"
    }
}
