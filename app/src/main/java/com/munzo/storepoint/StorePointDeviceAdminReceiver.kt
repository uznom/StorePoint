package com.munzo.storepoint

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.widget.Toast

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

        fun applyTamperProtections(context: Context) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
                val admin = getComponentName(context)
                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    // Prevent uninstalling the POS app
                    dpm.setUninstallBlocked(admin, context.packageName, true)
                    // Prevent clearing data, clearing cache, or force stopping in Android Settings
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
                    // Whitelist app for True Lock Task (kiosk)
                    dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
                }
            } catch (e: Throwable) {
                android.util.Log.e("DeviceAdminReceiver", "Could not apply tamper protections: ${e.message}", e)
            }
        }
    }
}

