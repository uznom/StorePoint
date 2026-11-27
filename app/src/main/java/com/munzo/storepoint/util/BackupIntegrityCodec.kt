package com.munzo.storepoint.util

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Provides deterministic JSON canonicalization + SHA-256 checksum verification for StorePoint
 * backups, plus an API-24-safe RFC 4648 Base64 codec that works on both Android and JVM tests.
 */
internal object BackupIntegrityCodec {

    const val CHECKSUM_ALGO_CANONICAL = "SHA-256-CANONICAL"

    private const val B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun computeCanonicalChecksum(tables: JSONObject): String =
        sha256Hex(canonicalizeJson(tables))

    /**
     * Verifies a backup envelope's checksum before destructive restore.
     * - Backups with `checksumAlgorithm == SHA-256-CANONICAL` must match the canonical SHA-256.
     * - Legacy backups carrying a `checksum` without `checksumAlgorithm` match either canonical
     *   or raw `tables.toString()` SHA-256.
     * - Legacy backups without a `checksum` field are accepted for backward compatibility.
     */
    fun verifyChecksum(root: JSONObject, tables: JSONObject): Boolean {
        val expected = root.optString("checksum", "").trim()
        if (expected.isEmpty()) return true

        val algo = root.optString("checksumAlgorithm", "")
        val canonicalActual = computeCanonicalChecksum(tables)
        if (MessageDigest.isEqual(
                canonicalActual.lowercase().toByteArray(Charsets.UTF_8),
                expected.lowercase().toByteArray(Charsets.UTF_8)
            )
        ) {
            return true
        }
        if (algo == CHECKSUM_ALGO_CANONICAL) {
            return false
        }
        val legacyActual = sha256Hex(tables.toString())
        return MessageDigest.isEqual(
            legacyActual.lowercase().toByteArray(Charsets.UTF_8),
            expected.lowercase().toByteArray(Charsets.UTF_8)
        )
    }

    internal fun canonicalizeJson(value: Any?): String {
        val sb = StringBuilder()
        appendCanonical(sb, value)
        return sb.toString()
    }

    private fun appendCanonical(sb: StringBuilder, value: Any?) {
        when (value) {
            null, JSONObject.NULL -> sb.append("null")
            is JSONObject -> {
                sb.append('{')
                val sortedKeys = mutableListOf<String>()
                val iter = value.keys()
                while (iter.hasNext()) sortedKeys.add(iter.next())
                sortedKeys.sort()
                for (i in sortedKeys.indices) {
                    if (i > 0) sb.append(',')
                    val k = sortedKeys[i]
                    sb.append(JSONObject.quote(k)).append(':')
                    appendCanonical(sb, value.opt(k))
                }
                sb.append('}')
            }
            is JSONArray -> {
                sb.append('[')
                for (i in 0 until value.length()) {
                    if (i > 0) sb.append(',')
                    appendCanonical(sb, value.opt(i))
                }
                sb.append(']')
            }
            is String -> sb.append(JSONObject.quote(value))
            is Boolean -> sb.append(if (value) "true" else "false")
            is Number -> sb.append(normalizeNumber(value))
            else -> sb.append(JSONObject.quote(value.toString()))
        }
    }

    private fun normalizeNumber(n: Number): String {
        val d = n.toDouble()
        if (n is Int || n is Long || n is Short || n is Byte) return n.toString()
        if (!d.isFinite()) return "0"
        return if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun encodeBase64(bytes: ByteArray): String {
        return try {
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (encoded != null) encoded else encodeBase64Pure(bytes)
        } catch (_: Throwable) {
            encodeBase64Pure(bytes)
        }
    }

    fun decodeBase64(str: String): ByteArray {
        return try {
            val decoded = Base64.decode(str, Base64.DEFAULT)
            if (decoded != null) decoded else decodeBase64Pure(str)
        } catch (_: Throwable) {
            decodeBase64Pure(str)
        }
    }

    internal fun encodeBase64Pure(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val out = StringBuilder(((input.size + 2) / 3) * 4)
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val hasB1 = i + 1 < input.size
            val hasB2 = i + 2 < input.size
            val b1 = if (hasB1) input[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) input[i + 2].toInt() and 0xFF else 0

            out.append(B64_ALPHABET[(b0 ushr 2) and 0x3F])
            out.append(B64_ALPHABET[((b0 and 0x03) shl 4) or ((b1 ushr 4) and 0x0F)])
            out.append(if (hasB1) B64_ALPHABET[((b1 and 0x0F) shl 2) or ((b2 ushr 6) and 0x03)] else '=')
            out.append(if (hasB2) B64_ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    internal fun decodeBase64Pure(input: String): ByteArray {
        val clean = input.filter { !it.isWhitespace() }
        if (clean.isEmpty()) return ByteArray(0)
        require(clean.length % 4 == 0) { "Invalid Base64 length." }
        val padding = when {
            clean.endsWith("==") -> 2
            clean.endsWith("=") -> 1
            else -> 0
        }
        val out = ByteArray((clean.length / 4) * 3 - padding)
        var outIdx = 0
        var i = 0
        while (i < clean.length) {
            val c0 = b64Value(clean[i])
            val c1 = b64Value(clean[i + 1])
            val c2 = if (clean[i + 2] == '=') 0 else b64Value(clean[i + 2])
            val c3 = if (clean[i + 3] == '=') 0 else b64Value(clean[i + 3])
            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            if (outIdx < out.size) out[outIdx++] = ((triple ushr 16) and 0xFF).toByte()
            if (outIdx < out.size) out[outIdx++] = ((triple ushr 8) and 0xFF).toByte()
            if (outIdx < out.size) out[outIdx++] = (triple and 0xFF).toByte()
            i += 4
        }
        return out
    }

    private fun b64Value(ch: Char): Int = when (ch) {
        in 'A'..'Z' -> ch - 'A'
        in 'a'..'z' -> ch - 'a' + 26
        in '0'..'9' -> ch - '0' + 52
        '+' -> 62
        '/' -> 63
        else -> throw IllegalArgumentException("Invalid Base64 character: $ch")
    }
}
