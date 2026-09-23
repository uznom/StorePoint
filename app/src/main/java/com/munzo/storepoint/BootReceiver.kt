package com.munzo.storepoint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        // Only the protected system broadcast BOOT_COMPLETED is honored. The legacy
        // QUICKBOOT/HTC quick-boot actions were removed from both this check and the
        // manifest: they are NOT protected broadcasts, so any third-party app could
        // spoof them to force-launch the kiosk activity (audit M2).
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            
            val prefs = context.getSharedPreferences("storepoint_sys_prefs", Context.MODE_PRIVATE)
            val isKioskActive = prefs.getBoolean("is_kiosk_mode_active", false)
            
            if (isKioskActive) {
                try {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Failed to start MainActivity from boot broadcast", e)
                }
            }
        }
    }
}
