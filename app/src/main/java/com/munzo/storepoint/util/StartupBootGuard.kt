package com.munzo.storepoint.util

import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zero-dependency startup boot guard & crash-loop detector.
 *
 * Intentionally avoids Room, ViewModel, and Compose dependencies so it can evaluate
 * startup health and route directly into Safe Mode before any heavy subsystem initializes.
 */
object StartupBootGuard {

    internal const val PREFS_NAME = "storepoint_boot_guard"
    internal const val KEY_BOOT_IN_PROGRESS = "boot_in_progress"
    internal const val KEY_CONSECUTIVE_CRASHES = "consecutive_startup_crashes"
    internal const val KEY_LAST_BOOT_ATTEMPT_MS = "last_boot_attempt_ms"
    internal const val KEY_LAST_CRASH_MS = "last_crash_timestamp_ms"
    internal const val KEY_LAST_CRASH_EXCEPTION = "last_crash_exception"
    internal const val KEY_LAST_CRASH_MESSAGE = "last_crash_message"
    internal const val KEY_LAST_CRASH_STACKTRACE = "last_crash_stacktrace"
    internal const val KEY_LAST_CRASH_THREAD = "last_crash_thread"
    internal const val KEY_FORCE_SAFE_MODE = "force_safe_mode"

    /** Two rapid or unfinished startup crashes trip Safe Mode automatically. */
    const val CRASH_LOOP_THRESHOLD = 2

    /** Any crash occurring within 15 seconds of launch counts as a startup crash. */
    const val STARTUP_CRASH_WINDOW_MS = 15_000L

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    data class BootSnapshot(
        val bootInProgress: Boolean = false,
        val consecutiveStartupCrashes: Int = 0,
        val lastBootAttemptMs: Long = 0L,
        val lastCrashTimestampMs: Long = 0L,
        val lastCrashException: String = "",
        val lastCrashMessage: String = "",
        val lastCrashStackTrace: String = "",
        val lastCrashThread: String = "",
        val forceSafeMode: Boolean = false
    )

    data class LaunchEvaluation(
        val enterSafeMode: Boolean,
        val reason: String,
        val consecutiveCrashes: Int,
        val lastCrashException: String,
        val lastCrashMessage: String,
        val lastCrashStackTrace: String,
        val lastCrashThread: String,
        val lastCrashTimestampMs: Long
    )

    /**
     * Pure deterministic evaluator for unit tests and runtime boot gating.
     */
    fun evaluateBootSnapshot(snapshot: BootSnapshot, nowMs: Long = System.currentTimeMillis()): LaunchEvaluation {
        val unfinishedPreviousBoot = snapshot.bootInProgress &&
            snapshot.lastBootAttemptMs > 0L &&
            (nowMs - snapshot.lastBootAttemptMs) in 0L..120_000L

        val effectiveCrashCount = if (unfinishedPreviousBoot) {
            (snapshot.consecutiveStartupCrashes + 1).coerceAtLeast(1)
        } else {
            snapshot.consecutiveStartupCrashes
        }

        val shouldEnterSafeMode = snapshot.forceSafeMode || effectiveCrashCount >= CRASH_LOOP_THRESHOLD

        val reason = when {
            snapshot.forceSafeMode -> "Startup failure detected during initialization."
            effectiveCrashCount >= CRASH_LOOP_THRESHOLD ->
                "Detected $effectiveCrashCount consecutive startup crashes. Safe Mode activated to prevent a crash loop."
            else -> "Healthy"
        }

        return LaunchEvaluation(
            enterSafeMode = shouldEnterSafeMode,
            reason = reason,
            consecutiveCrashes = effectiveCrashCount,
            lastCrashException = snapshot.lastCrashException.ifBlank { "StartupException" },
            lastCrashMessage = snapshot.lastCrashMessage.ifBlank {
                if (shouldEnterSafeMode) "The previous launch terminated before completing startup." else ""
            },
            lastCrashStackTrace = snapshot.lastCrashStackTrace,
            lastCrashThread = snapshot.lastCrashThread,
            lastCrashTimestampMs = snapshot.lastCrashTimestampMs
        )
    }

    /**
     * Pure helper for computing updated crash streak on uncaught exception.
     */
    fun computeNextCrashCount(
        currentCount: Int,
        bootInProgress: Boolean,
        lastBootAttemptMs: Long,
        crashTimeMs: Long
    ): Int {
        val withinStartupWindow = lastBootAttemptMs > 0L &&
            (crashTimeMs - lastBootAttemptMs) in 0L..STARTUP_CRASH_WINDOW_MS
        return if (bootInProgress || withinStartupWindow) {
            currentCount + 1
        } else {
            currentCount
        }
    }

