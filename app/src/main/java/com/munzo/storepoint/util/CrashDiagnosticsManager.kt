package com.munzo.storepoint.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashDiagnosticsManager {

    data class CrashRecord(
        val timestamp: Long,
        val dateFormatted: String,
        val exceptionName: String,
        val message: String,
        val stackTrace: String,
        val deviceInfo: String,
        val activeCashier: String = "None"
    )

    private const val MAX_CRASH_LOGS = 30
    private const val CRASH_FILE_NAME = "crash_logs.json"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private fun formatDate(date: Date): String = synchronized(dateFormat) {
        dateFormat.format(date)
    }

    @Volatile
    var currentCashier: String = "None"

    fun initCrashHandler(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(context, throwable)
            } catch (e: Exception) {
                android.util.Log.e("CrashDiagnostics", "Failed saving crash log", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun recordCrash(context: Context, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val deviceInfo = "Model: ${Build.MANUFACTURER} ${Build.MODEL}, SDK: ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})"

        val record = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("dateFormatted", formatDate(Date()))
            put("exceptionName", throwable.javaClass.simpleName)
            put("message", throwable.localizedMessage ?: "No error message provided")
            put("stackTrace", stackTrace)
            put("deviceInfo", deviceInfo)
            put("activeCashier", currentCashier)
        }

        val file = File(context.filesDir, CRASH_FILE_NAME)
        val array = if (file.exists()) {
            try {
                JSONArray(file.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }

        array.put(record)

        // Keep only the most recent MAX_CRASH_LOGS
        val trimmedArray = JSONArray()
        val startIdx = (array.length() - MAX_CRASH_LOGS).coerceAtLeast(0)
        for (i in startIdx until array.length()) {
            trimmedArray.put(array.get(i))
        }

        file.writeText(trimmedArray.toString(2), Charsets.UTF_8)
    }

    fun getCrashLogs(context: Context): List<CrashRecord> {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            val list = mutableListOf<CrashRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CrashRecord(
                        timestamp = obj.optLong("timestamp"),
                        dateFormatted = obj.optString("dateFormatted"),
                        exceptionName = obj.optString("exceptionName"),
                        message = obj.optString("message"),
                        stackTrace = obj.optString("stackTrace"),
                        deviceInfo = obj.optString("deviceInfo"),
                        activeCashier = obj.optString("activeCashier", "None")
                    )
                )
            }
            list.reversed()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearCrashLogs(context: Context) {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }

    fun generateDiagnosticsReport(context: Context): String {
        val sb = StringBuilder()
        sb.append("=== STOREPOINT POS SYSTEM DIAGNOSTICS ===\n")
        sb.append("Generated: ${formatDate(Date())}\n\n")

        // Hardware & OS
        sb.append("--- HARDWARE & OS ---\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
        sb.append("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Build Fingerprint: ${Build.FINGERPRINT}\n\n")

        // Memory
        sb.append("--- MEMORY & STORAGE ---\n")
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (actManager != null) {
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val availMb = memInfo.availMem / (1024 * 1024)
            val totalMb = memInfo.totalMem / (1024 * 1024)
            sb.append("RAM: $availMb MB available / $totalMb MB total (Low Mem: ${memInfo.lowMemory})\n")
        }

        try {
            val stat = StatFs(context.filesDir.path)
            val availStorageMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
            sb.append("Internal App Storage Free: $availStorageMb MB\n\n")
        } catch (e: Exception) {
            sb.append("Storage info unavailable\n\n")
        }

        // Recent Crashes
        val crashes = getCrashLogs(context)
        sb.append("--- CRASH HISTORY (${crashes.size} incidents recorded) ---\n")
        if (crashes.isEmpty()) {
            sb.append("No uncaught exceptions logged. System health is optimal.\n")
        } else {
            crashes.forEachIndexed { index, c ->
                sb.append("[$index] ${c.dateFormatted} | ${c.exceptionName}\n")
                sb.append("Message: ${c.message}\n")
                sb.append("Active Cashier: ${c.activeCashier}\n")
                sb.append("Stack Trace: ${c.stackTrace.take(350)}...\n")
                sb.append("------------------------------------------\n")
            }
        }

        return sb.toString()
    }

    fun shareDiagnosticsReport(context: Context) {
        val report = generateDiagnosticsReport(context)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "StorePoint POS Diagnostics Report")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "Share Diagnostics Report"))
    }
}
