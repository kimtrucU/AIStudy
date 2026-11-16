package com.aistudy.aistudy.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun hashAndMatches_workWithSha256HexHashes() {
        val hash1 = PasswordHasher.hash("secret123")
        val hash2 = PasswordHasher.hash("secret123")

        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertTrue(PasswordHasher.matches("secret123", hash1))
        assertFalse(PasswordHasher.matches("wrongpass", hash1))
    }

    @Test
    fun matches_supportsLegacyPlaintextAndRejectsInvalid() {
        assertTrue(PasswordHasher.matches("legacy123", "legacy123"))
        assertFalse(PasswordHasher.matches("legacy123", "other"))
        assertFalse(PasswordHasher.matches("secret", null))
        assertFalse(PasswordHasher.matches("secret", ""))
    }
}
