package com.munzo.storepoint.util

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

object BiometricAuthHelper {

    fun isBiometricSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val pm = context.packageManager
        val hasFingerprint = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        val hasBiometrics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.hasSystemFeature(PackageManager.FEATURE_FACE) || pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
        } else false
        return hasFingerprint || hasBiometrics
    }

    fun authenticate(
        activity: Activity,
        title: String = "Cashier Biometric Verification",
        subtitle: String = "Verify fingerprint to unlock terminal",
        negativeButtonText: String = "Use Password",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ): CancellationSignal? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cancellationSignal = CancellationSignal()
            try {
                val executor = ContextCompat.getMainExecutor(activity)
                val prompt = BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButton(negativeButtonText, executor) { _, _ ->
                        onError("Biometric verification cancelled")
                    }
                    .build()

                prompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            onError(errString?.toString() ?: "Biometric error code: $errorCode")
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onError("Biometric not recognized. Please try again.")
                        }
                    }
                )
                return cancellationSignal
            } catch (e: Exception) {
                onError("Biometric prompt unavailable: ${e.localizedMessage}")
                return null
            }
        } else {
            onError("Biometrics require Android 9.0 (API 28) or higher")
            return null
        }
    }
}
