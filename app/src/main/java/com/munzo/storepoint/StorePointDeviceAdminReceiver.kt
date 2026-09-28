package com.munzo.storepoint

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.widget.Toast

/**
 * How much of the terminal lockdown StorePoint can actually enforce right now.
 *
 * Ordered from strongest to weakest so callers can compare with `>=`.
 */
enum class LockdownStrength {
    /**
     * StorePoint is the **device owner**. `startLockTask()` engages true, non-dismissable
     * LockTask, and the tamper protections (uninstall block, app-control restrictions,
     * lock-task allowlist) are genuinely applied. This is the only state in which
     * "kiosk lockdown" means what an owner expects it to mean.
     */
    DeviceOwnerEnforced,

    /**
     * Device administrator is active but StorePoint is **not** the device owner.
     *
     * The real restrictions are all gated behind `isDeviceOwnerApp()` and are therefore
     * silently skipped, while `startLockTask()` degrades to legacy **screen pinning** -
     * dismissable with the Back + Overview gesture, and announced by a system toast the
     * user can simply read past. The terminal is *not* secured, despite device admin
     * reporting as "on".
     */
    ScreenPinningOnly,

    /**
     * No device admin at all. Nothing is enforced; kiosk mode is cosmetic.
     */
    Unprotected
}

/**
 * Single source of truth for "can this terminal actually enforce lockdown?" (issue #4).
 *
 * The bug this fixes: the Security tab derived its status from `isAdminActive()` alone
 * and reported "DEVICE PROTECTION ON", so a terminal that was merely a device *admin*
 * was presented as protected - even though every real restriction sits behind a
 * `isDeviceOwnerApp()` branch that never executed.
 */
object KioskLockdownCapability {

    /**
     * Classifies lockdown strength from the two independent facts.
     *
     * Pure so the tier decision is unit tested rather than eyeballed on a device - the
     * conflation it guards against is invisible in review precisely because both
     * booleans are individually plausible.
     */
    fun classify(isDeviceAdminActive: Boolean, isDeviceOwner: Boolean): LockdownStrength = when {
        // Device owner implies device admin; checked first so the strongest state wins
        // even if a caller reports them inconsistently.
        isDeviceOwner -> LockdownStrength.DeviceOwnerEnforced
        isDeviceAdminActive -> LockdownStrength.ScreenPinningOnly
        else -> LockdownStrength.Unprotected
    }

    /** True only when lockdown is genuinely enforced, not merely advisory. */
    fun isEnforced(strength: LockdownStrength): Boolean =
        strength == LockdownStrength.DeviceOwnerEnforced

    /** Reads the live capability of this terminal. */
    fun current(context: Context): LockdownStrength {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return LockdownStrength.Unprotected
        return classify(
            isDeviceAdminActive = dpm.isAdminActive(StorePointDeviceAdminReceiver.getComponentName(context)),
            isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
        )
    }

    /**
     * Human-readable explanation, shown to the owner so a degraded terminal is never
     * mistaken for a secured one.
     */
    fun explanation(strength: LockdownStrength): String = when (strength) {
        LockdownStrength.DeviceOwnerEnforced ->
            "StorePoint is the device owner. Kiosk lockdown is genuinely enforced: the user " +
                "cannot leave the app, uninstall it, clear its data, or reach Settings."

        LockdownStrength.ScreenPinningOnly ->
            "Device Administrator is ON, but StorePoint is NOT the device owner. Kiosk " +
                "lockdown is running in reduced screen-pinning mode, which the user can " +
                "dismiss with Back + Overview, and uninstall/data-clearing are NOT blocked. " +
                "Do not rely on this terminal being secured until it is provisioned as " +
                "device owner."

        LockdownStrength.Unprotected ->
            "No device protection is active. StorePoint cannot lock, wipe, or block " +
                "uninstallation if the terminal is lost, stolen, or tampered with."
    }

    /** Short label for status chips. */
    fun label(strength: LockdownStrength): String = when (strength) {
        LockdownStrength.DeviceOwnerEnforced -> "FULL TERMINAL LOCKDOWN"
        LockdownStrength.ScreenPinningOnly -> "REDUCED - SCREEN PINNING ONLY"
        LockdownStrength.Unprotected -> "PROTECTION OFF"
    }
}

class StorePointDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "StorePoint Security: Device Administrator active.", Toast.LENGTH_LONG).show()
        applyTamperProtections(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "StorePoint Security: Device Administrator deactivated.", Toast.LENGTH_LONG).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "SECURITY RESTRICTION: Deactivating StorePoint Device Administration will unlock the POS kiosk, " +
            "allow unauthorized uninstallation, and expose store transactions and inventory to data tampering."
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, StorePointDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            return dpm.isAdminActive(getComponentName(context))
        }

        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            return dpm.isDeviceOwnerApp(context.packageName)
        }

        /**
         * Applies the tamper protections that actually restrict the terminal.
         *
         * SECURITY (issue #4): every restriction here is only honoured when StorePoint
         * is the **device owner**. On a merely-administered terminal this method used
         * to do nothing at all and return silently, so the app went on reporting
         * "DEVICE PROTECTION ON" while none of it applied. It now logs and returns the
         * actual outcome so the UI can present a truthful status.
         *
         * @return the resulting lockdown strength, so callers can warn the owner.
         */
        fun applyTamperProtections(context: Context): LockdownStrength {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: return LockdownStrength.Unprotected
            val admin = getComponentName(context)

            if (!dpm.isDeviceOwnerApp(context.packageName)) {
                android.util.Log.w(
                    "DeviceAdminReceiver",
                    "Device Administrator is active but StorePoint is not the device owner. " +
                        "Uninstall blocking, app-control restrictions and true LockTask CANNOT be " +
                        "applied; kiosk lockdown degrades to dismissable screen pinning."
                )
                return KioskLockdownCapability.classify(
                    isDeviceAdminActive = dpm.isAdminActive(admin),
                    isDeviceOwner = false
                )
            }

            return try {
                // Prevent uninstalling the POS app
                dpm.setUninstallBlocked(admin, context.packageName, true)
                // Prevent clearing data, clearing cache, or force stopping in Settings
                dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
                // Whitelist app for True Lock Task (kiosk)
                dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    // Close the status-bar pull as an escape hatch while locked down.
                    dpm.setStatusBarDisabled(admin, true)
                }
                android.util.Log.i("DeviceAdminReceiver", "Tamper protections applied as device owner.")
                LockdownStrength.DeviceOwnerEnforced
            } catch (e: Throwable) {
                // A partial application (e.g. uninstall blocked but lock-task packages
                // rejected) must never be reported as full enforcement.
                android.util.Log.e(
                    "DeviceAdminReceiver",
                    "Could not apply all tamper protections: ${e.message}", e
                )
                LockdownStrength.ScreenPinningOnly
            }
        }
    }
}