    fun readSnapshot(context: Context): BootSnapshot {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            BootSnapshot(
                bootInProgress = prefs.getBoolean(KEY_BOOT_IN_PROGRESS, false),
                consecutiveStartupCrashes = prefs.getInt(KEY_CONSECUTIVE_CRASHES, 0),
                lastBootAttemptMs = prefs.getLong(KEY_LAST_BOOT_ATTEMPT_MS, 0L),
                lastCrashTimestampMs = prefs.getLong(KEY_LAST_CRASH_MS, 0L),
                lastCrashException = prefs.getString(KEY_LAST_CRASH_EXCEPTION, "") ?: "",
                lastCrashMessage = prefs.getString(KEY_LAST_CRASH_MESSAGE, "") ?: "",
                lastCrashStackTrace = prefs.getString(KEY_LAST_CRASH_STACKTRACE, "") ?: "",
                lastCrashThread = prefs.getString(KEY_LAST_CRASH_THREAD, "") ?: "",
                forceSafeMode = prefs.getBoolean(KEY_FORCE_SAFE_MODE, false)
            )
        } catch (_: Throwable) {
            BootSnapshot()
        }
    }

    /**
     * Called at the start of [com.munzo.storepoint.MainActivity.onCreate] before any ViewModel or Room access.
     * Evaluates whether the previous launches crashed repeatedly and marks the current launch as in-progress.
     */
    fun evaluateAndBeginLaunch(context: Context): LaunchEvaluation {
        val now = System.currentTimeMillis()
        val snapshot = readSnapshot(context)
        val evaluation = evaluateBootSnapshot(snapshot, now)

        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_BOOT_IN_PROGRESS, !evaluation.enterSafeMode)
                .putInt(KEY_CONSECUTIVE_CRASHES, evaluation.consecutiveCrashes)
                .putLong(KEY_LAST_BOOT_ATTEMPT_MS, now)
                .commit()
        } catch (_: Throwable) {
            // Never allow SharedPreferences failure to crash launch.
        }

        return evaluation
    }

    /**
     * Called once the activity finishes preflight and renders its healthy primary UI.
     */
    fun markBootHealthy(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_BOOT_IN_PROGRESS, false)
                .putInt(KEY_CONSECUTIVE_CRASHES, 0)
                .putBoolean(KEY_FORCE_SAFE_MODE, false)
                .apply()
        } catch (_: Throwable) {}
    }

    /**
     * Records an uncaught exception synchronously using [SharedPreferences.Editor.commit]
     * so the crash trace and streak survive immediate Android process termination.
     */
    fun recordUncaughtCrash(context: Context, threadName: String, throwable: Throwable, forceSafeMode: Boolean = false) {
        try {
            val now = System.currentTimeMillis()
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val bootInProgress = prefs.getBoolean(KEY_BOOT_IN_PROGRESS, false)
            val currentCount = prefs.getInt(KEY_CONSECUTIVE_CRASHES, 0)
            val lastBootAttemptMs = prefs.getLong(KEY_LAST_BOOT_ATTEMPT_MS, 0L)
            val nextCount = computeNextCrashCount(currentCount, bootInProgress, lastBootAttemptMs, now)

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stack = sw.toString().take(12_000)

            prefs.edit()
                .putBoolean(KEY_BOOT_IN_PROGRESS, false)
                .putInt(KEY_CONSECUTIVE_CRASHES, if (forceSafeMode) nextCount.coerceAtLeast(CRASH_LOOP_THRESHOLD) else nextCount)
                .putLong(KEY_LAST_CRASH_MS, now)
                .putString(KEY_LAST_CRASH_EXCEPTION, throwable.javaClass.name)
                .putString(KEY_LAST_CRASH_MESSAGE, throwable.localizedMessage ?: throwable.message ?: "No error message")
                .putString(KEY_LAST_CRASH_STACKTRACE, stack)
                .putString(KEY_LAST_CRASH_THREAD, threadName)
                .putBoolean(KEY_FORCE_SAFE_MODE, forceSafeMode || nextCount >= CRASH_LOOP_THRESHOLD)
                .commit()
        } catch (_: Throwable) {}
    }

    /**
     * Clears the safe-mode latch when the operator chooses "Retry Normal Launch".
     */
    fun clearSafeModeForRetry(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_BOOT_IN_PROGRESS, false)
                .putInt(KEY_CONSECUTIVE_CRASHES, 0)
                .putBoolean(KEY_FORCE_SAFE_MODE, false)
                .commit()
        } catch (_: Throwable) {}
    }

    fun formatTimestamp(ms: Long): String {
        if (ms <= 0L) return "Unknown time"
        return synchronized(timestampFormat) {
            timestampFormat.format(Date(ms))
        }
    }

    fun getDeviceSummary(): String {
        val manufacturer = try { Build.MANUFACTURER ?: "Generic" } catch (_: Throwable) { "Generic" }
        val model = try { Build.MODEL ?: "Android" } catch (_: Throwable) { "Android" }
        val release = try { Build.VERSION.RELEASE ?: "?" } catch (_: Throwable) { "?" }
        val sdk = try { Build.VERSION.SDK_INT } catch (_: Throwable) { 0 }
        return "$manufacturer $model · Android $release (API $sdk) · StorePoint v$APP_VERSION"
    }
}
