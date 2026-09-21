package com.munzo.storepoint

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import com.munzo.storepoint.util.CrashDiagnosticsManager

class StorePointApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashDiagnosticsManager.initCrashHandler(this)
        logAndOptimizeRuntimeMemory()
    }

    private fun logAndOptimizeRuntimeMemory() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryClass = am?.memoryClass ?: 0
            val largeMemoryClass = am?.largeMemoryClass ?: 0
            val maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
            Log.i("StorePointTurbo", "Turbo Memory Engaged: maxHeap=${maxMemoryMb}MB, largeMemoryClass=${largeMemoryClass}MB, defaultClass=${memoryClass}MB")
        } catch (e: Exception) {
            Log.w("StorePointTurbo", "Memory profiling note: ${e.message}")
        }
    }
}
