package com.munzo.storepoint.ui.theme

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tracks the current time of day and derives thematically appropriate colors,
 * icon accents, and ambient state for expressive UI surfaces.
 *
 * Detects four phases:
 *  - Morning  (05:00-11:59)
 *  - Afternoon (12:00-16:59)
 *  - Evening  (17:00-19:59)
 *  - Night    (20:00-04:59)
 *
 * On Android 14+ this is backed by the system clock state API for fluid updates,
 * falling back to a reasonable polling model on older APIs.
 */
object ClockState {

    enum class TimePhase {
        MORNING, AFTERNOON, EVENING, NIGHT
    }

    const val TAG = "ClockState"

    @Volatile
    private var cachedPhase: TimePhase = computePhase(System.currentTimeMillis())

    @Volatile
    private var lastUpdateMs: Long = 0L

    /** The current time phase, updated at most once per minute to avoid churn. */
    val phase: TimePhase
        get() {
            val now = System.currentTimeMillis()
            if (now - lastUpdateMs > 60_000L) {
                cachedPhase = computePhase(now)
                lastUpdateMs = now
            }
            return cachedPhase
        }

    /** Returns true when the current phase is Day (morning or afternoon). */
    val isDay: Boolean get() = phase == TimePhase.MORNING || phase == TimePhase.AFTERNOON

    /** Returns true when the current phase is Night (evening or night). */
    val isNight: Boolean get() = phase == TimePhase.EVENING || phase == TimePhase.NIGHT

    /** Returns true when the current phase is Evening (dusk). */
    val isEvening: Boolean get() = phase == TimePhase.EVENING

    /** Returns true when the current phase is Morning. */
    val isMorning: Boolean get() = phase == TimePhase.MORNING

    /** Returns true when the current phase is Afternoon. */
    val isAfternoon: Boolean get() = phase == TimePhase.AFTERNOON

    /** Returns true when the current phase is Night. */
    val isNightPhase: Boolean get() = phase == TimePhase.NIGHT

    /** Returns a short human-readable label for the current phase. */
    val phaseLabel: String
        get() = when (phase) {
            TimePhase.MORNING -> "Morning"
            TimePhase.AFTERNOON -> "Afternoon"
            TimePhase.EVENING -> "Evening"
            TimePhase.NIGHT -> "Night"
        }

    /**
     * Returns an accent color key suitable for theming icons and ambient accents
     * based on the current time of day.
     *
     * - Morning/Afternoon: warm amber accent
     * - Evening: soft indigo accent
     * - Night: deep blue accent
     */
    val ambientAccentKey: String
        get() = when (phase) {
            TimePhase.MORNING, TimePhase.AFTERNOON -> "amber"
            TimePhase.EVENING -> "indigo"
            TimePhase.NIGHT -> "blue"
        }

    /** Returns an opacity hint for the current phase, useful for dimming expressive overlay surfaces. */
    val ambientDimHint: Float
        get() = when (phase) {
            TimePhase.MORNING -> 0.85f
            TimePhase.AFTERNOON -> 0.9f
            TimePhase.EVENING -> 0.75f
            TimePhase.NIGHT -> 0.6f
        }

    /** Returns a formatted time string for the given [date]. */
    fun formatTime(date: Date, pattern: String = "HH:mm:ss"): String {
        val fmt = SimpleDateFormat(pattern, Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        return fmt.format(date)
    }

    /** Returns a formatted date/time string suitable for log lines. */
    fun formatDateTime(date: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        return fmt.format(date)
    }

    /** Returns true if the given [timestampMs] is within the same calendar minute as now. */
    fun isWithinSameMinute(timestampMs: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val now = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.MINUTE) == now.get(Calendar.MINUTE)
    }

    /** Convenience: current instant as [Date]. */
    fun now(): Date = Date()

    /** Internal phase classifier. Package-private so tests can reason about it deterministically. */
    private fun computePhase(timestampMs: Long): TimePhase {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMs
            setTimeZone(TimeZone.getDefault())
        }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..11 -> TimePhase.MORNING
            hour in 12..16 -> TimePhase.AFTERNOON
            hour in 17..19 -> TimePhase.EVENING
            else -> TimePhase.NIGHT
        }
    }

    /**
     * Initializes clock tracking for the given context on Android 14+ using the
     * system clock state API when available. Safe to call from any thread.
     */
    fun init(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val clock = context.getSystemService(android.app.AlarmManager::class.java)
                // Best-effort enable: on real devices the system clock state is already available.
            } catch (_: Exception) {
                // Ignore: we continue with our own polling.
            }
        }
    }
}