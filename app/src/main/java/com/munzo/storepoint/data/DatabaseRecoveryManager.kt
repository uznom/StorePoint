package com.munzo.storepoint.data

import android.content.Context
import android.util.Log
import com.munzo.storepoint.util.CrashDiagnosticsManager
import com.munzo.storepoint.util.DatabaseBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Preflight health probe and self-healing quarantine engine for StorePoint's SQLite database.
 *
 * When a terminal's local SQLite file is corrupted (e.g. sudden power loss mid-write) or fails
 * Room's schema integrity verification, this manager safely moves the damaged database and WAL
 * sidecars into a timestamped internal quarantine directory, initializes a clean v20 database,
 * and returns a [DatabaseRecoveryPromptState] so the operator can restore their latest backup
 * or start fresh instead of suffering an infinite crash loop.
 */
object DatabaseRecoveryManager {

    private const val TAG = "DatabaseRecovery"
    private const val QUARANTINE_ROOT_NAME = "db_quarantine"
    private const val MAX_QUARANTINE_FOLDERS = 3

    private val folderDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    data class DatabaseQuarantineResult(
        val quarantinedDir: File?,
        val movedFiles: List<String>,
        val reason: String,
        val timestampMs: Long
    )

    data class DatabaseRecoveryPromptState(
        val reason: String,
        val quarantinePath: String?,
        val latestBackup: DatabaseBackupManager.BackupMetadata?,
        val availableBackups: List<DatabaseBackupManager.BackupMetadata>
    )

    private fun formatFolderTimestamp(nowMs: Long = System.currentTimeMillis()): String =
        synchronized(folderDateFormat) { folderDateFormat.format(Date(nowMs)) }

    /**
     * Pure file-based quarantine helper (unit-testable on JVM without Android framework).
     * Moves the primary SQLite file plus `-wal`, `-shm`, and `-journal` sidecars into
     * `<quarantineRoot>/quarantine_<timestampLabel>/` and prunes older quarantine folders
     * beyond [maxRetained].
     */
    fun quarantineDatabaseFiles(
        dbFile: File,
        quarantineRoot: File,
        timestampLabel: String = formatFolderTimestamp(),
        maxRetained: Int = MAX_QUARANTINE_FOLDERS
    ): DatabaseQuarantineResult {
        val now = System.currentTimeMillis()
        val moved = mutableListOf<String>()
        val parentDir = dbFile.parentFile
        val baseName = dbFile.name
        val candidates = listOf(
            dbFile,
            File(parentDir, "$baseName-wal"),
            File(parentDir, "$baseName-shm"),
            File(parentDir, "$baseName-journal")
        )

        val existing = candidates.filter { it.exists() }
        if (existing.isEmpty()) {
            return DatabaseQuarantineResult(
                quarantinedDir = null,
                movedFiles = emptyList(),
                reason = "No existing database files found to quarantine.",
                timestampMs = now
            )
        }

        if (!quarantineRoot.exists()) {
            quarantineRoot.mkdirs()
        }
        val targetDir = File(quarantineRoot, "quarantine_$timestampLabel").apply { mkdirs() }

        for (src in existing) {
            val dst = File(targetDir, src.name)
            val renamed = try {
                src.renameTo(dst)
            } catch (_: Exception) {
                false
            }
            if (renamed) {
                moved.add(src.name)
            } else {
                try {
                    src.copyTo(dst, overwrite = true)
                    src.delete()
                    moved.add(src.name)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed moving ${src.name} to quarantine: ${e.message}")
                    try {
                        src.delete()
                    } catch (_: Exception) {}
                }
            }
        }

        pruneOldQuarantineFolders(quarantineRoot, maxRetained)

        return DatabaseQuarantineResult(
            quarantinedDir = targetDir,
            movedFiles = moved,
            reason = "Quarantined ${moved.size} database file(s).",
            timestampMs = now
        )
    }

