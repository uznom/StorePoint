package com.munzo.storepoint

import com.munzo.storepoint.util.SecurityHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SecurityHelperTest {

    @Test
    fun hashPassword_generatesValidPbkdf2Hash() {
        val password = "adminPassword123"
        val hash = SecurityHelper.hashPassword(password)
        
        assertTrue("Hash should start with pbkdf2 prefix", hash.startsWith("pbkdf2$120000$"))
        val parts = hash.split("$")
        assertEquals(4, parts.size)
        assertEquals(32, parts[2].length) // 16 bytes = 32 hex chars
        assertEquals(64, parts[3].length) // 32 bytes = 64 hex chars
    }

    @Test
    fun verifyPassword_matchesCorrectPassword() {
        val password = "secureStorePin987"
        val hash = SecurityHelper.hashPassword(password)

        val result = SecurityHelper.verifyPassword(password, hash)
        assertTrue("Correct password must match", result.isMatch)
        assertFalse("Already modern PBKDF2 hash should not need upgrade", result.needsUpgrade)
    }

    @Test
    fun verifyPassword_rejectsIncorrectPassword() {
        val password = "correctPassword"
        val hash = SecurityHelper.hashPassword(password)

        val result = SecurityHelper.verifyPassword("wrongPassword", hash)
        assertFalse("Incorrect password must be rejected", result.isMatch)
    }

    @Test
    fun verifyPassword_upgradesLegacy10000IterationHash() {
        val password = "legacyPinPassword"
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), ByteArray(16), 10000, 256)
        val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val computed = skf.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
        val realLegacyHash = "pbkdf2\$10000\$00000000000000000000000000000000\$$computed"
        val result = SecurityHelper.verifyPassword(password, realLegacyHash)
        assertTrue("Password should match legacy 10,000 hash", result.isMatch)
        assertTrue("Legacy 10,000 hash should flag needsUpgrade = true", result.needsUpgrade)
    }

    @Test
    fun verifyPassword_supportsLegacySha256WithUpgradeFlag() {
        val password = "legacyPassword123"
        val md = MessageDigest.getInstance("SHA-256")
        val legacyHash = md.digest(password.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        val result = SecurityHelper.verifyPassword(password, legacyHash)
        assertTrue("Legacy SHA-256 password must match", result.isMatch)
        assertTrue("Legacy hash must trigger needsUpgrade = true", result.needsUpgrade)
    }

    @Test
    fun verifyPassword_rejectsBlankOrEmptyHash() {
        val result = SecurityHelper.verifyPassword("anyPass", "")
        assertFalse("Blank hash must not match", result.isMatch)
    }

    @Test
    fun isValidPin_validatesExact6Digits() {
        assertTrue("Valid 6 digits should pass", SecurityHelper.isValidPin("123456"))
        assertTrue("Valid 6 zero digits should pass", SecurityHelper.isValidPin("000000"))
        assertTrue("Valid 6 digits 987654 should pass", SecurityHelper.isValidPin("987654"))

        assertFalse("5 digits must fail", SecurityHelper.isValidPin("12345"))
        assertFalse("7 digits must fail", SecurityHelper.isValidPin("1234567"))
        assertFalse("Alphanumeric must fail", SecurityHelper.isValidPin("12345a"))
        assertFalse("Letters must fail", SecurityHelper.isValidPin("abcdef"))
        assertFalse("Empty string must fail", SecurityHelper.isValidPin(""))
        assertFalse("Whitespace must fail", SecurityHelper.isValidPin("12345 "))
    }

    @Test
    fun hashPin_and_verifyPin_successAndFailure() {
        val pin = "654321"
        val hash = SecurityHelper.hashPin(pin)

        assertTrue("Hash should start with pbkdf2 prefix", hash.startsWith("pbkdf2$120000$"))
        val matchResult = SecurityHelper.verifyPin(pin, hash)
        assertTrue("Correct PIN must match", matchResult.isMatch)
        assertFalse("Modern PBKDF2 hash should not need upgrade", matchResult.needsUpgrade)

        val wrongResult = SecurityHelper.verifyPin("111111", hash)
        assertFalse("Wrong PIN must be rejected", wrongResult.isMatch)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected.toLong(), actual.toLong())
    }
}
