package com.munzo.storepoint.util

/**
 * Rules for the two-stage kiosk PIN setup wizard: **enter**, then **verify**.
 *
 * Why the flow is staged, and why only one keypad is ever on screen: the original
 * dialog rendered both `PinPadEntry` widgets inside a single `AlertDialog`, and each
 * `PinPadEntry` is a complete on-screen keypad (OTP display + 4 rows of keys). Two
 * stacked keypads made the dialog taller than the display, so the
 * "Confirm & Enter Lockdown" button was pushed off-screen and lockdown could never be
 * completed. Splitting the flow into two stages keeps exactly one field, one keypad
 * and the action button on screen at a time, and lets a mistyped confirmation be
 * corrected with "Back" instead of dismissing the whole dialog.
 *
 * Pure Kotlin (no Android, no Compose) so the decisions are unit-testable — the same
 * reason [KioskLockdownPolicy] is pure. The dialog owns the stage and PIN state; this
 * object only answers "may this stage advance, and if not, why". Writing the PIN goes
 * through `StorePointViewModel.setKioskPin()`, which remains the single point of
 * enforcement for the strength policy on the actual preference write.
 */
object KioskPinSetupFlow {

    /** Stage of the setup wizard. */
    enum class Stage { ENTER, VERIFY }

    /** True when [pin] holds a full, well-formed [SecurityHelper.PIN_LENGTH] PIN. */
    fun isComplete(pin: String): Boolean = SecurityHelper.isValidPin(pin)

    /**
     * Reason the ENTER stage must not advance, or `null` when the chosen PIN may be
     * carried forward into the verification stage.
     *
     * The strength check happens here rather than only on write so the admin can pick
     * another code *before* having to retype the confirmation.
     */
    fun enterRejection(pin: String): String? = when {
        !SecurityHelper.isValidPin(pin) ->
            "Kiosk PIN must be exactly ${SecurityHelper.PIN_LENGTH} digits."
        SecurityHelper.isWeakPin(pin) ->
            "That Kiosk PIN is too easy to guess. ${SecurityHelper.pinPolicyHint()}"
        else -> null
    }

    /**
     * Reason the VERIFY stage must not commit, or `null` when both entries agree and
     * the PIN may be handed to `setKioskPin()`.
     *
     * Strength is deliberately not re-checked here: [enterRejection] already gates it,
     * and `setKioskPin()` re-enforces it on the write, so a weak PIN can never be
     * persisted even if a future caller skips a stage.
     */
    fun verifyRejection(entered: String, confirmed: String): String? = when {
        entered != confirmed ->
            "PINs do not match. Re-enter the same ${SecurityHelper.PIN_LENGTH} digits."
        !SecurityHelper.isValidPin(entered) ->
            "Kiosk PIN must be exactly ${SecurityHelper.PIN_LENGTH} digits."
        else -> null
    }
}