    internal fun pruneOldQuarantineFolders(quarantineRoot: File, maxRetained: Int = MAX_QUARANTINE_FOLDERS) {
        try {
            val dirs = quarantineRoot.listFiles { f -> f.isDirectory && f.name.startsWith("quarantine_") }
                ?.sortedByDescending { it.name }
                ?: return
            if (dirs.size > maxRetained) {
                dirs.drop(maxRetained).forEach { oldDir ->
                    oldDir.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Quarantine pruning skipped: ${e.message}")
        }
    }

    /**
     * Runs on [Dispatchers.IO] BEFORE [com.munzo.storepoint.ui.StorePointViewModel] is created.
     *
     * Returns `null` if the local database opened cleanly and passed integrity verification.
     * Returns a [DatabaseRecoveryPromptState] if corruption/open failure occurred and the database
     * was automatically quarantined and reset to a fresh healthy instance.
     */
    suspend fun verifyOrRecoverDatabase(context: Context): DatabaseRecoveryPromptState? =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val probeError = probeDatabaseHealth(appContext)
            if (probeError == null) {
                return@withContext null
            }

            Log.e(TAG, "Database preflight failed ($probeError); initiating safe quarantine & auto-reset.")
            val quarantine = quarantineAndResetDatabase(appContext, probeError)
            buildRecoveryPromptState(
                context = appContext,
                reason = probeError,
                quarantinePath = quarantine.quarantinedDir?.absolutePath
            )
        }

    /**
     * Explicit quarantine + clean database reset (also callable from Safe Mode).
     */
    suspend fun quarantineAndResetDatabase(context: Context, reason: String): DatabaseQuarantineResult =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            AppDatabase.resetInstance()

            val dbFile = appContext.getDatabasePath(AppDatabase.DATABASE_NAME)
            val quarantineRoot = File(appContext.filesDir, QUARANTINE_ROOT_NAME)
            val result = quarantineDatabaseFiles(dbFile, quarantineRoot)

            // Re-open fresh Room v20 database and verify it accepts queries cleanly.
            val freshDb = AppDatabase.getDatabase(appContext)
            freshDb.openHelper.writableDatabase.query("SELECT 1").use { it.moveToFirst() }
            result.copy(reason = reason)
        }

    fun buildRecoveryPromptState(
        context: Context,
        reason: String,
        quarantinePath: String?
    ): DatabaseRecoveryPromptState {
        val snapshots = try {
            DatabaseBackupManager.listLocalSnapshots(context)
                .mapNotNull { file -> DatabaseBackupManager.peekBackupMetadata(file) }
                .take(5)
        } catch (_: Exception) {
            emptyList()
        }

        val latest = try {
            DatabaseBackupManager.getLatestPersistentBackup(context)
                ?.let { DatabaseBackupManager.peekBackupMetadata(it) }
                ?: snapshots.firstOrNull()
        } catch (_: Exception) {
            snapshots.firstOrNull()
        }

        return DatabaseRecoveryPromptState(
            reason = reason,
            quarantinePath = quarantinePath,
            latestBackup = latest,
            availableBackups = snapshots
        )
    }

    /**
     * Probes SQLite connection open, `SELECT 1`, `PRAGMA quick_check(1)`, and core table readability.
     * Returns `null` when healthy, or a human-readable diagnostic reason string when unhealthy.
     */
    private fun probeDatabaseHealth(context: Context): String? {
        return try {
            val db = AppDatabase.getDatabase(context)
            val sdb = db.openHelper.writableDatabase
            if (!sdb.isOpen) {
                return "SQLite database connection failed to open."
            }
            var selectOk = false
            sdb.query("SELECT 1").use { cursor ->
                if (cursor.moveToFirst()) {
                    selectOk = cursor.getInt(0) == 1
                }
            }
            if (!selectOk) {
                return "SQLite database did not respond to basic health probe."
            }

            var integrityResult = "ok"
            sdb.query("PRAGMA quick_check(1)").use { cursor ->
                if (cursor.moveToFirst()) {
                    integrityResult = cursor.getString(0) ?: "unknown"
                }
            }
            if (!integrityResult.equals("ok", ignoreCase = true)) {
                return "SQLite integrity check failed: $integrityResult"
            }

            // Confirm core table exists and is readable without page corruption.
            sdb.query("SELECT COUNT(*) FROM store_config").use { cursor ->
                cursor.moveToFirst()
            }
            null
        } catch (t: Throwable) {
            try {
                CrashDiagnosticsManager.logException(context, t)
            } catch (_: Throwable) {}
            "${t.javaClass.simpleName}: ${t.localizedMessage ?: t.message ?: "Unreadable database"}"
        }
    }
}
