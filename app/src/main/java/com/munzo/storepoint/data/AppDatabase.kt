package com.munzo.storepoint.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StoreConfig::class,
        User::class,
        Category::class,
        Product::class,
        CashierSession::class,
        Transaction::class,
        TransactionItem::class,
        ParkedTransaction::class,
        ParkedTransactionItem::class,
        DrawerTransaction::class,
        PaymentSchedule::class,
        ReturnTransaction::class,
        ReturnItem::class,
        ProductVariant::class,
        Supplier::class,
        PurchaseOrder::class,
        PurchaseOrderItem::class,
        WalletLedgerEntry::class,
        ShiftReconciliation::class,
        BillOfMaterials::class,
        ProductionLogEntity::class,
        InventoryMovementEntity::class,
        CustomerAccount::class,
        CustomerCreditEntry::class,
        PendingPrintJob::class
    ],
    version = 20,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract val storeConfigDao: StoreConfigDao
    abstract val userDao: UserDao
    abstract val categoryDao: CategoryDao
    abstract val productDao: ProductDao
    abstract val cashierSessionDao: CashierSessionDao
    abstract val transactionDao: TransactionDao
    abstract val parkedTransactionDao: ParkedTransactionDao
    abstract val drawerTransactionDao: DrawerTransactionDao
    abstract val paymentScheduleDao: PaymentScheduleDao
    abstract val returnDao: ReturnDao
    abstract val productVariantDao: ProductVariantDao
    abstract val supplierDao: SupplierDao
    abstract val purchaseOrderDao: PurchaseOrderDao
    abstract val walletLedgerDao: WalletLedgerDao
    abstract val shiftReconciliationDao: ShiftReconciliationDao
    abstract val productionDao: ProductionDao
    abstract val inventoryMovementDao: InventoryMovementDao
    abstract val customerCreditDao: CustomerCreditDao
    abstract val pendingPrintJobDao: PendingPrintJobDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Backward-compatible accessors delegating to DatabaseMigrations.kt
        val MIGRATION_1_2 get() = DatabaseMigrations.MIGRATION_1_2
        val MIGRATION_2_3 get() = DatabaseMigrations.MIGRATION_2_3
        val MIGRATION_3_4 get() = DatabaseMigrations.MIGRATION_3_4
        val MIGRATION_4_5 get() = DatabaseMigrations.MIGRATION_4_5
        val MIGRATION_5_6 get() = DatabaseMigrations.MIGRATION_5_6
        val MIGRATION_6_7 get() = DatabaseMigrations.MIGRATION_6_7
        val MIGRATION_7_8 get() = DatabaseMigrations.MIGRATION_7_8
        val MIGRATION_8_9 get() = DatabaseMigrations.MIGRATION_8_9
        val MIGRATION_9_10 get() = DatabaseMigrations.MIGRATION_9_10
        val MIGRATION_10_11 get() = DatabaseMigrations.MIGRATION_10_11
        val MIGRATION_11_12 get() = DatabaseMigrations.MIGRATION_11_12
        val MIGRATION_12_13 get() = DatabaseMigrations.MIGRATION_12_13
        val MIGRATION_13_14 get() = DatabaseMigrations.MIGRATION_13_14
        val MIGRATION_14_15 get() = DatabaseMigrations.MIGRATION_14_15
        val MIGRATION_15_16 get() = DatabaseMigrations.MIGRATION_15_16
        val MIGRATION_16_17 get() = DatabaseMigrations.MIGRATION_16_17
        val MIGRATION_17_18 get() = DatabaseMigrations.MIGRATION_17_18
        val MIGRATION_18_19 get() = DatabaseMigrations.MIGRATION_18_19
        val MIGRATION_19_20 get() = DatabaseMigrationsV20.MIGRATION_19_20

        const val DATABASE_NAME = "storepoint_pos_db"

        /**
         * Executes SQLite PRAGMAs that return a result row via [SupportSQLiteDatabase.query]
         * instead of `execSQL`, because Android's Framework SQLite throws `SQLiteException`
         * when `execSQL` is called on row-returning statements (`mmap_size`, `busy_timeout`,
         * `wal_autocheckpoint`).
         */
        internal fun executePragmaQuery(db: SupportSQLiteDatabase, pragma: String) {
            try {
                db.query(pragma)?.use { cursor ->
                    cursor.moveToFirst()
                }
            } catch (e: Throwable) {
                try {
                    android.util.Log.w("AppDatabase", "SQLite pragma query note ($pragma): ${e.message}")
                } catch (_: Throwable) {}
            }
        }

        internal fun executePragmaStatement(db: SupportSQLiteDatabase, pragma: String) {
            try {
                db.execSQL(pragma)
            } catch (e: Throwable) {
                try {
                    android.util.Log.w("AppDatabase", "SQLite pragma exec note ($pragma): ${e.message}")
                } catch (_: Throwable) {}
            }
        }

        fun configureConnectionPragmas(db: SupportSQLiteDatabase, isMigrating: Boolean = false) {
            // CRITICAL: Do NOT enable foreign key enforcement while a schema migration is in progress.
            // In SQLite, dropping a table while foreign_keys=ON triggers cascading deletes on child tables
            // (e.g. purchase_order_items). Foreign keys are enabled post-migration in Room Callback.onOpen.
            if (!isMigrating) {
                try {
                    db.setForeignKeyConstraintsEnabled(true)
                } catch (_: Throwable) {}
                executePragmaStatement(db, "PRAGMA foreign_keys = ON;")
            }
            val is64Bit = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.os.Process.is64Bit()
                } else false
            } catch (_: Throwable) {
                false
            }
            // Right-size cache: 16MB native page cache per connection holds active working set
            val cacheKb = if (is64Bit) -16384 else -8192
            executePragmaStatement(db, "PRAGMA cache_size = $cacheKb;")
            executePragmaStatement(db, "PRAGMA temp_store = MEMORY;")
            executePragmaStatement(db, "PRAGMA synchronous = NORMAL;")
            // Row-returning pragmas MUST use query() on Android Framework SQLite.
            val mmapSize = if (is64Bit) 134217728L else 33554432L
            executePragmaQuery(db, "PRAGMA mmap_size = $mmapSize;")
            executePragmaQuery(db, "PRAGMA busy_timeout = 5000;")
        }

        /**
         * Safely closes and clears the singleton Room instance so [DatabaseRecoveryManager]
         * can quarantine a corrupted database file and reopen a fresh instance without stale handles.
         */
        fun resetInstance() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase", "Error closing previous AppDatabase instance: ${e.message}")
                } finally {
                    INSTANCE = null
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DATABASE_NAME
                    )
                        .openHelperFactory(PragmaConfiguringOpenHelperFactory())
                        .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                        // NOTE: Intentionally NO silent destructive-fallback migration for forward upgrades.
                        // Corrupted or unreadable databases are explicitly quarantined and recovered via
                        // DatabaseRecoveryManager, which prompts the operator to restore a backup.
                        .addCallback(object : RoomDatabase.Callback() {
                            override fun onOpen(db: SupportSQLiteDatabase) {
                                super.onOpen(db)
                                try {
                                    db.setForeignKeyConstraintsEnabled(true)
                                } catch (_: Exception) {}
                                executePragmaStatement(db, "PRAGMA foreign_keys = ON;")
                                configureConnectionPragmas(db, isMigrating = false)
                                executePragmaQuery(db, "PRAGMA wal_autocheckpoint = 1000;")
                            }
                        })
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }

        fun checkConnectionHealth(context: Context): DatabaseHealthReport {
            val startNano = System.nanoTime()
            return try {
                val db = getDatabase(context)
                val sdb = db.openHelper.writableDatabase
                if (!sdb.isOpen) {
                    return DatabaseHealthReport(
                        isConnected = false,
                        isWritable = false,
                        integrityOk = false,
                        latencyMs = (System.nanoTime() - startNano) / 1_000_000L,
                        cacheSizeKb = 0L,
                        walSizeBytes = 0L,
                        message = "SQLite connection is not open (No Connections)"
                    )
                }
                var queryOk = false
                sdb.query("SELECT 1").use { cursor ->
                    if (cursor.moveToFirst()) {
                        queryOk = cursor.getInt(0) == 1
                    }
                }
                val latency = (System.nanoTime() - startNano) / 1_000_000L

                var integrityOk = false
                sdb.query("PRAGMA quick_check(1)").use { cursor ->
                    if (cursor.moveToFirst()) {
                        val res = cursor.getString(0)
                        integrityOk = res.equals("ok", ignoreCase = true)
                    }
                }
                val walFile = context.getDatabasePath("storepoint_pos_db-wal")
                val walSize = if (walFile.exists()) walFile.length() else 0L

                var cacheKb = 16384L
                try {
                    sdb.query("PRAGMA cache_size").use { c ->
                        if (c.moveToFirst()) {
                            val raw = c.getLong(0)
                            cacheKb = if (raw < 0) -raw else (raw * 4096) / 1024
                        }
                    }
                } catch (_: Exception) {}
                if (cacheKb <= 0L) cacheKb = 16384L

                DatabaseHealthReport(
                    isConnected = true,
                    isWritable = !sdb.isReadOnly,
                    integrityOk = integrityOk && queryOk,
                    latencyMs = latency,
                    cacheSizeKb = cacheKb,
                    walSizeBytes = walSize,
                    message = if (integrityOk && queryOk) "Database connection active, verified, peak throughput (${latency}ms latency)" else "Database responsive with warnings"
                )
            } catch (e: Exception) {
                DatabaseHealthReport(
                    isConnected = false,
                    isWritable = false,
                    integrityOk = false,
                    latencyMs = (System.nanoTime() - startNano) / 1_000_000L,
                    cacheSizeKb = 0L,
                    walSizeBytes = 0L,
                    message = "Connection failure: ${e.message}"
                )
            }
        }
    }
}

