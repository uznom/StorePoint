package com.munzo.storepoint.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Device-biometric gate used for primary (fingerprint-first) sign-in.
 *
 * Built on `androidx.biometric` rather than the platform
 * `android.hardware.biometrics.BiometricPrompt`, which is deprecated as of API 29 and
 * exposes no enrolment state, no error classification, and no contract letting
 * `DEVICE_CREDENTIAL` coexist with a negative button.
 *
 * Only `BIOMETRIC_STRONG` is accepted. Weak biometrics are deliberately excluded: this
 * unlocks a cash register and the kiosk lockdown, so a spoofable sensor is not an
 * acceptable trade.
 */
object BiometricAuthHelper {

    private val strongAuthenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /** Why a biometric attempt ended, so the UI reacts without string-matching. */
    enum class BiometricOutcome {
        /** Authenticated successfully. */
        SUCCESS,

        /** User chose the fallback. Not an error. */
        FALLBACK,

        /** Recoverable failure (bad finger). Offer a retry. */
        RECOVERABLE_ERROR,

        /** Unrecoverable for now (lockout). Escalate to PIN. */
        FATAL_ERROR,

        /** No usable hardware/enrolment, or API unavailable. Hide the affordance. */
        UNAVAILABLE
    }

    /**
     * Result of probing device capability. Preferred over the legacy boolean
     * [isBiometricSupported] so callers can distinguish "no hardware" (hide the button)
     * from "hardware present but nothing enrolled" (offer enrolment).
     */
    data class BiometricAvailability(
        val isAvailable: Boolean,
        val isHardwarePresent: Boolean,
        val canEnroll: Boolean,
        val reason: String
    )

    /** Probes whether a strong biometric (or device credential) can be used right now. */
    fun checkAvailability(context: Context): BiometricAvailability {
        val manager = BiometricManager.from(context)
        val canAuthenticate = manager.canAuthenticate(strongAuthenticators)
        val hardwarePresent = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE) ||
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)))

        return when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability(
                isAvailable = true,
                isHardwarePresent = true,
                canEnroll = true,
                reason = "Ready"
            )
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability(
                isAvailable = false,
                isHardwarePresent = hardwarePresent,
                canEnroll = hardwarePresent,
                reason = "No biometric enrolled on this device"
            )
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability(
                isAvailable = false,
                isHardwarePresent = false,
                canEnroll = false,
                reason = "No biometric hardware"
            )
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability(
                isAvailable = false,
                isHardwarePresent = hardwarePresent,
                canEnroll = hardwarePresent,
                reason = "Biometric hardware temporarily unavailable"
            )
            else -> BiometricAvailability(
                isAvailable = false,
                isHardwarePresent = hardwarePresent,
                canEnroll = false,
                reason = "Biometrics unsupported on this device"
            )
        }
    }

    /** True when a strong biometric can be used immediately. */
    fun isBiometricSupported(context: Context): Boolean = checkAvailability(context).isAvailable

    /**
     * Shows the system biometric prompt.
     *
     * Failures are delivered through [onOutcome] rather than thrown, so the caller can
     * always fall back to the PIN pad.
     *
     * @return the [CancellationSignal] driving the prompt, or null when the prompt
     *   could not be shown at all.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Fingerprint Sign-In",
        subtitle: String = "Verify to unlock the register",
        onOutcome: (BiometricOutcome, String) -> Unit,
        onSuccess: () -> Unit
    ): CancellationSignal? {
        val availability = checkAvailability(activity)
        if (!availability.isAvailable) {
            onOutcome(BiometricOutcome.UNAVAILABLE, availability.reason)
            return null
        }

        val cancellationSignal = CancellationSignal()
        return try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onOutcome(BiometricOutcome.SUCCESS, "Verified")
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        val message = errString.toString()
                        // Choosing the fallback is an ordinary "use my PIN instead"
                        // gesture, not a failure worth alarming the user about.
                        val outcome = when (errorCode) {
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED -> BiometricOutcome.FALLBACK

                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricOutcome.FATAL_ERROR

                            else -> BiometricOutcome.RECOVERABLE_ERROR
                        }
                        onOutcome(outcome, message)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        // Fired on an unrecognised finger while the prompt stays up.
                        // Deliberately non-terminal: the user can simply retry.
                        onOutcome(BiometricOutcome.RECOVERABLE_ERROR, "Fingerprint not recognized")
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(strongAuthenticators)
                .setConfirmationRequired(false)
                .build()

            prompt.authenticate(promptInfo)
            cancellationSignal
        } catch (e: Exception) {
            onOutcome(BiometricOutcome.UNAVAILABLE, "Biometric prompt unavailable: ${e.localizedMessage}")
            null
        }
    }
}
