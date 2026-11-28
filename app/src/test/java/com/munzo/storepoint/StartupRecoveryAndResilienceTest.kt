package com.munzo.storepoint

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.munzo.storepoint.data.DatabaseRecoveryManager
import com.munzo.storepoint.util.CrashDiagnosticsManager
import com.munzo.storepoint.util.StartupBootGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StartupRecoveryAndResilienceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun bootGuard_healthyFirstLaunch_doesNotEnterSafeMode() {
        val eval = StartupBootGuard.evaluateBootSnapshot(
            StartupBootGuard.BootSnapshot(),
            nowMs = 1_000_000L
        )
        assertFalse(eval.enterSafeMode)
        assertEquals(0, eval.consecutiveCrashes)
    }

    @Test
    fun bootGuard_singleUnfinishedLaunch_incrementsCountWithoutTrippingSafeMode() {
        val eval = StartupBootGuard.evaluateBootSnapshot(
            StartupBootGuard.BootSnapshot(
                bootInProgress = true,
                consecutiveStartupCrashes = 0,
                lastBootAttemptMs = 995_000L
            ),
            nowMs = 1_000_000L
        )
        assertFalse(eval.enterSafeMode)
        assertEquals(1, eval.consecutiveCrashes)
    }

    @Test
    fun bootGuard_twoConsecutiveStartupCrashes_tripsSafeMode() {
        val eval = StartupBootGuard.evaluateBootSnapshot(
            StartupBootGuard.BootSnapshot(
                bootInProgress = true,
                consecutiveStartupCrashes = 1,
                lastBootAttemptMs = 998_000L,
                lastCrashException = "java.lang.IllegalStateException",
                lastCrashMessage = "Room schema integrity failure"
            ),
            nowMs = 1_000_000L
        )
        assertTrue(eval.enterSafeMode)
        assertEquals(2, eval.consecutiveCrashes)
        assertEquals("java.lang.IllegalStateException", eval.lastCrashException)
        assertEquals("Room schema integrity failure", eval.lastCrashMessage)
    }

    @Test
    fun bootGuard_forceSafeMode_immediatelyEntersSafeMode() {
        val eval = StartupBootGuard.evaluateBootSnapshot(
            StartupBootGuard.BootSnapshot(
                forceSafeMode = true,
                lastCrashException = "android.database.sqlite.SQLiteException",
                lastCrashMessage = "PRAGMA failure"
            ),
            nowMs = 1_000_000L
        )
        assertTrue(eval.enterSafeMode)
        assertTrue(eval.reason.contains("Startup failure", ignoreCase = true))
    }

    @Test
    fun bootGuard_computeNextCrashCount_onlyCountsWithinStartupWindowOrBootInProgress() {
        assertEquals(
            1,
            StartupBootGuard.computeNextCrashCount(
                currentCount = 0,
                bootInProgress = true,
                lastBootAttemptMs = 100_000L,
                crashTimeMs = 105_000L
            )
        )
        assertEquals(
            2,
            StartupBootGuard.computeNextCrashCount(
                currentCount = 1,
                bootInProgress = false,
                lastBootAttemptMs = 100_000L,
                crashTimeMs = 110_000L
            )
        )
        // Crash 5 minutes after normal boot should NOT count as a startup crash loop
        assertEquals(
            0,
            StartupBootGuard.computeNextCrashCount(
                currentCount = 0,
                bootInProgress = false,
                lastBootAttemptMs = 100_000L,
                crashTimeMs = 400_000L
            )
        )
    }

    @Test
    fun databaseRecovery_quarantineMovesPrimaryAndWalSidecarsAndLeavesOriginClean() {
        val dbDir = tempFolder.newFolder("databases")
        val quarantineRoot = tempFolder.newFolder("db_quarantine")

        val dbFile = File(dbDir, "storepoint_pos_db").apply { writeText("corrupt-db-bytes") }
        val walFile = File(dbDir, "storepoint_pos_db-wal").apply { writeText("corrupt-wal") }
        val shmFile = File(dbDir, "storepoint_pos_db-shm").apply { writeText("corrupt-shm") }
        val journalFile = File(dbDir, "storepoint_pos_db-journal").apply { writeText("corrupt-journal") }

        val result = DatabaseRecoveryManager.quarantineDatabaseFiles(
            dbFile = dbFile,
            quarantineRoot = quarantineRoot,
            timestampLabel = "20261128_010000"
        )

        assertNotNull(result.quarantinedDir)
        assertEquals(4, result.movedFiles.size)
        assertFalse(dbFile.exists())
        assertFalse(walFile.exists())
        assertFalse(shmFile.exists())
        assertFalse(journalFile.exists())
        assertTrue(File(result.quarantinedDir, "storepoint_pos_db").exists())
        assertTrue(File(result.quarantinedDir, "storepoint_pos_db-wal").exists())
        assertTrue(File(result.quarantinedDir, "storepoint_pos_db-shm").exists())
        assertTrue(File(result.quarantinedDir, "storepoint_pos_db-journal").exists())
    }

    @Test
    fun databaseRecovery_quarantinePrunesOlderFoldersBeyondRetentionLimit() {
        val dbDir = tempFolder.newFolder("databases_prune")
        val quarantineRoot = tempFolder.newFolder("db_quarantine_prune")

        val labels = listOf("20261125_010000", "20261126_010000", "20261127_010000", "20261128_010000")
        for (label in labels) {
            val dbFile = File(dbDir, "storepoint_pos_db").apply { writeText("bytes-$label") }
            DatabaseRecoveryManager.quarantineDatabaseFiles(
                dbFile = dbFile,
                quarantineRoot = quarantineRoot,
                timestampLabel = label,
                maxRetained = 3
            )
        }

        val remaining = quarantineRoot.listFiles { f -> f.isDirectory }?.map { it.name }?.sorted().orEmpty()
        assertEquals(
            listOf("quarantine_20261126_010000", "quarantine_20261127_010000", "quarantine_20261128_010000"),
            remaining
        )
    }

    @Test
    fun crashDiagnostics_formatSingleCrashReport_containsKeyFields() {
        val record = CrashDiagnosticsManager.CrashRecord(
            timestamp = 1700000000000L,
            dateFormatted = "2026-11-28 01:15:00",
            exceptionName = "IllegalStateException",
            message = "Test startup failure",
            stackTrace = "java.lang.IllegalStateException: Test startup failure\n\tat MainActivity.onCreate",
            deviceInfo = "Pixel 8 (API 35)",
            activeCashier = "admin"
        )
        val formatted = CrashDiagnosticsManager.formatSingleCrashReport(record)
        assertTrue(formatted.contains("IllegalStateException"))
        assertTrue(formatted.contains("Test startup failure"))
        assertTrue(formatted.contains("MainActivity.onCreate"))
        assertTrue(formatted.contains("Pixel 8 (API 35)"))
    }

    @Test
    fun extendedFilledIcons_everyCustomIconParsesCleanlyWithoutThrowing() {
        val clazz = Class.forName("androidx.compose.material.icons.filled.ExtendedFilledIconsKt")
        val getters = clazz.declaredMethods.filter { method ->
            method.name.startsWith("get") &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Icons.Filled::class.java &&
                method.returnType == ImageVector::class.java
        }
        assertTrue("Expected custom Material Icons getters to exist", getters.size >= 50)
        for (getter in getters) {
            val icon = getter.invoke(null, Icons.Filled) as? ImageVector
            assertNotNull("Icon getter ${getter.name} returned null", icon)
            assertTrue("Icon ${getter.name} has empty name", icon!!.name.isNotBlank())
        }
    }

    @Test
    fun appDatabase_configureConnectionPragmas_usesQueryForRowReturningPragmas() {
        val execCalls = mutableListOf<String>()
        val queryCalls = mutableListOf<String>()

        val dbProxy = java.lang.reflect.Proxy.newProxyInstance(
            androidx.sqlite.db.SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (args != null && args.isNotEmpty() && args[0] is String) {
                val sql = (args[0] as String).trim()
                if (method.name == "execSQL") execCalls.add(sql)
                if (method.name == "query") queryCalls.add(sql)
            }
            null
        } as androidx.sqlite.db.SupportSQLiteDatabase

        com.munzo.storepoint.data.AppDatabase.configureConnectionPragmas(dbProxy, isMigrating = false)

        assertTrue(
            "mmap_size must be executed via query(), not execSQL()",
            queryCalls.any { it.contains("mmap_size", ignoreCase = true) }
        )
        assertTrue(
            "busy_timeout must be executed via query(), not execSQL()",
            queryCalls.any { it.contains("busy_timeout", ignoreCase = true) }
        )
        assertFalse(
            "mmap_size must NOT be executed via execSQL()",
            execCalls.any { it.contains("mmap_size", ignoreCase = true) }
        )
        assertFalse(
            "busy_timeout must NOT be executed via execSQL()",
            execCalls.any { it.contains("busy_timeout", ignoreCase = true) }
        )
    }
}