data class DatabaseHealthReport(
    val isConnected: Boolean,
    val isWritable: Boolean,
    val integrityOk: Boolean,
    val latencyMs: Long,
    val cacheSizeKb: Long,
    val walSizeBytes: Long,
    val message: String
)

/**
 * SQLite OpenHelper Factory that intercepts the Android framework `onConfigure` lifecycle hook.
 *
 * In SQLite WAL mode, reader queries run on concurrent pooled connections, while writes run
 * on the primary connection. Overriding onConfigure guarantees that RAM cache sizing,
 * memory-mapped I/O, synchronous flags, and busy timeouts are actively applied to every
 * single connection created in the pool.
 */
class PragmaConfiguringOpenHelperFactory(
    private val delegate: androidx.sqlite.db.SupportSQLiteOpenHelper.Factory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
) : androidx.sqlite.db.SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration): androidx.sqlite.db.SupportSQLiteOpenHelper {
        val wrappedCallback = object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(configuration.callback.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                configuration.callback.onConfigure(db)
                // Do NOT call db.version or enable foreign keys inside onConfigure:
                // migrations haven't run yet and querying user_version during early SQLite
                // configuration can fail on Android Framework SQLite. Foreign keys are
                // enabled post-migration inside RoomDatabase.Callback.onOpen().
                AppDatabase.configureConnectionPragmas(db, isMigrating = true)
            }
            override fun onCreate(db: SupportSQLiteDatabase) = configuration.callback.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = configuration.callback.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = configuration.callback.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = configuration.callback.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) = configuration.callback.onCorruption(db)
        }
        val builder = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name)
            .callback(wrappedCallback)
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery)

        return delegate.create(builder.build())
    }
}
