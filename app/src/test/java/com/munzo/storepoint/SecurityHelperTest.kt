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
    fun isValidPin_validatesExact4Digits() {
        assertTrue("Valid 4 digits should pass", SecurityHelper.isValidPin("1234"))
        assertTrue("Valid 4 digits 9753 should pass", SecurityHelper.isValidPin("9753"))
        assertTrue("Valid 4 digits 0482 should pass", SecurityHelper.isValidPin("0482"))

        assertFalse("3 digits must fail", SecurityHelper.isValidPin("123"))
        assertFalse("5 digits must fail", SecurityHelper.isValidPin("12345"))
        assertFalse("6 digits must fail strict validation", SecurityHelper.isValidPin("123456"))
        assertFalse("Alphanumeric must fail", SecurityHelper.isValidPin("12a4"))
        assertFalse("Letters must fail", SecurityHelper.isValidPin("abcd"))
        assertFalse("Empty string must fail", SecurityHelper.isValidPin(""))
        assertFalse("Whitespace must fail", SecurityHelper.isValidPin("12 4"))
    }

    @Test
    fun isValidPinLenient_strictFourDigitsOnly() {
        assertTrue("4 digits accepted", SecurityHelper.isValidPinLenient("1234"))
        assertFalse("6 digits rejected", SecurityHelper.isValidPinLenient("123456"))
        assertFalse("5 digits rejected", SecurityHelper.isValidPinLenient("12345"))
        assertFalse("7 digits rejected", SecurityHelper.isValidPinLenient("1234567"))
        assertFalse("letters rejected", SecurityHelper.isValidPinLenient("abcdef"))
    }

    @Test
    fun isWeakPin_rejectsTriviallyGuessablePins() {
        // Repeats
        assertTrue("0000 rejected", SecurityHelper.isWeakPin("0000"))
        assertTrue("1111 rejected", SecurityHelper.isWeakPin("1111"))
        assertTrue("9999 rejected", SecurityHelper.isWeakPin("9999"))
        // Runs
        assertTrue("1234 rejected", SecurityHelper.isWeakPin("1234"))
        assertTrue("2345 rejected", SecurityHelper.isWeakPin("2345"))
        assertTrue("4321 rejected", SecurityHelper.isWeakPin("4321"))
        assertTrue("0123 rejected", SecurityHelper.isWeakPin("0123"))
        assertTrue("3456 rejected", SecurityHelper.isWeakPin("3456"))
        // Patterns
        assertTrue("2580 rejected", SecurityHelper.isWeakPin("2580"))
        assertTrue("0852 rejected", SecurityHelper.isWeakPin("0852"))
        assertTrue("1212 rejected", SecurityHelper.isWeakPin("1212"))
        assertTrue("1010 rejected", SecurityHelper.isWeakPin("1010"))
        assertTrue("1000 rejected", SecurityHelper.isWeakPin("1000"))
    }

    @Test
    fun isWeakPin_acceptsReasonablePins() {
        assertFalse("5371 is fine", SecurityHelper.isWeakPin("5371"))
        assertFalse("8024 is fine", SecurityHelper.isWeakPin("8024"))
        assertFalse("4690 is fine", SecurityHelper.isWeakPin("4690"))
        assertFalse("7315 is fine", SecurityHelper.isWeakPin("7315"))
    }

    @Test
    fun isWeakPin_rejectsMalformedLengths() {
        assertTrue("wrong length is not a valid pin", SecurityHelper.isWeakPin("123"))
        assertTrue("6-digit is not a valid pin", SecurityHelper.isWeakPin("123456"))
        assertTrue("empty is not a valid pin", SecurityHelper.isWeakPin(""))
    }

    @Test
    fun verifyPinLenient_strictFourDigitsOnly() {
        val pin = "4567"
        val hash = SecurityHelper.hashPin(pin)

        assertTrue("Strict 4-digit pin verifies normally", SecurityHelper.verifyPinLenient(pin, hash).isMatch)
        assertFalse("6 digits must not pass the gate", SecurityHelper.verifyPinLenient("654321", hash).isMatch)
        assertTrue("Strict 4-digit pin verifies normally", SecurityHelper.verifyPin("4567", SecurityHelper.hashPin("4567")).isMatch)
    }

    @Test
    fun verifyPinLenient_rejectsWrongLengthAndWrongPin() {
        val hash = SecurityHelper.hashPin("4567")
        assertFalse("5 digits must not pass the gate", SecurityHelper.verifyPinLenient("65432", hash).isMatch)
        assertFalse("wrong 6-digit must fail", SecurityHelper.verifyPinLenient("111111", hash).isMatch)
        assertFalse("wrong 4-digit must fail", SecurityHelper.verifyPinLenient("1111", hash).isMatch)
    }

    @Test
    fun hashPin_and_verifyPin_successAndFailure() {
        val pin = "5371"
        val hash = SecurityHelper.hashPin(pin)

        assertTrue("Hash should start with pbkdf2 prefix", hash.startsWith("pbkdf2$120000$"))
        val matchResult = SecurityHelper.verifyPin(pin, hash)
        assertTrue("Correct PIN must match", matchResult.isMatch)
        assertFalse("Modern PBKDF2 hash should not need upgrade", matchResult.needsUpgrade)

        val wrongResult = SecurityHelper.verifyPin("1111", hash)
        assertFalse("Wrong PIN must be rejected", wrongResult.isMatch)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected.toLong(), actual.toLong())
    }
}
