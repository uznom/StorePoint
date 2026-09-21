package com.munzo.storepoint.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import com.munzo.storepoint.data.TransactionItem

data class PendingDigitalService(
    val rawName: String,
    val service: String,        // "GCash", "Maya", "Load"
    val action: String,         // "Cash In", "Cash Out", "Top-Up"
    val amount: Double,
    val fee: Double,
    val mobileNumber: String,
    val network: String,        // "Smart", "TNT", "Globe", "TM"
    val bankFee: Double = 0.0
)

object DigitalServicesHelper {

    const val PREFS_NAME = "storepoint_admin_prefs"
    const val KEY_ALLOWED_APPS = "allowed_terminal_apps"
    const val KEY_PREFERRED_LOAD_APP = "admin_preferred_load_app"

    val DEFAULT_ALLOWED_APPS = setOf(
        "com.globe.gcash.android",
        "com.paymaya",
        "com.android.stk"
    )

    private val DIGITAL_REGEX = Regex(
        """^(GCash|Maya|Load)\s+(Cash In|Cash Out|Top-Up)(?:\s*\(([^\)]+)\))?(?:\s+for\s+([0-9+]+))?(?:\s*\(Amt:\s*([0-9.]+),\s*Fee:\s*([0-9.]+)(?:,\s*BankFee:\s*([0-9.]+))?\))?""",
        RegexOption.IGNORE_CASE
    )

    fun parseItem(productName: String): PendingDigitalService? {
        val trimmed = productName.trim()
        val match = DIGITAL_REGEX.find(trimmed)
        if (match != null) {
            val service = match.groupValues[1].replaceFirstChar { it.uppercase() }
            val action = match.groupValues[2]
            val network = match.groupValues[3].ifBlank { "" }
            val mobileNumber = match.groupValues[4].ifBlank { "" }
            val amount = match.groupValues[5].toDoubleOrNull() ?: 0.0
            val fee = match.groupValues[6].toDoubleOrNull() ?: 0.0
            val bankFee = match.groupValues.getOrNull(7)?.toDoubleOrNull() ?: 0.0
            return PendingDigitalService(
                rawName = productName,
                service = service,
                action = action,
                amount = amount,
                fee = fee,
                mobileNumber = mobileNumber,
                network = network,
                bankFee = bankFee
            )
        }

        // Fallback detection for custom or legacy digital service names
        val isGCash = trimmed.contains("GCash", ignoreCase = true)
        val isMaya = trimmed.contains("Maya", ignoreCase = true)
        val isLoad = trimmed.contains("Load", ignoreCase = true)

        if (!isGCash && !isMaya && !isLoad) return null

        val service = when {
            isGCash -> "GCash"
            isMaya -> "Maya"
            else -> "Load"
        }
        val action = when {
            trimmed.contains("Cash Out", ignoreCase = true) -> "Cash Out"
            trimmed.contains("Top-Up", ignoreCase = true) -> "Top-Up"
            else -> "Cash In"
        }
        val numMatch = Regex("""(09\d{9}|\+639\d{9})""").find(trimmed)
        val mobileNumber = numMatch?.value ?: ""

        val amtMatch = Regex("""Amt:\s*([0-9.]+)""", RegexOption.IGNORE_CASE).find(trimmed)
        val amount = amtMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        val feeMatch = Regex("""Fee:\s*([0-9.]+)""", RegexOption.IGNORE_CASE).find(trimmed)
        val fee = feeMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        val netMatch = listOf("Smart", "TNT", "Globe", "TM").firstOrNull { trimmed.contains(it, ignoreCase = true) } ?: ""

        return PendingDigitalService(
            rawName = productName,
            service = service,
            action = action,
            amount = amount,
            fee = fee,
            mobileNumber = mobileNumber,
            network = netMatch
        )
    }

    fun parseDigitalServiceItems(items: List<TransactionItem>): List<PendingDigitalService> {
        return items.mapNotNull { parseItem(it.productName) }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied: $text", Toast.LENGTH_SHORT).show()
    }

    fun launchGCash(context: Context) {
        val pkg = "com.globe.gcash.android"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open GCash: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "GCash app not found. Launching Google Play Store...", Toast.LENGTH_SHORT).show()
            try {
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playIntent)
            } catch (e: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    fun launchMaya(context: Context) {
        val pkg = "com.paymaya"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open Maya: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Maya app not found. Launching Google Play Store...", Toast.LENGTH_SHORT).show()
            try {
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playIntent)
            } catch (e: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    fun launchDialer(context: Context, ussdCode: String) {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(ussdCode))).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(dialIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot launch dialer: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchSimToolkit(context: Context) {
        val stkPkg = "com.android.stk"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(stkPkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open SIM Toolkit: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "SIM Toolkit not available on this device or SIM.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchAppByPackage(context: Context, packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot launch app: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "App not installed on device.", Toast.LENGTH_SHORT).show()
        }
    }

    fun getAllowedApps(prefs: SharedPreferences): Set<String> {
        val saved = prefs.getString(KEY_ALLOWED_APPS, null)
        return if (saved.isNullOrBlank()) {
            DEFAULT_ALLOWED_APPS
        } else {
            saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    fun saveAllowedApps(prefs: SharedPreferences, apps: Set<String>) {
        prefs.edit().putString(KEY_ALLOWED_APPS, apps.joinToString(",")).apply()
    }

    fun getPreferredLoadApp(prefs: SharedPreferences): String {
        return prefs.getString(KEY_PREFERRED_LOAD_APP, "") ?: ""
    }

    fun savePreferredLoadApp(prefs: SharedPreferences, pkg: String) {
        prefs.edit().putString(KEY_PREFERRED_LOAD_APP, pkg.trim()).apply()
    }
}
