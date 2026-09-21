package com.munzo.storepoint.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityHelper {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES_LENGTH = 16

    data class VerificationResult(
        val isMatch: Boolean,
        val needsUpgrade: Boolean
    )

    /**
     * Validates whether a candidate PIN conforms to the strict 6-digit numeric standard.
     */
    fun isValidPin(pin: String): Boolean {
        return pin.length == 6 && pin.all { it.isDigit() }
    }

    /**
     * Generates a salted PBKDF2-HMAC-SHA256 hash for a 6-digit PIN.
     */
    fun hashPin(pin: String): String = hashPassword(pin)

    /**
     * Verifies candidate PIN against stored PBKDF2 hash or legacy hash.
     */
    fun verifyPin(pin: String, storedHash: String): VerificationResult = verifyPassword(pin, storedHash)

    /**
     * Generates a salted PBKDF2-HMAC-SHA256 hash using a cryptographically secure random salt.
     * Format: pbkdf2$120000$<saltHex>$<hashHex>
     */
    fun hashPassword(password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES_LENGTH)
        random.nextBytes(salt)

        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = skf.generateSecret(spec).encoded

        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }

        return "pbkdf2$${ITERATIONS}$${saltHex}$${hashHex}"
    }

    /**
     * Verifies a candidate plaintext password against a stored hash.
     * Supports:
     * 1) Modern salted PBKDF2 format ("pbkdf2$...")
     * 2) Legacy SHA-256 (64-char hex)
     * 3) Legacy plaintext
     *
     * Returns true if valid, along with needsUpgrade = true if the hash should be migrated to modern PBKDF2.
     */
    fun verifyPassword(password: String, storedHash: String): VerificationResult {
        if (storedHash.isBlank()) return VerificationResult(isMatch = false, needsUpgrade = false)

        if (storedHash.startsWith("pbkdf2$")) {
            val parts = storedHash.split("$")
            if (parts.size == 4) {
                try {
                    val iterations = parts[1].toIntOrNull() ?: ITERATIONS
                    val saltHex = parts[2]
                    val expectedHashHex = parts[3]

                    val salt = hexToByteArray(saltHex)
                    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
                    val skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                    val computedHash = skf.generateSecret(spec).encoded
                    val computedHashHex = computedHash.joinToString("") { "%02x".format(it) }

                    val isMatch = MessageDigest.isEqual(
                        computedHashHex.toByteArray(Charsets.UTF_8),
                        expectedHashHex.toByteArray(Charsets.UTF_8)
                    )
                    val needsUpgrade = isMatch && (iterations < ITERATIONS)
                    return VerificationResult(isMatch = isMatch, needsUpgrade = needsUpgrade)
                } catch (e: Exception) {
                    return VerificationResult(isMatch = false, needsUpgrade = false)
                }
            }
        }

        // Legacy SHA-256 check (64 hex characters)
        if (storedHash.length == 64 && storedHash.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            val sha256Candidate = MessageDigest.getInstance("SHA-256")
                .digest(password.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val isShaMatch = MessageDigest.isEqual(
                sha256Candidate.lowercase().toByteArray(Charsets.UTF_8),
                storedHash.lowercase().toByteArray(Charsets.UTF_8)
            )
            if (isShaMatch) {
                return VerificationResult(isMatch = true, needsUpgrade = true)
            }
        }

        // Legacy plaintext fallback check
        if (MessageDigest.isEqual(password.toByteArray(Charsets.UTF_8), storedHash.toByteArray(Charsets.UTF_8))) {
            return VerificationResult(isMatch = true, needsUpgrade = true)
        }

        return VerificationResult(isMatch = false, needsUpgrade = false)
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
