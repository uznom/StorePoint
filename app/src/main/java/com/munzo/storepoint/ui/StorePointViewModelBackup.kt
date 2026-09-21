package com.munzo.storepoint.ui

import androidx.lifecycle.viewModelScope
import com.munzo.storepoint.util.CrashDiagnosticsManager
import com.munzo.storepoint.util.DatabaseBackupManager
import com.munzo.storepoint.util.SecurityHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// --- System Observability & Crash Diagnostics ---
internal fun StorePointViewModel.backupRefreshCrashLogsImpl() {
    crashLogs.value = CrashDiagnosticsManager.getCrashLogs(context)
}

internal fun StorePointViewModel.backupClearCrashLogsImpl() {
    CrashDiagnosticsManager.clearCrashLogs(context)
    backupRefreshCrashLogsImpl()
}

internal fun StorePointViewModel.backupGetDiagnosticReportImpl(): String {
    return CrashDiagnosticsManager.generateDiagnosticsReport(context)
}

// --- Disaster Recovery & Database Backup ---
internal fun StorePointViewModel.backupRefreshBackupSnapshotsImpl() {
    backupSnapshots.value = DatabaseBackupManager.listLocalSnapshots(context)
}

internal fun StorePointViewModel.backupCheckAndPerformDailyAutoBackupImpl() {
    val lastAutoBackupDate = prefs.getString("last_auto_backup_date", "")
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    if (lastAutoBackupDate != today) {
        viewModelScope.launch {
            try {
                DatabaseBackupManager.autoCreateLocalSnapshot(context, db)
                prefs.edit().putString("last_auto_backup_date", today).apply()
                backupRefreshBackupSnapshotsImpl()
                android.util.Log.d("StorePointViewModel", "Automated daily safety snapshot created for $today")
            } catch (e: Exception) {
                android.util.Log.e("StorePointViewModel", "Failed to create automated daily snapshot", e)
            }
        }
    }
}

internal fun StorePointViewModel.backupExportDatabaseBackupImpl(onCompleted: (File?) -> Unit) {
    backupExportDatabaseBackupImpl(
        passphrase = "",
        onSuccess = { file ->
            backupRefreshBackupSnapshotsImpl()
            onCompleted(file)
        },
        onFailure = {
            onCompleted(null)
        }
    )
}

internal fun StorePointViewModel.backupExportDatabaseBackupImpl(passphrase: String, onCompleted: (File?) -> Unit) {
    backupExportDatabaseBackupImpl(
        passphrase = passphrase,
        onSuccess = { file ->
            backupRefreshBackupSnapshotsImpl()
            onCompleted(file)
        },
        onFailure = {
            onCompleted(null)
        }
    )
}

internal fun StorePointViewModel.backupExportDatabaseBackupImpl(onSuccess: (File) -> Unit, onFailure: (String) -> Unit) {
    backupExportDatabaseBackupImpl(passphrase = "", onSuccess = onSuccess, onFailure = onFailure)
}

internal fun StorePointViewModel.backupExportDatabaseBackupImpl(passphrase: String, onSuccess: (File) -> Unit, onFailure: (String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            var json = DatabaseBackupManager.exportBackupJson(db)
            if (passphrase.isNotEmpty()) {
                DatabaseBackupManager.encryptBackupJson(json, passphrase)
                    .onSuccess { envelope -> json = envelope }
                    .onFailure { err -> throw Exception(err.localizedMessage ?: "Encryption failed.") }
            }
            val file = DatabaseBackupManager.saveBackupToFile(context, json)
            backupRefreshBackupSnapshotsImpl()
            onSuccess(file)
        } catch (e: Exception) {
            onFailure("Database backup export failed: ${e.localizedMessage}")
        }
    }
}

internal fun StorePointViewModel.backupRestoreDatabaseBackupImpl(jsonContent: String, passphrase: String = "", onResult: (DatabaseBackupManager.RestoreSummary) -> Unit) {
    viewModelScope.launch {
        // M2: transparently decrypt passphrase-protected backups before import.
        var finalJson = jsonContent
        if (jsonContent.trimStart().startsWith("{")) {
            try {
                val probe = JSONObject(jsonContent)
                if (probe.optInt("enc", 0) == 1) {
                    val decrypted = DatabaseBackupManager.decryptBackupJson(jsonContent, passphrase)
                    decrypted
                        .onSuccess { plain -> finalJson = plain }
                        .onFailure { err ->
                            onResult(DatabaseBackupManager.RestoreSummary(false, err.localizedMessage ?: "Backup decryption failed."))
                            return@launch
                        }
                }
            } catch (_: Exception) {
                // Not an encrypted envelope — fall through and treat as a plain backup.
            }
        }

        val summary = DatabaseBackupManager.importAndRestoreBackupJson(context, db, finalJson)
        if (summary.success) {
            DatabaseBackupManager.autoCreateLocalSnapshot(context, db)
            backupRefreshBackupSnapshotsImpl()
        }
        onResult(summary)
    }
}

internal fun StorePointViewModel.backupRestoreFromPersistentBackupImpl(
    file: File,
    onResult: (DatabaseBackupManager.RestoreSummary) -> Unit
) {
    viewModelScope.launch {
        try {
            val content = withContext(Dispatchers.IO) {
                file.readText(Charsets.UTF_8)
            }
            val summary = DatabaseBackupManager.importAndRestoreBackupJson(context, db, content)
            if (summary.success) {
                val cfg = withContext(Dispatchers.IO) {
                    db.storeConfigDao.getStoreConfigSync()
                }
                if (cfg != null && cfg.setupCompleted) {
                    setOnboardingCompleted(true)
                    val admins = withContext(Dispatchers.IO) {
                        db.userDao.getAllUsersSync().filter { it.role == "ADMIN" }
                    }
                    if (admins.isNotEmpty()) {
                        activeUser.value = admins.first()
                    }
                }
                DatabaseBackupManager.savePersistentSnapshot(context, db)
                backupRefreshBackupSnapshotsImpl()
            }
            onResult(summary)
        } catch (e: Exception) {
            onResult(DatabaseBackupManager.RestoreSummary(false, "Failed to restore backup: ${e.localizedMessage}"))
        }
    }
}

internal fun StorePointViewModel.backupClearAllDatabaseDataImpl(adminPass: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
    viewModelScope.launch {
        try {
            val isAuthorized = withContext(Dispatchers.IO) {
                val dbAdmins = repository.getAllUsersSync().filter { it.role == "ADMIN" }
                val active = activeUser.value
                if (active != null && active.role == "ADMIN" && SecurityHelper.verifyPin(adminPass, active.pinHash).isMatch) {
                    true
                } else {
                    dbAdmins.any { SecurityHelper.verifyPin(adminPass, it.pinHash).isMatch }
                }
            }

            if (isAuthorized) {
                withContext(Dispatchers.IO) {
                    // Clear all database tables
                    db.clearAllTables()
                    
                    // Repopulate setup templates
                    repository.prePopulateData()
                }
                
                // Clear all shared preferences
                prefs.edit().clear().apply()
                isAlwaysOnEnabled.value = false
                kioskPin.value = ""
                isOnboardingCompleted.value = false
                isKioskModeActive.value = false
                
                // Reset ViewModel session/user state
                activeUser.value = null
                clearCart()
                
                onSuccess()
            } else {
                onFailure("Invalid admin 6-digit PIN.")
            }
        } catch (e: Exception) {
            android.util.Log.e("StorePointViewModel", "Error wiping data", e)
            onFailure(e.localizedMessage ?: "Failed to clean database.")
        }
    }
}
