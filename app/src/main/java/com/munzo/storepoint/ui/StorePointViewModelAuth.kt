package com.munzo.storepoint.ui

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.data.User
import com.munzo.storepoint.util.BiometricAuthHelper
import com.munzo.storepoint.util.CrashDiagnosticsManager
import com.munzo.storepoint.util.DigitalServicesHelper
import com.munzo.storepoint.util.SecurityHelper
import kotlinx.coroutines.launch

// --- Authentication & User Management Extensions ---

internal fun StorePointViewModel.authSetKioskPinImpl(newPin: String, onResult: ((String?) -> Unit)?): String? {
    if (!SecurityHelper.isValidPin(newPin)) {
        val reason = "Kiosk PIN must be exactly ${SecurityHelper.PIN_LENGTH} digits."
        onResult?.invoke(reason)
        return reason
    }
    if (SecurityHelper.isWeakPin(newPin)) {
        val reason = "That Kiosk PIN is too easy to guess. ${SecurityHelper.pinPolicyHint()}"
        onResult?.invoke(reason)
        return reason
    }
    val hashedPin = SecurityHelper.hashPin(newPin)
    prefs.edit().putString("system_kiosk_pin", hashedPin).apply()
    kioskPin.value = hashedPin
    onResult?.invoke(null)
    return null
}

internal fun StorePointViewModel.authVerifyKioskPinUnlockImpl(
    pin: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    val active = kioskPinLockout.lockoutMessage(StorePointViewModel.KIOSK_PIN_LOCKOUT_KEY)
    if (active != null) {
        onFailure(active)
        return
    }

    if (isKioskPinValid(pin)) {
        kioskPinLockout.onSuccess(StorePointViewModel.KIOSK_PIN_LOCKOUT_KEY)
        onSuccess()
    } else {
        onFailure(kioskPinLockout.onFailure(StorePointViewModel.KIOSK_PIN_LOCKOUT_KEY))
    }
}

internal fun StorePointViewModel.authIsKioskPinValidImpl(candidate: String): Boolean {
    val storedHash = kioskPin.value ?: ""
    return storedHash.isNotEmpty() && SecurityHelper.verifyPassword(candidate, storedHash).isMatch
}

internal suspend fun StorePointViewModel.authVerifyAdminPinImpl(candidatePin: String): Boolean {
    if (candidatePin.isBlank()) return false

    val now = System.currentTimeMillis()
    val lockedUntil = prefs.getLong("admin_pin_locked_until", 0L)
    if (now < lockedUntil) return false

    val active = activeUser.value
    val verified = if (active != null && active.role.equals("ADMIN", ignoreCase = true) &&
        SecurityHelper.verifyPin(candidatePin, active.pinHash).isMatch
    ) {
        true
    } else {
        val dbAdmins = repository.getAllUsersSync().filter { it.role.equals("ADMIN", ignoreCase = true) }
        dbAdmins.isNotEmpty() && dbAdmins.any { SecurityHelper.verifyPin(candidatePin, it.pinHash).isMatch }
    }

    if (verified) {
        prefs.edit().putInt("admin_pin_fail_count", 0).putLong("admin_pin_locked_until", 0L).apply()
    } else {
        val fails = prefs.getInt("admin_pin_fail_count", 0) + 1
        if (fails >= 5) {
            prefs.edit().putInt("admin_pin_fail_count", 0).putLong("admin_pin_locked_until", now + 60_000L).apply()
        } else {
            prefs.edit().putInt("admin_pin_fail_count", fails).apply()
        }
    }
    return verified
}

internal fun StorePointViewModel.authToggleKioskModeImpl(active: Boolean) {
    prefs.edit().putBoolean("is_kiosk_mode_active", active).apply()
    isKioskModeActive.value = active
    if (!active) {
        kioskPinLockout.onSuccess(StorePointViewModel.KIOSK_PIN_LOCKOUT_KEY)
    }
}

internal fun StorePointViewModel.authLoginWithBarcodeImpl(
    barcodeId: String,
    onSuccess: (User) -> Unit,
    onFailure: (String) -> Unit
) {
    viewModelScope.launch {
        val user = repository.getUserByBarcode(barcodeId.trim())
        if (user != null) {
            activeUser.value = user
            playBeep()
            onSuccess(user)
        } else {
            onFailure("No staff member matches scanned barcode ID: $barcodeId")
        }
    }
}

internal fun StorePointViewModel.authAddAllowedTerminalAppImpl(pkg: String) {
    val clean = pkg.trim()
    if (clean.isBlank()) return
    val current = allowedTerminalApps.value.toMutableSet()
    current.add(clean)
    allowedTerminalApps.value = current
    val p = context.getSharedPreferences(DigitalServicesHelper.PREFS_NAME, Context.MODE_PRIVATE)
    DigitalServicesHelper.saveAllowedApps(p, current)
}

