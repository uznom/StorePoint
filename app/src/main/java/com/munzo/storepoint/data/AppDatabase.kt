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

        fun configureConnectionPragmas(db: SupportSQLiteDatabase, isMigrating: Boolean = false) {
            // CRITICAL: Do NOT enable foreign key enforcement while a schema migration is in progress.
            // In SQLite, dropping a table while foreign_keys=ON triggers cascading deletes on child tables
            // (e.g. purchase_order_items). Foreign keys are enabled post-migration in Room Callback.onOpen.
            if (!isMigrating) {
                try {
                    db.setForeignKeyConstraintsEnabled(true)
                } catch (_: Exception) {}
                try {
                    db.execSQL("PRAGMA foreign_keys = ON;")
                } catch (_: Exception) {}
            }
            try {
                val is64Bit = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.os.Process.is64Bit()
                } else false
                // Right-size cache: 16MB native page cache per connection holds active working set
                val cacheKb = if (is64Bit) -16384 else -8192
                db.execSQL("PRAGMA cache_size = $cacheKb;")
                val mmapSize = if (is64Bit) 134217728L else 33554432L
                db.execSQL("PRAGMA mmap_size = $mmapSize;")
                db.execSQL("PRAGMA temp_store = MEMORY;")
                db.execSQL("PRAGMA synchronous = NORMAL;")
                db.execSQL("PRAGMA busy_timeout = 5000;")
            } catch (e: Exception) {
                android.util.Log.w("AppDatabase", "Turbo SQLite connection configure note: ${e.message}")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "storepoint_pos_db"
                )
                .openHelperFactory(PragmaConfiguringOpenHelperFactory())
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                                 // Room 2.7 deprecates the no-arg overload; the boolean overload's
                 // `dropAllTables = false` is the behavior-identical replacement
                 // (default for the Builder flag is false, matching the legacy no-arg call).
                 .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
                // NOTE: Intentionally NO destructive-fallback migration for forward upgrades.
                // This app declares hasFragileUserData=true (POS sales + inventory).
                // If a schema upgrade ever fails/mismatches, Room will fail to open the
                // database instead of silently wiping all rows. Recovery path is the
                // JSON backup restore flow (DatabaseBackupManager).
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        try {
                            db.setForeignKeyConstraintsEnabled(true)
                        } catch (_: Exception) {}
                        db.execSQL("PRAGMA foreign_keys = ON;")

                        // Turbo Memory Optimization: Maximize RAM cache & memory-mapped I/O based on device capabilities
                        try {
                            val is64Bit = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                android.os.Process.is64Bit()
                            } else false
                            // Right-size cache: 16MB native page cache holds the entire active POS dataset in RAM without native OOM
                            val cacheKb = if (is64Bit) -16384 else -8192
                            db.execSQL("PRAGMA cache_size = $cacheKb;")
                            // Bound memory-mapped I/O: 128MB on 64-bit, 32MB on 32-bit POS terminals to prevent address fragmentation
                            val mmapSize = if (is64Bit) 134217728L else 33554432L
                            db.execSQL("PRAGMA mmap_size = $mmapSize;")
                            // Store temporary tables, indices, and sort operations in RAM memory
                            db.execSQL("PRAGMA temp_store = MEMORY;")
                            // Optimize WAL synchronization for immediate high-throughput transactions
                            db.execSQL("PRAGMA synchronous = NORMAL;")
                            // Busy timeout: prevents 'database locked' / 'no connections' errors under concurrent write loads
                            db.execSQL("PRAGMA busy_timeout = 5000;")
                            // Autocheckpoint WAL at 1000 pages to keep WAL size bounded and read performance peak
                            db.execSQL("PRAGMA wal_autocheckpoint = 1000;")
                            android.util.Log.i("AppDatabase", "Turbo Memory Engaged: cacheKb=$cacheKb, mmap=${mmapSize / (1024 * 1024)}MB, temp_store=MEMORY, busy_timeout=5000ms, is64Bit=$is64Bit")
                        } catch (e: Exception) {
                            android.util.Log.w("AppDatabase", "Turbo SQLite memory config note: ${e.message}")
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
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
                val isMigrating = db.version > 0 && db.version < configuration.callback.version
                AppDatabase.configureConnectionPragmas(db, isMigrating = isMigrating)
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
