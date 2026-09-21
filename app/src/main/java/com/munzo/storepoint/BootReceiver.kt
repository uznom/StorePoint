package com.munzo.storepoint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            
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
