package com.munzo.storepoint

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Enterprise Device Administrator Receiver enabling dedicated POS Kiosk Mode lockdown
 * (`adb shell dpm set-device-owner com.munzo.storepoint/.StorePointDeviceAdminReceiver`).
 */
class StorePointDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
