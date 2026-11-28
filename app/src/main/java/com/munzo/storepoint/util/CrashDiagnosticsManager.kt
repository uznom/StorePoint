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
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                recordCrash(appContext, throwable)
            } catch (e: Exception) {
                android.util.Log.e("CrashDiagnostics", "Failed saving crash log", e)
            }
            try {
                StartupBootGuard.recordUncaughtCrash(appContext, thread.name, throwable)
            } catch (e: Exception) {
                android.util.Log.e("CrashDiagnostics", "Failed updating startup boot guard", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun logException(context: Context, throwable: Throwable) {
        recordCrash(context, throwable)
    }

    fun recordCrashToFile(file: File, throwable: Throwable, cashier: String = currentCashier): CrashRecord {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val devManufacturer = try { Build.MANUFACTURER ?: "Generic" } catch (_: Throwable) { "Generic" }
        val devModel = try { Build.MODEL ?: "Terminal" } catch (_: Throwable) { "Terminal" }
        val devSdk = try { Build.VERSION.SDK_INT } catch (_: Throwable) { 0 }
        val devRelease = try { Build.VERSION.RELEASE ?: "" } catch (_: Throwable) { "" }
        val deviceInfo = "Model: $devManufacturer $devModel, SDK: $devSdk (Android $devRelease)"

        val record = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("dateFormatted", formatDate(Date()))
            put("exceptionName", throwable.javaClass.simpleName)
            put("message", throwable.localizedMessage ?: "No error message provided")
            put("stackTrace", stackTrace)
            put("deviceInfo", deviceInfo)
            put("activeCashier", cashier)
        }

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

        return CrashRecord(
            timestamp = record.getLong("timestamp"),
            dateFormatted = record.getString("dateFormatted"),
            exceptionName = record.getString("exceptionName"),
            message = record.getString("message"),
            stackTrace = stackTrace,
            deviceInfo = deviceInfo,
            activeCashier = cashier
        )
    }

    private fun recordCrash(context: Context, throwable: Throwable) {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        recordCrashToFile(file, throwable, currentCashier)
    }

    fun parseCrashLogs(file: File): List<CrashRecord> {
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

    fun getCrashLogs(context: Context): List<CrashRecord> {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        return parseCrashLogs(file)
    }

    fun clearCrashLogs(context: Context) {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }

    fun formatDiagnosticsReport(
        deviceManufacturer: String = "Generic",
        deviceModel: String = "POS Terminal",
        deviceCode: String = "generic",
        androidRelease: String = "14",
        apiLevel: Int = 34,
        fingerprint: String = "generic/storepoint/pos",
        ramAvailMb: Long = 2048,
        ramTotalMb: Long = 4096,
        isLowMemory: Boolean = false,
        storageFreeMb: Long = 10240,
        crashes: List<CrashRecord> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.append("=== STOREPOINT POS SYSTEM DIAGNOSTICS ===\n")
        sb.append("Generated: ${formatDate(Date())}\n\n")

        // Hardware & OS
        sb.append("--- HARDWARE & OS ---\n")
        sb.append("Device: $deviceManufacturer $deviceModel ($deviceCode)\n")
        sb.append("Android Version: $androidRelease (API $apiLevel)\n")
        sb.append("Build Fingerprint: $fingerprint\n\n")

        // Memory & Storage
        sb.append("--- MEMORY & STORAGE ---\n")
        sb.append("RAM: $ramAvailMb MB available / $ramTotalMb MB total (Low Mem: $isLowMemory)\n")
        sb.append("Internal App Storage Free: $storageFreeMb MB\n\n")

        // Recent Crashes
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

    fun generateDiagnosticsReport(context: Context): String {
        var devManufacturer = "Generic"
        var devModel = "POS Terminal"
        var devCode = "generic"
        var androidRelease = "14"
        var apiLevel = 34
        var fingerprint = "generic/storepoint/pos"

        try {
            devManufacturer = Build.MANUFACTURER ?: "Generic"
            devModel = Build.MODEL ?: "POS Terminal"
            devCode = Build.DEVICE ?: "generic"
            androidRelease = Build.VERSION.RELEASE ?: "14"
            apiLevel = Build.VERSION.SDK_INT
            fingerprint = Build.FINGERPRINT ?: "generic/storepoint/pos"
        } catch (_: Throwable) {}

        var ramAvailMb = 2048L
        var ramTotalMb = 4096L
        var isLowMem = false

        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (actManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                ramAvailMb = memInfo.availMem / (1024 * 1024)
                ramTotalMb = memInfo.totalMem / (1024 * 1024)
                isLowMem = memInfo.lowMemory
            }
        } catch (_: Throwable) {}

        var storageFreeMb = 0L
        try {
            val stat = StatFs(context.filesDir.path)
            storageFreeMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
        } catch (_: Throwable) {}

        val crashes = getCrashLogs(context)

        return formatDiagnosticsReport(
            deviceManufacturer = devManufacturer,
            deviceModel = devModel,
            deviceCode = devCode,
            androidRelease = androidRelease,
            apiLevel = apiLevel,
            fingerprint = fingerprint,
            ramAvailMb = ramAvailMb,
            ramTotalMb = ramTotalMb,
            isLowMemory = isLowMem,
            storageFreeMb = storageFreeMb,
            crashes = crashes
        )
    }

    fun formatSingleCrashReport(record: CrashRecord): String = buildString {
        appendLine("StorePoint POS Crash Record")
        appendLine("Timestamp: ${record.dateFormatted}")
        appendLine("Exception: ${record.exceptionName}")
        appendLine("Message: ${record.message}")
        appendLine("Device: ${record.deviceInfo}")
        appendLine("Active Cashier: ${record.activeCashier}")
        appendLine()
        appendLine("--- STACK TRACE ---")
        append(record.stackTrace)
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
