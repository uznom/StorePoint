package com.munzo.storepoint

import com.munzo.storepoint.util.KioskPinSetupFlow
import com.munzo.storepoint.util.SecurityHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two-stage kiosk PIN setup wizard (choose -> verify).
 *
 * The dialog is a thin Compose shell over these rules, so the contract that matters
 * is asserted here: a PIN cannot reach the VERIFY stage unless it is complete and
 * passes the strength policy, and VERIFY refuses to commit anything the admin did not
 * literally re-enter. `setKioskPin()` remains the enforcement point on the write, so
 * these assertions describe the UX gates, not the only line of defence.
 */
class KioskPinSetupFlowTest {

    @Test
    fun isComplete_acceptsOnlyAFullFourDigitPin() {
        assertTrue(KioskPinSetupFlow.isComplete("5371"))
        assertFalse("empty must not be complete", KioskPinSetupFlow.isComplete(""))
        assertFalse("3 digits is short", KioskPinSetupFlow.isComplete("537"))
        assertFalse("6 digits is not the current format", KioskPinSetupFlow.isComplete("537192"))
        assertFalse("non-numeric is refused", KioskPinSetupFlow.isComplete("53a1"))
    }

    @Test
    fun enterStage_refusesAnythingButACompletePin() {
        assertNotNull("blank cannot advance", KioskPinSetupFlow.enterRejection(""))
        assertNotNull("partial cannot advance", KioskPinSetupFlow.enterRejection("537"))
        assertNotNull("5 digits cannot advance", KioskPinSetupFlow.enterRejection("53719"))
        assertNull("a complete 4-digit PIN advances", KioskPinSetupFlow.enterRejection("5371"))
    }

    @Test
    fun enterStage_refusesWeakPinsSoTheyNeverReachTheVerifyStage() {
        // These all satisfy isValidPin, so only the strength rule stands between them
        // and the confirmation step. KioskPinPolicyTest pins the full weak list.
        for (weak in listOf("1234", "0000", "1111", "4321", "2580", "1212")) {
            assertNotNull("$weak must be rejected at the enter stage", KioskPinSetupFlow.enterRejection(weak))
        }
        assertNull("a non-pattern PIN advances", KioskPinSetupFlow.enterRejection("8024"))
    }

    @Test
    fun verifyStage_commitsOnlyWhenBothEntriesMatch() {
        assertNull("matching entries commit", KioskPinSetupFlow.verifyRejection("5371", "5371"))
        assertNotNull(
            "a differing confirmation must not commit",
            KioskPinSetupFlow.verifyRejection("5371", "5372")
        )
        assertNotNull("an empty confirmation must not commit", KioskPinSetupFlow.verifyRejection("5371", ""))
        assertNotNull(
            "both blank must not commit",
            KioskPinSetupFlow.verifyRejection("", "")
        )
    }

    @Test
    fun verifyStage_messageTellsTheAdminWhatToDo() {
        val message = KioskPinSetupFlow.verifyRejection("5371", "7153")
        assertTrue(
            "mismatch message must name the failure: $message",
            message != null && message.contains("do not match")
        )
    }

    @Test
    fun stageEnum_hasExactlyTheTwoDocumentedStages() {
        // A future third stage must be a deliberate decision: the dialog title,
        // buttons and tests are all written against this pair.
        assertTrue(KioskPinSetupFlow.Stage.values().map { it.name } == listOf("ENTER", "VERIFY"))
    }

    /** The completeness rule is sized on the same constant as the throttle schedule. */
    @Test
    fun completenessTracksTheCanonicalPinLength() {
        assertTrue(SecurityHelper.PIN_LENGTH == 4)
        assertTrue(KioskPinSetupFlow.isComplete("1".repeat(SecurityHelper.PIN_LENGTH)))
        assertFalse(
            "a length drift would silently disable the stage gate",
            KioskPinSetupFlow.isComplete("1".repeat(SecurityHelper.PIN_LENGTH - 1))
        )
    }
}