internal fun StorePointViewModel.authRemoveAllowedTerminalAppImpl(pkg: String) {
    val clean = pkg.trim()
    val current = allowedTerminalApps.value.toMutableSet()
    current.remove(clean)
    allowedTerminalApps.value = current
    val p = context.getSharedPreferences(DigitalServicesHelper.PREFS_NAME, Context.MODE_PRIVATE)
    DigitalServicesHelper.saveAllowedApps(p, current)
}

internal fun StorePointViewModel.authSwitchCashierWithPinImpl(
    pin: String,
    onResult: (Boolean, String, User?) -> Unit
) {
    viewModelScope.launch {
        val now = System.currentTimeMillis()
        val lockKey = "quick_switch_pin"
        val lockedUntil = prefs.getLong("${lockKey}_locked_until", 0L)
        if (now < lockedUntil) {
            val remainSec = ((lockedUntil - now) / 1000).toInt().coerceAtLeast(1)
            onResult(false, "Too many invalid PIN attempts. Try again in ${remainSec}s.", null)
            return@launch
        }
        if (!SecurityHelper.isValidPin(pin)) {
            onResult(false, "PIN must be exactly 4 numeric digits.", null)
            return@launch
        }
        val users = repository.getAllUsersSync().ifEmpty { allUsers.value }
        val match = users.firstOrNull { !it.pinResetRequired && SecurityHelper.verifyPin(pin, it.pinHash).isMatch }
        if (match != null) {
            prefs.edit().putInt("${lockKey}_fails", 0).putLong("${lockKey}_locked_until", 0L).apply()
            activeUser.value = match
            lastLoggedInUser.value = match.username
            prefs.edit().putString("last_logged_in_user", match.username).apply()
            playBeep()
            onResult(true, "Switched to ${match.username.uppercase()} (${match.role})", match)
        } else {
            val fails = prefs.getInt("${lockKey}_fails", 0) + 1
            if (fails >= 5) {
                prefs.edit().putInt("${lockKey}_fails", 0).putLong("${lockKey}_locked_until", now + 60_000L).apply()
                onResult(false, "Too many invalid staff PIN attempts. Locked for 60 seconds.", null)
            } else {
                prefs.edit().putInt("${lockKey}_fails", fails).apply()
                val remaining = 5 - fails
                onResult(false, "Invalid staff PIN. $remaining attempt${if (remaining == 1) "" else "s"} left.", null)
            }
        }
    }
}

internal fun StorePointViewModel.authSwitchCashierWithUserImpl(user: User) {
    activeUser.value = user
    lastLoggedInUser.value = user.username
    prefs.edit().putString("last_logged_in_user", user.username).apply()
    playBeep()
}

internal fun StorePointViewModel.authResetAdminPinImpl(
    adminUsername: String,
    currentPinOrRecoveryKey: String,
    newPin: String,
    onResult: (Boolean, String) -> Unit
) {
    viewModelScope.launch {
        val now = System.currentTimeMillis()
        val lockKey = "admin_pin_reset_$adminUsername"
        val lockedUntil = prefs.getLong("${lockKey}_locked_until", 0L)
        if (now < lockedUntil) {
            val remainSec = ((lockedUntil - now) / 1000).toInt().coerceAtLeast(1)
            onResult(false, "Too many failed attempts. Try again in ${remainSec}s.")
            return@launch
        }
        if (!SecurityHelper.isValidPin(newPin)) {
            onResult(false, "New PIN must be exactly 4 digits.")
            return@launch
        }
        if (SecurityHelper.isWeakPin(newPin)) {
            onResult(false, "That PIN is too easy to guess. ${SecurityHelper.pinPolicyHint()}")
            return@launch
        }
        val user = repository.getUserByUsername(adminUsername)
        if (user == null || !user.role.equals("ADMIN", ignoreCase = true)) {
            onResult(false, "Invalid Administrator username specified.")
            return@launch
        }

        val verification = SecurityHelper.verifyPinLenient(currentPinOrRecoveryKey, user.pinHash)
        if (verification.isMatch) {
            prefs.edit().putInt("${lockKey}_fails", 0).putLong("${lockKey}_locked_until", 0L).apply()
            val updatedUser = user.copy(
                pinHash = SecurityHelper.hashPin(newPin),
                pinResetRequired = false
            )
            repository.saveUser(updatedUser)
            onResult(true, "Admin PIN successfully updated!")
        } else {
            val fails = prefs.getInt("${lockKey}_fails", 0) + 1
            if (fails >= 4) {
                val window = lockoutWindowFor(fails - 4)
                prefs.edit().putInt("${lockKey}_fails", 0).putLong("${lockKey}_locked_until", now + window).apply()
                onResult(false, "Too many failed attempts. Locked for ${window / 1000} seconds.")
            } else {
                prefs.edit().putInt("${lockKey}_fails", fails).apply()
                val remaining = 4 - fails
                onResult(false, "Current Admin PIN is incorrect. $remaining attempt${if (remaining == 1) "" else "s"} left.")
            }
        }
    }
}

