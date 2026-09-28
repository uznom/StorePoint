package com.munzo.storepoint

import com.munzo.storepoint.util.KioskLockdownPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kiosk PIN is held to the same strength policy as every user PIN (issue #9).
 *
 * Before this, `setKioskPin()` accepted 4-8 digits with no validation at all, so
 * `0000`, `1234` and `1111` were valid kiosk PINs - while every *account* PIN had to
 * pass `SecurityHelper.isValidPin` and `isWeakPin`. The credential guarding a cash
 * register was the easiest one in the app to guess.
 *
 * `setKioskPin` lives on the ViewModel (needs prefs), so these assert the policy it
 * delegates to. The enforcement call-site is covered by construction: `setKioskPin`
 * returns a non-null rejection string for every one of these inputs and never
 * reaches the prefs write.
 */
class KioskPinPolicyTest {

    /** Values `isWeakPin` must refuse, per the documented policy. */
    private val weak = listOf("0000", "1111", "9999", "1234", "4321", "0123", "1212", "2121", "1000", "2580", "0852")

    @Test
    fun weakKioskPinsAreRejectedByTheSharedPolicy() {
        for (pin in weak) {
            assertTrue(
                "Kiosk PIN policy must refuse '$pin'",
                com.munzo.storepoint.util.SecurityHelper.isWeakPin(pin)
            )
        }
    }

    @Test
    fun kioskPinLengthIsFixedToFourDigits() {
        // The old dialog accepted 4-8. Anything other than exactly PIN_LENGTH must
        // fail the validity check that setKioskPin applies first.
        assertTrue(com.munzo.storepoint.util.SecurityHelper.isValidPin("4571"))
        for (tooShort in listOf("", "1", "12", "123")) {
            assertTrue(
                "Must reject '$tooShort'",
                !com.munzo.storepoint.util.SecurityHelper.isValidPin(tooShort)
            )
        }
        for (tooLong in listOf("12345", "123456", "1234567", "12345678")) {
            assertTrue(
                "Must reject '$tooLong' (the old dialog allowed up to 8)",
                !com.munzo.storepoint.util.SecurityHelper.isValidPin(tooLong)
            )
        }
    }

    @Test
    fun nonNumericKioskPinsAreRejected() {
        for (bad in listOf("12a4", "abcd", "1 2 3", "-123")) {
            assertTrue(
                "Must reject '$bad'",
                !com.munzo.storepoint.util.SecurityHelper.isValidPin(bad)
            )
        }
    }

    @Test
    fun aReasonablePinPassesBothGates() {
        val pin = "4571"
        assertTrue(com.munzo.storepoint.util.SecurityHelper.isValidPin(pin))
        assertTrue(
            "A non-sequential, non-repeating PIN must still be accepted",
            !com.munzo.storepoint.util.SecurityHelper.isWeakPin(pin)
        )
    }

    @Test
    fun kioskPinKeyspaceMatchesTheThrottleAssumption() {
        // The escalating lockout is sized on the assumption that the kiosk PIN is a
        // 4-digit space. If the accepted length ever changes, the throttle schedule
        // has to be revisited, so pin the relationship explicitly.
        assertEquals(4, com.munzo.storepoint.util.SecurityHelper.PIN_LENGTH)
        assertEquals(10_000, Math.pow(10.0, com.munzo.storepoint.util.SecurityHelper.PIN_LENGTH.toDouble()).toInt())
    }

    @Test
    fun malformedPinIsNeitherValidNorAccepted() {
        // Guard against isValidPin/isWeakPin drifting apart in a way that lets a
        // weak value through: isWeakPin must return true for anything invalid, so a
        // caller checking only isWeakPin still fails closed.
        for (bad in listOf("", "1", "abc", "12345678")) {
            assertTrue(
                "isWeakPin must fail closed for '$bad'",
                com.munzo.storepoint.util.SecurityHelper.isWeakPin(bad)
            )
        }
    }
}
