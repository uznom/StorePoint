package com.munzo.storepoint.util

/**
 * Deterministic keystroke-burst detector for external USB/Bluetooth HID barcode scanners.
 *
 * Hardware HID scanners emulate a physical keyboard and emit characters rapidly (typically
 * 2–25ms apart) followed by Enter or Tab, whereas human typing averages 90–300ms between keys.
 * This pure-Kotlin state machine isolates scanner bursts without depending on Android classes
 * so it is 100% unit-testable on the JVM.
 */
class HidBarcodeScannerBuffer(
    private val maxInterKeyIntervalMs: Long = DEFAULT_MAX_INTER_KEY_MS,
    private val minBarcodeLength: Int = DEFAULT_MIN_BARCODE_LENGTH,
    private val maxBarcodeLength: Int = DEFAULT_MAX_BARCODE_LENGTH
) {
    private val buffer = StringBuilder()
    private var lastKeyTimeMs: Long = 0L

    /**
     * Records a printable character keystroke at [timestampMs].
     * If the gap since the previous keystroke exceeds [maxInterKeyIntervalMs],
     * any prior buffered characters are discarded and a fresh burst begins.
     */
    fun onPrintableChar(char: Char, timestampMs: Long) {
        if (char.isISOControl()) return
        if (buffer.isNotEmpty() && (timestampMs - lastKeyTimeMs) > maxInterKeyIntervalMs) {
            buffer.clear()
        }
        if (buffer.length < maxBarcodeLength) {
            buffer.append(char)
        }
        lastKeyTimeMs = timestampMs
    }

    /**
     * Called when an Enter or Tab terminator arrives at [timestampMs].
     * Returns the sanitized scanned barcode if the buffered burst satisfies
     * the timing window and length bounds; otherwise returns null.
     */
    fun onTerminator(timestampMs: Long): String? {
        val elapsed = timestampMs - lastKeyTimeMs
        val candidate = buffer.toString().trim()
        reset()
        if (candidate.length < minBarcodeLength) return null
        if (elapsed > maxInterKeyIntervalMs * 2L) return null
        return candidate
    }

    /** Clears any in-flight characters (e.g., on focus change or modifier keys). */
    fun reset() {
        buffer.clear()
        lastKeyTimeMs = 0L
    }

    /** Current buffered character count (for diagnostics/tests). */
    val pendingLength: Int get() = buffer.length

    companion object {
        const val DEFAULT_MAX_INTER_KEY_MS = 65L
        const val DEFAULT_MIN_BARCODE_LENGTH = 4
        const val DEFAULT_MAX_BARCODE_LENGTH = 64
    }
}