internal fun StorePointViewModel.authResetPinImpl(
    username: String,
    newPin: String,
    onSuccess: (User) -> Unit,
    onFailure: (String) -> Unit
) {
    viewModelScope.launch {
        if (!SecurityHelper.isValidPin(newPin)) {
            onFailure("PIN must be exactly ${SecurityHelper.PIN_LENGTH} digits.")
            return@launch
        }
        if (SecurityHelper.isWeakPin(newPin)) {
            onFailure("That PIN is too easy to guess. ${SecurityHelper.pinPolicyHint()}")
            return@launch
        }
        val user = repository.getUserByUsername(username)
        if (user == null) {
            onFailure("Account not found.")
            return@launch
        }
        val updated = user.copy(
            pinHash = SecurityHelper.hashPin(newPin),
            pinResetRequired = false
        )
        repository.updatePinAfterReset(username, updated.pinHash)
        onSuccess(updated)
    }
}

internal fun StorePointViewModel.authVerifyPinResetAuthorizationImpl(
    username: String,
    currentPin: String,
    onSuccess: (User) -> Unit,
    onFailure: (String) -> Unit
) {
    viewModelScope.launch {
        val now = System.currentTimeMillis()
        val lockKey = "pinreset_$username"
        val lockedUntil = prefs.getLong("${lockKey}_locked_until", 0L)
        if (now < lockedUntil) {
            val remainSec = ((lockedUntil - now) / 1000).toInt().coerceAtLeast(1)
            onFailure("Too many attempts. Try again in ${remainSec}s.")
            return@launch
        }

        val user = repository.getUserByUsername(username)
        if (user == null) {
            onFailure("Account not found.")
            return@launch
        }

        val verification = SecurityHelper.verifyPinLenient(currentPin, user.pinHash)
        if (verification.isMatch) {
            prefs.edit().putInt("${lockKey}_fails", 0).putLong("${lockKey}_locked_until", 0L).apply()
            onSuccess(user)
            return@launch
        }

        val fails = prefs.getInt("${lockKey}_fails", 0) + 1
        if (fails >= 4) {
            val window = lockoutWindowFor(fails - 4)
            prefs.edit()
                .putInt("${lockKey}_fails", 0)
                .putLong("${lockKey}_locked_until", now + window)
                .apply()
            onFailure("Too many attempts. Locked for ${window / 1000} seconds.")
        } else {
            val remaining = 4 - fails
            prefs.edit().putInt("${lockKey}_fails", fails).apply()
            onFailure("Current PIN incorrect. $remaining attempt${if (remaining == 1) "" else "s"} left.")
        }
    }
}

internal fun StorePointViewModel.authLoginImpl(
    username: String,
    pass: String,
    onSuccess: (User) -> Unit,
    onFailure: (String) -> Unit
) {
    viewModelScope.launch {
        val now = System.currentTimeMillis()
        val lockKey = "login_fail_$username"
        val lockedUntil = prefs.getLong("${lockKey}_locked_until", 0L)
        if (now < lockedUntil) {
            val remainSec = ((lockedUntil - now) / 1000).toInt().coerceAtLeast(1)
            onFailure("Too many failed attempts. Try again in ${remainSec}s.")
            return@launch
        }

        val user = repository.getUserByUsername(username)
        if (user != null && !user.pinResetRequired) {
            val verification = SecurityHelper.verifyPin(pass, user.pinHash)
            if (verification.isMatch) {
                prefs.edit().putInt("${lockKey}_fails", 0).putLong("${lockKey}_locked_until", 0L).apply()
                var signedIn = user
                if (verification.needsUpgrade) {
                    signedIn = user.copy(pinHash = SecurityHelper.hashPin(pass))
                    repository.saveUser(signedIn)
                }
                signedIn = authMaybeAutoEnrollBiometricImpl(signedIn)
                authCompleteLoginImpl(signedIn, onSuccess)
                return@launch
            }
        }

        val fails = prefs.getInt("${lockKey}_fails", 0) + 1
        if (fails >= 4) {
            val window = lockoutWindowFor(fails - 4)
            prefs.edit()
                .putInt("${lockKey}_fails", 0)
                .putLong("${lockKey}_locked_until", now + window)
                .apply()
            onFailure("Too many failed attempts. Locked for ${window / 1000} seconds.")
        } else {
            prefs.edit().putInt("${lockKey}_fails", fails).apply()
            val remaining = 4 - fails
            val msg = if (user?.pinResetRequired == true) {
                "This account still needs a new ${SecurityHelper.PIN_LENGTH}-digit PIN."
            } else {
                "Incorrect username or ${SecurityHelper.PIN_LENGTH}-digit PIN. " +
                    "$remaining attempt${if (remaining == 1) "" else "s"} left."
            }
            onFailure(msg)
        }
    }
}

