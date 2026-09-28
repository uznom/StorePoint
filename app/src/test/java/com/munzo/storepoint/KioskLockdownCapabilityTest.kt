package com.munzo.storepoint

import com.munzo.storepoint.KioskLockdownCapability
import com.munzo.storepoint.LockdownStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the terminal-lockdown capability model (issue #4).
 *
 * The bug: the Security tab reported "DEVICE PROTECTION ON" from `isAdminActive()`
 * alone. A terminal that was only a device *admin* looked fully protected, even though
 * every real restriction (uninstall block, app-control, true LockTask) sits behind an
 * `isDeviceOwnerApp()` branch that never ran. These tests pin the tier decision so the
 * conflation cannot come back.
 */
class KioskLockdownCapabilityTest {

    @Test
    fun deviceOwnerIsFullyEnforced() {
        val strength = KioskLockdownCapability.classify(
            isDeviceAdminActive = true,
            isDeviceOwner = true
        )
        assertEquals(LockdownStrength.DeviceOwnerEnforced, strength)
        assertTrue(KioskLockdownCapability.isEnforced(strength))
    }

    @Test
    fun deviceAdminWithoutDeviceOwnerIsOnlyScreenPinning() {
        // THE regression: this is the state that previously displayed as protected.
        val strength = KioskLockdownCapability.classify(
            isDeviceAdminActive = true,
            isDeviceOwner = false
        )
        assertEquals(LockdownStrength.ScreenPinningOnly, strength)
        assertFalse(
            "Admin-only must never be reported as enforced",
            KioskLockdownCapability.isEnforced(strength)
        )
    }

    @Test
    fun noAdminAtAllIsUnprotected() {
        val strength = KioskLockdownCapability.classify(
            isDeviceAdminActive = false,
            isDeviceOwner = false
        )
        assertEquals(LockdownStrength.Unprotected, strength)
        assertFalse(KioskLockdownCapability.isEnforced(strength))
    }

    @Test
    fun deviceOwnerWinsEvenIfAdminFlagIsInconsistent() {
        // Defensive: device owner implies device admin, so the strongest state must be
        // chosen even if a caller reports the two inconsistently.
        val strength = KioskLockdownCapability.classify(
            isDeviceAdminActive = false,
            isDeviceOwner = true
        )
        assertEquals(LockdownStrength.DeviceOwnerEnforced, strength)
    }

    @Test
    fun onlyDeviceOwnerIsEverConsideredEnforced() {
        // Only three of the four boolean combinations are physically reachable: device
        // owner always implies device admin. classify(false, true) is covered separately
        // by deviceOwnerWinsEvenIfAdminFlagIsInconsistent.
        val reachable = listOf(
            KioskLockdownCapability.classify(true, true),
            KioskLockdownCapability.classify(true, false),
            KioskLockdownCapability.classify(false, false)
        )
        assertEquals(
            "Exactly one reachable state may be enforced",
            1,
            reachable.count { KioskLockdownCapability.isEnforced(it) }
        )
    }

    @Test
    fun everyStrengthHasADistinctTruthfulLabelAndExplanation() {
        for (strength in LockdownStrength.entries) {
            val label = KioskLockdownCapability.label(strength)
            val explanation = KioskLockdownCapability.explanation(strength)
            assertTrue("Blank label for $strength", label.isNotBlank())
            assertTrue("Blank explanation for $strength", explanation.length > 40)

            // The label must not overstate protection. "REDUCED" is the key word that
            // must survive for the screen-pinning tier.
            if (strength == LockdownStrength.ScreenPinningOnly) {
                assertTrue("Degraded tier must say REDUCED: $label", label.contains("REDUCED"))
                // Matched loosely: the copy may hyphenate ("screen-pinning"), and a
                // test should not break over punctuation.
                assertTrue(
                    "Degraded explanation must mention screen pinning: $explanation",
                    explanation.contains("screen", ignoreCase = true) &&
                        explanation.contains("pinning", ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun labelsAreUnique() {
        val labels = LockdownStrength.entries.map { KioskLockdownCapability.label(it) }
        assertEquals("Each tier needs a distinguishable label", labels.size, labels.toSet().size)
    }

    @Test
    fun degradedExplanationWarnsUninstallIsNotBlocked() {
        // The single most dangerous misconception: owners assume device admin blocks
        // uninstall. It does not, unless StorePoint is device owner.
        val text = KioskLockdownCapability.explanation(LockdownStrength.ScreenPinningOnly)
        assertTrue(
            "Must state that uninstall is not blocked: $text",
            text.contains("uninstall", ignoreCase = true)
        )
    }
}
