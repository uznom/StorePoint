package com.munzo.storepoint.util

/**
 * Progressive backoff for a failed attempt at a high-value local credential.
 *
 * The kiosk PIN guards a cash register, and its keyspace is only
 * `10^PIN_LENGTH` (10,000 for a 4-digit PIN). A flat "5 tries then 60 seconds"
 * policy still allows thousands of guesses per hour, so this escalates the
 * window on each successive failure and, critically, makes the window grow
 * without ever being reset by simply reopening the dialog.
 *
 * This is a pure object with no Android dependency so the schedule can be unit
 * tested directly rather than eyeballed on a terminal.
 */
object KioskLockdownPolicy {

    /**
     * Attempts allowed in a burst before any lockout is imposed. Forgiving the
     * first couple of entries matters on a touchscreen register where fat-finger
     * mistakes are routine; everything after that is treated as hostile.
     */
    const val FREE_ATTEMPTS: Int = 2

    /** Longest enforced window, reached after [MAX_LOCKOUT_MS] is exceeded. */
    const val MAX_LOCKOUT_MS: Long = 300_000L // 5 minutes

    /**
     * Lockout windows in order, applied to the failure *after* [FREE_ATTEMPTS]
     * have been consumed. The final entry is reused for every further failure.
     */
    private val WINDOWS_MS = longArrayOf(
        30_000L,  // 30s
        60_000L,  // 1m
        120_000L, // 2m
        MAX_LOCKOUT_MS
    )

    /**
     * The lockout window to impose after `failCount` total failures.
     *
     * Returns `0` while the caller is still within [FREE_ATTEMPTS], meaning
     * "record the failure but do not lock".
     */
    fun lockoutWindowFor(failCount: Int): Long {
        if (failCount <= FREE_ATTEMPTS) return 0L
        val index = (failCount - FREE_ATTEMPTS - 1).coerceAtMost(WINDOWS_MS.lastIndex)
        return WINDOWS_MS[index]
    }

    /** Milliseconds remaining on an active lockout, or `0` when not locked. */
    fun remainingMs(lockedUntil: Long, now: Long): Long =
        if (now < lockedUntil) lockedUntil - now else 0L

    /** Human-readable lockout message, e.g. `Try again in 27s.` */
    fun lockoutMessage(lockedUntil: Long, now: Long): String {
        val remainSec = (remainingMs(lockedUntil, now) / 1000L).toInt().coerceAtLeast(1)
        return "Too many failed attempts. Locked for ${remainSec}s."
    }
}

/**
 * Per-credential failure counter with escalating lockout.
 *
 * Split from [KioskLockdownPolicy] so the policy (pure, static) stays trivially
 * testable while the persistence stays swappable. Production uses
 * `SharedPreferences`; tests use an in-memory map and a fake clock.
 *
 * Counters are keyed so that distinct credentials of different risk do not share
 * a throttle — leaving lockdown and toggling the Wi-Fi radio are not the same
 * action, and exhausting one must not make the other (or vice versa) harder to
 * reach during an incident.
 */
class KioskLockout(
    private val readFailCount: (String) -> Int,
    private val readLockedUntil: (String) -> Long,
    private val writeState: (String, Int, Long) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    /** Milliseconds remaining on the active lockout for [key], or `0` if unlocked. */
    fun remainingMs(key: String): Long = KioskLockdownPolicy.remainingMs(readLockedUntil(key), now())

    /** The live lockout message for [key], or `null` when not locked. */
    fun lockoutMessage(key: String): String? {
        val remain = remainingMs(key)
        return if (remain > 0L) {
            "Too many failed attempts. Locked for ${(remain / 1000L).toInt().coerceAtLeast(1)}s."
        } else {
            null
        }
    }

    /** Clears the failure history for [key] after a successful verification. */
    fun onSuccess(key: String) = writeState(key, 0, 0L)

    /**
     * Records a failed attempt and returns the message to show the user.
     *
     * Imposes the escalating window once the burst allowance is exhausted, so a
     * caller cannot loop indefinitely against a 10,000-combination keyspace.
     */
    fun onFailure(key: String): String {
        val fails = readFailCount(key) + 1
        val window = KioskLockdownPolicy.lockoutWindowFor(fails)
        if (window > 0L) {
            writeState(key, 0, now() + window)
            return KioskLockdownPolicy.lockoutMessage(now() + window, now())
        }
        writeState(key, fails, 0L)
        val remainingFree = KioskLockdownPolicy.FREE_ATTEMPTS - fails
        return if (remainingFree > 0) {
            "Incorrect PIN. $remainingFree attempt${if (remainingFree == 1) "" else "s"} remaining before lockout."
        } else {
            "Incorrect PIN. This is your last attempt before a lockout."
        }
    }
}