internal suspend fun StorePointViewModel.authCompleteLoginImpl(user: User, onSuccess: (User) -> Unit) {
    activeUser.value = user
    CrashDiagnosticsManager.currentCashier = user.username
    prefs.edit().putString("last_logged_in_user", user.username).apply()
    lastLoggedInUser.value = user.username
    playBeep()
    restoreCartDraft()
    onSuccess(user)
}

internal suspend fun StorePointViewModel.authMaybeAutoEnrollBiometricImpl(user: User): User {
    if (user.biometricEnrolled) return user
    if (!isBiometricEnabled.value) return user
    if (!BiometricAuthHelper.checkAvailability(getApplication()).isAvailable) return user
    repository.setBiometricEnrolled(user.username, true)
    return user.copy(biometricEnrolled = true)
}

internal fun StorePointViewModel.authSetBiometricEnrolledForUserImpl(username: String, enrolled: Boolean) {
    viewModelScope.launch {
        repository.setBiometricEnrolled(username, enrolled)
    }
}

internal fun StorePointViewModel.authVerifyKioskUnlockImpl(
    pin: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
    lockoutKey: String
) {
    viewModelScope.launch {
        val active = adminUnlockLockout.lockoutMessage(lockoutKey)
        if (active != null) {
            onFailure(active)
            return@launch
        }

        val admins = repository.getAllUsersSync().filter { it.role.equals("ADMIN", ignoreCase = true) }
        var isValid = false
        if (pin.isNotBlank()) {
            for (admin in admins) {
                val verification = SecurityHelper.verifyPinLenient(pin, admin.passwordHash)
                if (verification.isMatch) {
                    isValid = true
                    if (verification.needsUpgrade) {
                        repository.saveUser(admin.copy(pinHash = SecurityHelper.hashPin(pin)))
                    }
                    break
                }
            }
        }

        if (isValid) {
            adminUnlockLockout.onSuccess(lockoutKey)
            onSuccess()
        } else {
            onFailure(adminUnlockLockout.onFailure(lockoutKey))
        }
    }
}

internal fun StorePointViewModel.authLoginWithBiometricImpl(
    targetUsername: String?,
    onSuccess: (User) -> Unit,
    onFailure: (String) -> Unit
) {
    viewModelScope.launch {
        val requested = targetUsername?.takeIf { it.isNotBlank() }
            ?: prefs.getString("last_logged_in_user", null)

        val user = requested?.let { repository.getUserByUsername(it) }

        if (user == null) {
            onFailure("No account found for fingerprint sign-in.")
            return@launch
        }
        if (!user.biometricEnrolled) {
            onFailure("${user.username} has not enrolled a fingerprint. Use your PIN instead.")
            return@launch
        }
        if (user.pinResetRequired) {
            onFailure("${user.username} must set a new ${SecurityHelper.PIN_LENGTH}-digit PIN first.")
            return@launch
        }

        authCompleteLoginImpl(user, onSuccess)
    }
}

internal fun StorePointViewModel.authLogoutImpl() {
    activeUser.value = null
    CrashDiagnosticsManager.currentCashier = "None"
    restoredCartLineCount = 0
}

internal fun StorePointViewModel.authSaveUserImpl(user: User) {
    viewModelScope.launch {
        repository.saveUser(user)
    }
}

internal fun StorePointViewModel.authResetUserPinImpl(
    username: String,
    newPin: String,
    onResult: (Boolean, String) -> Unit
) {
    viewModelScope.launch {
        if (!SecurityHelper.isValidPin(newPin)) {
            onResult(false, "PIN must be exactly 4 numeric digits.")
            return@launch
        }
        if (SecurityHelper.isWeakPin(newPin)) {
            onResult(false, "That PIN is too easy to guess. ${SecurityHelper.pinPolicyHint()}")
            return@launch
        }
        val user = repository.getUserByUsername(username)
        if (user != null) {
            val updatedUser = user.copy(
                pinHash = SecurityHelper.hashPin(newPin),
                pinResetRequired = false
            )
            repository.saveUser(updatedUser)
            onResult(true, "Staff PIN updated successfully!")
        } else {
            onResult(false, "User not found.")
        }
    }
}

internal fun StorePointViewModel.authDeleteUserImpl(username: String) {
    viewModelScope.launch {
        repository.deleteUser(username)
        Toast.makeText(context, "User profile removed.", Toast.LENGTH_SHORT).show()
    }
}
