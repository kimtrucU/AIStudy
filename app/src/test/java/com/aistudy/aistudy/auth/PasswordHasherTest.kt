package com.aistudy.aistudy.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun hashAndMatches_workWithSaltedHashes() {
        val hash1 = PasswordHasher.hash("secret123")
        val hash2 = PasswordHasher.hash("secret123")

        assertNotEquals(hash1, hash2)
        assertTrue(hash1.startsWith("sha256$"))
        assertTrue(PasswordHasher.matches("secret123", hash1))
        assertTrue(PasswordHasher.matches("secret123", hash2))
        assertFalse(PasswordHasher.matches("wrongpass", hash1))
    }

    @Test
    fun matches_supportsLegacyPlaintextAndRejectsInvalid() {
        assertTrue(PasswordHasher.matches("legacy123", "legacy123"))
        assertFalse(PasswordHasher.matches("legacy123", "other"))
        assertFalse(PasswordHasher.matches("secret", null))
        assertFalse(PasswordHasher.matches("secret", ""))
        assertFalse(PasswordHasher.matches("secret", "sha256\$not-base64\$bad"))
    }
}

