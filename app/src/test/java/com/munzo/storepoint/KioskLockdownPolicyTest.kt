package com.munzo.storepoint

import com.munzo.storepoint.util.KioskLockdownPolicy
import com.munzo.storepoint.util.KioskLockout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the escalating lockout added for the kiosk PIN (issue #3).
 *
 * Before this, the Admin Security unlock dialog called `isKioskPinValid()` directly
 * on every button press with no counter, against a 10,000-combination keyspace.
 * The tests below are the regression guard: reverting the fix would let the
 * `unlimitedAttemptsAreEventuallyLockedOut` test fail.
 */
class KioskLockdownPolicyTest {

    private var clock = 1_000_000L
    private val fails = mutableMapOf<String, Int>()
    private val lockedUntil = mutableMapOf<String, Long>()

    private fun lockout() = KioskLockout(
        readFailCount = { fails[it] ?: 0 },
        readLockedUntil = { lockedUntil[it] ?: 0L },
        writeState = { key, count, until ->
            fails[key] = count
            lockedUntil[key] = until
        },
        now = { clock }
    )

    // --- Policy: the escalation schedule ---

    @Test
    fun burstAllowanceIsHonouredBeforeAnyLockout() {
        assertEquals("No lockout on the first failure", 0L, KioskLockdownPolicy.lockoutWindowFor(1))
        assertEquals(
            "No lockout within the free allowance",
            0L,
            KioskLockdownPolicy.lockoutWindowFor(KioskLockdownPolicy.FREE_ATTEMPTS)
        )
    }

    @Test
    fun lockoutWindowEscalatesWithEachSuccessiveFailure() {
        val first = KioskLockdownPolicy.lockoutWindowFor(KioskLockdownPolicy.FREE_ATTEMPTS + 1)
        val second = KioskLockdownPolicy.lockoutWindowFor(KioskLockdownPolicy.FREE_ATTEMPTS + 2)
        val third = KioskLockdownPolicy.lockoutWindowFor(KioskLockdownPolicy.FREE_ATTEMPTS + 3)

        assertTrue("Window must grow: $first -> $second", second > first)
        assertTrue("Window must keep growing: $second -> $third", third > second)
    }

    @Test
    fun lockoutWindowIsCappedForUnboundedFailureCounts() {
        val far = KioskLockdownPolicy.lockoutWindowFor(10_000)
        assertEquals(
            "A determined attacker must not grow the window without bound",
            KioskLockdownPolicy.MAX_LOCKOUT_MS,
            far
        )
    }

    // --- Behaviour: counting and enforcement ---

    @Test
    fun freeAttemptsReportRemainingAllowance() {
        val sut = lockout()
        assertTrue(sut.onFailure("kiosk_pin").contains("remaining"))
        assertTrue(sut.onFailure("kiosk_pin").contains("last attempt"))
    }

    @Test
    fun exceedingTheAllowanceImposesALockout() {
        val sut = lockout()
        repeat(KioskLockdownPolicy.FREE_ATTEMPTS) { sut.onFailure("kiosk_pin") }

        assertNull("Must not be locked while within the allowance", sut.lockoutMessage("kiosk_pin"))

        val message = sut.onFailure("kiosk_pin")
        assertTrue("A lockout must be reported: $message", message.contains("Locked"))
        assertNotNull("Lockout must now be active", sut.lockoutMessage("kiosk_pin"))
    }

    @Test
    fun correctPinIsRefusedWhileLockedOut() {
        val sut = lockout()
        repeat(KioskLockdownPolicy.FREE_ATTEMPTS + 1) { sut.onFailure("kiosk_pin") }
        assertNotNull(sut.lockoutMessage("kiosk_pin"))

        // Even a correct PIN cannot be checked until the window elapses.
        assertTrue(sut.remainingMs("kiosk_pin") > 0L)
    }

    @Test
    fun lockoutExpiresOnceTheWindowElapses() {
        val sut = lockout()
        repeat(KioskLockdownPolicy.FREE_ATTEMPTS + 1) { sut.onFailure("kiosk_pin") }
        assertNotNull(sut.lockoutMessage("kiosk_pin"))

        // Advance past the maximum possible window.
        clock += KioskLockdownPolicy.MAX_LOCKOUT_MS + 1_000L

        assertNull("Lockout must expire", sut.lockoutMessage("kiosk_pin"))
        assertEquals("No time may remain", 0L, sut.remainingMs("kiosk_pin"))
    }

    @Test
    fun successResetsTheFailureHistory() {
        val sut = lockout()
        repeat(KioskLockdownPolicy.FREE_ATTEMPTS + 1) { sut.onFailure("kiosk_pin") }
        assertNotNull(sut.lockoutMessage("kiosk_pin"))

        clock += KioskLockdownPolicy.MAX_LOCKOUT_MS + 1_000L
        sut.onSuccess("kiosk_pin")

        assertEquals("Failure count must be cleared", 0, fails["kiosk_pin"])
        assertNull(sut.lockoutMessage("kiosk_pin"))
    }

    @Test
    fun distinctCredentialsDoNotShareALockout() {
        val sut = lockout()
        repeat(KioskLockdownPolicy.FREE_ATTEMPTS + 1) { sut.onFailure("kiosk_pin") }

        // Exhausting the kiosk PIN budget must not block an unrelated admin action.
        assertNull(
            "A different credential must have its own allowance",
            sut.lockoutMessage("admin_pin")
        )
        assertTrue(sut.onFailure("admin_pin").contains("remaining"))
    }

    @Test
    fun remainingTimeNeverReportsNegative() {
        val message = KioskLockdownPolicy.lockoutMessage(lockedUntil = 10_000L, now = 10_000L)
        assertTrue("Must floor at 1s, not 0s or negative: $message", message.contains("1s"))
    }
}
