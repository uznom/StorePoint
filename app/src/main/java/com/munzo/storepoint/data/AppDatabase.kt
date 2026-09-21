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
        PurchaseOrderItem::class
    ],
    version = 13,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Explicit structured migrations preserving all transactions, inventory, and accounts
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_accounts ADD COLUMN barcodeId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE store_config ADD COLUMN gcashBalance REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE store_config ADD COLUMN hasGCash INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE store_config ADD COLUMN hasMaya INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE store_config ADD COLUMN hasLoad INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `parked_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `note` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `parked_transaction_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `parkedTransactionId` INTEGER NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `productName` TEXT NOT NULL,
                        `price` REAL NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        FOREIGN KEY(`parkedTransactionId`) REFERENCES `parked_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS payment_schedules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        amount REAL NOT NULL,
                        dueDate TEXT NOT NULL,
                        category TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        isPaid INTEGER NOT NULL DEFAULT 0,
                        paymentMethod TEXT NOT NULL DEFAULT 'CASH'
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `drawer_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `cashierUsername` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `type` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        FOREIGN KEY(`sessionId`) REFERENCES `cashier_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN hasStick10s INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN priceStick10s REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE products ADD COLUMN hasStick20s INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN priceStick20s REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE products ADD COLUMN hasReam INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN priceReam REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE products ADD COLUMN hasMasterCase INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN priceMasterCase REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE products ADD COLUMN hasCustomUom INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN customUomName TEXT NOT NULL DEFAULT 'Pack'")
                db.execSQL("ALTER TABLE products ADD COLUMN customUomMultiplier INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE products ADD COLUMN customUomPrice REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE products ADD COLUMN barcode10s TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN barcode20s TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN barcodeReam TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN barcodeMasterCase TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN barcodeCustomUom TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE products ADD COLUMN cost REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    // Column may already exist
                }
                try {
                    db.execSQL("ALTER TABLE transaction_items ADD COLUMN cost REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    // Column may already exist
                }
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. New table: return_transactions
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `return_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `originalTransactionId` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `cashierUsername` TEXT NOT NULL,
                        `totalRefundAmount` REAL NOT NULL,
                        `refundMethod` TEXT NOT NULL,
                        `returnReason` TEXT NOT NULL,
                        `customerName` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        FOREIGN KEY(`originalTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                // 2. New table: return_items
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `return_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `returnTransactionId` INTEGER NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `productName` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `unitPrice` REAL NOT NULL,
                        `refundAmount` REAL NOT NULL,
                        `uomName` TEXT NOT NULL,
                        `restockToInventory` INTEGER NOT NULL,
                        FOREIGN KEY(`returnTransactionId`) REFERENCES `return_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                // 3. New table: product_variants
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `product_variants` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `variantName` TEXT NOT NULL,
                        `uomName` TEXT NOT NULL,
                        `multiplier` INTEGER NOT NULL,
                        `price` REAL NOT NULL,
                        `cost` REAL NOT NULL,
                        `barcode` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                // 4. New table: suppliers
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `suppliers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `contactPerson` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `deliverySchedule` TEXT NOT NULL,
                        `paymentTerms` TEXT NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                """.trimIndent())

                // 5. New table: purchase_orders
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_orders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `poNumber` TEXT NOT NULL,
                        `supplierId` INTEGER NOT NULL,
                        `supplierName` TEXT NOT NULL,
                        `orderDate` INTEGER NOT NULL,
                        `expectedDeliveryDate` INTEGER,
                        `receivedDate` INTEGER,
                        `totalCost` REAL NOT NULL,
                        `status` TEXT NOT NULL,
                        `paymentStatus` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                // 6. New table: purchase_order_items
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_order_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `purchaseOrderId` INTEGER NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `productName` TEXT NOT NULL,
                        `quantityOrdered` INTEGER NOT NULL,
                        `quantityReceived` INTEGER NOT NULL,
                        `unitCost` REAL NOT NULL,
                        `totalCost` REAL NOT NULL,
                        `uomName` TEXT NOT NULL,
                        FOREIGN KEY(`purchaseOrderId`) REFERENCES `purchase_orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                // Alter existing tables safely
                try {
                    db.execSQL("ALTER TABLE products ADD COLUMN baseUom TEXT NOT NULL DEFAULT 'pc'")
                } catch (e: Exception) {}

                try {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'")
                } catch (e: Exception) {}

                try {
                    db.execSQL("ALTER TABLE transactions ADD COLUMN customerName TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {}

                try {
                    db.execSQL("ALTER TABLE transaction_items ADD COLUMN uomName TEXT NOT NULL DEFAULT 'pc'")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Ensure transaction_items has the foreign key to transactions(id)
                try {
                    var hasFk = false
                    val cursor = db.query("PRAGMA foreign_key_list(`transaction_items`)", emptyArray())
                    while (cursor.moveToNext()) {
                        val tableIdx = cursor.getColumnIndex("table")
                        if (tableIdx != -1 && cursor.getString(tableIdx).equals("transactions", ignoreCase = true)) {
                            hasFk = true
                            break
                        }
                    }
                    cursor.close()

                    if (!hasFk) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `transaction_items_new` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `transactionId` INTEGER NOT NULL,
                                `productId` INTEGER NOT NULL,
                                `productName` TEXT NOT NULL,
                                `price` REAL NOT NULL,
                                `quantity` INTEGER NOT NULL,
                                `cost` REAL NOT NULL DEFAULT 0.0,
                                `uomName` TEXT NOT NULL DEFAULT 'pc',
                                FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                            )
                        """.trimIndent())
                        db.execSQL("""
                            INSERT INTO `transaction_items_new` (`id`, `transactionId`, `productId`, `productName`, `price`, `quantity`, `cost`, `uomName`)
                            SELECT `id`, `transactionId`, `productId`, `productName`, `price`, `quantity`, `cost`, `uomName` FROM `transaction_items`
                        """.trimIndent())
                        db.execSQL("DROP TABLE `transaction_items`")
                        db.execSQL("ALTER TABLE `transaction_items_new` RENAME TO `transaction_items`")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Error verifying/rebuilding transaction_items FK in 9->10", e)
                }

                // 2. Ensure drawer_transactions has the foreign key to cashier_sessions(id)
                try {
                    var hasFk = false
                    val cursor = db.query("PRAGMA foreign_key_list(`drawer_transactions`)", emptyArray())
                    while (cursor.moveToNext()) {
                        val tableIdx = cursor.getColumnIndex("table")
                        if (tableIdx != -1 && cursor.getString(tableIdx).equals("cashier_sessions", ignoreCase = true)) {
                            hasFk = true
                            break
                        }
                    }
                    cursor.close()

                    if (!hasFk) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `drawer_transactions_new` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `sessionId` INTEGER NOT NULL,
                                `timestamp` INTEGER NOT NULL,
                                `cashierUsername` TEXT NOT NULL,
                                `amount` REAL NOT NULL,
                                `type` TEXT NOT NULL,
                                `reason` TEXT NOT NULL,
                                FOREIGN KEY(`sessionId`) REFERENCES `cashier_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                            )
                        """.trimIndent())
                        db.execSQL("""
                            INSERT INTO `drawer_transactions_new` (`id`, `sessionId`, `timestamp`, `cashierUsername`, `amount`, `type`, `reason`)
                            SELECT `id`, `sessionId`, `timestamp`, `cashierUsername`, `amount`, `type`, `reason` FROM `drawer_transactions`
                        """.trimIndent())
                        db.execSQL("DROP TABLE `drawer_transactions`")
                        db.execSQL("ALTER TABLE `drawer_transactions_new` RENAME TO `drawer_transactions`")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Error verifying/rebuilding drawer_transactions FK in 9->10", e)
                }

                // 3. Ensure parked_transaction_items has the foreign key to parked_transactions(id)
                try {
                    var hasFk = false
                    val cursor = db.query("PRAGMA foreign_key_list(`parked_transaction_items`)", emptyArray())
                    while (cursor.moveToNext()) {
                        val tableIdx = cursor.getColumnIndex("table")
                        if (tableIdx != -1 && cursor.getString(tableIdx).equals("parked_transactions", ignoreCase = true)) {
                            hasFk = true
                            break
                        }
                    }
                    cursor.close()

                    if (!hasFk) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `parked_transaction_items_new` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `parkedTransactionId` INTEGER NOT NULL,
                                `productId` INTEGER NOT NULL,
                                `productName` TEXT NOT NULL,
                                `price` REAL NOT NULL,
                                `quantity` INTEGER NOT NULL,
                                FOREIGN KEY(`parkedTransactionId`) REFERENCES `parked_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                            )
                        """.trimIndent())
                        db.execSQL("""
                            INSERT INTO `parked_transaction_items_new` (`id`, `parkedTransactionId`, `productId`, `productName`, `price`, `quantity`)
                            SELECT `id`, `parkedTransactionId`, `productId`, `productName`, `price`, `quantity` FROM `parked_transaction_items`
                        """.trimIndent())
                        db.execSQL("DROP TABLE `parked_transaction_items`")
                        db.execSQL("ALTER TABLE `parked_transaction_items_new` RENAME TO `parked_transaction_items`")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Error verifying/rebuilding parked_transaction_items FK in 9->10", e)
                }

                // 4. Ensure foreign key indices exist for performance and relational integrity
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_variants_productId` ON `product_variants` (`productId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_variants_barcode` ON `product_variants` (`barcode`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_drawer_transactions_sessionId` ON `drawer_transactions` (`sessionId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_transactionId` ON `transaction_items` (`transactionId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_productId` ON `transaction_items` (`productId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_return_transactions_originalTransactionId` ON `return_transactions` (`originalTransactionId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_return_items_returnTransactionId` ON `return_items` (`returnTransactionId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_return_items_productId` ON `return_items` (`productId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_orders_supplierId` ON `purchase_orders` (`supplierId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_order_items_purchaseOrderId` ON `purchase_order_items` (`purchaseOrderId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_order_items_productId` ON `purchase_order_items` (`productId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_parked_transaction_items_parkedTransactionId` ON `parked_transaction_items` (`parkedTransactionId`)") } catch (e: Exception) {}
                try { db.execSQL("CREATE INDEX IF NOT EXISTS `index_parked_transaction_items_productId` ON `parked_transaction_items` (`productId`)") } catch (e: Exception) {}
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN smartLoadBalance REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase", "smartLoadBalance column might already exist: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN globeLoadBalance REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase", "globeLoadBalance column might already exist: ${e.message}")
                }
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN loadServiceFee REAL NOT NULL DEFAULT 2.0")
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase", "loadServiceFee column might already exist: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN gcashServiceFee REAL NOT NULL DEFAULT 10.0")
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase", "gcashServiceFee column might already exist: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN mayaServiceFee REAL NOT NULL DEFAULT 10.0")
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase", "mayaServiceFee column might already exist: ${e.message}")
                }
                try {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN mayaBankFee REAL NOT NULL DEFAULT 15.0")
                } catch (e: Exception) {
                    android.util.Log.w("AppDatabase", "mayaBankFee column might already exist: ${e.message}")
                }
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `user_accounts_new` (
                            `username` TEXT NOT NULL PRIMARY KEY,
                            `pinHash` TEXT NOT NULL,
                            `role` TEXT NOT NULL,
                            `barcodeId` TEXT NOT NULL DEFAULT ''
                        )
                    """.trimIndent())
                    db.execSQL("""
                        INSERT INTO `user_accounts_new` (`username`, `pinHash`, `role`, `barcodeId`)
                        SELECT `username`, `passwordHash`, `role`, `barcodeId` FROM `user_accounts`
                    """.trimIndent())
                    db.execSQL("DROP TABLE `user_accounts`")
                    db.execSQL("ALTER TABLE `user_accounts_new` RENAME TO `user_accounts`")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Error migrating user_accounts in 12->13", e)
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "storepoint_pos_db"
                )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13
                )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigrationOnDowngrade()
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
                            val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                            // Allocate cache: -128000 KB (128MB) for >=512MB heap, -64000 KB (64MB) for >=256MB heap, else 32MB
                            val cacheKb = if (maxHeapMb >= 512) -128000 else if (maxHeapMb >= 256) -64000 else -32000
                            db.execSQL("PRAGMA cache_size = $cacheKb;")
                            // Enable 256MB memory-mapped I/O for ultra-fast reading from RAM
                            db.execSQL("PRAGMA mmap_size = 268435456;")
                            // Store temporary tables, indices, and sort operations in RAM memory
                            db.execSQL("PRAGMA temp_store = MEMORY;")
                            // Optimize WAL synchronization for immediate high-throughput transactions
                            db.execSQL("PRAGMA synchronous = NORMAL;")
                            // Busy timeout: prevents 'database locked' / 'no connections' errors under concurrent write loads
                            db.execSQL("PRAGMA busy_timeout = 5000;")
                            // Autocheckpoint WAL at 1000 pages to keep WAL size bounded and read performance peak
                            db.execSQL("PRAGMA wal_autocheckpoint = 1000;")
                            android.util.Log.i("AppDatabase", "Turbo Memory Engaged: cacheKb=$cacheKb, mmap=256MB, temp_store=MEMORY, busy_timeout=5000ms, heapCap=${maxHeapMb}MB")
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
            val startTime = System.currentTimeMillis()
            return try {
                val db = getDatabase(context)
                val sdb = db.openHelper.writableDatabase
                if (!sdb.isOpen) {
                    return DatabaseHealthReport(
                        isConnected = false,
                        isWritable = false,
                        integrityOk = false,
                        latencyMs = System.currentTimeMillis() - startTime,
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
                var integrityOk = false
                sdb.query("PRAGMA quick_check(1)").use { cursor ->
                    if (cursor.moveToFirst()) {
                        val res = cursor.getString(0)
                        integrityOk = res.equals("ok", ignoreCase = true)
                    }
                }
                val walFile = context.getDatabasePath("storepoint_pos_db-wal")
                val walSize = if (walFile.exists()) walFile.length() else 0L
                val latency = System.currentTimeMillis() - startTime

                DatabaseHealthReport(
                    isConnected = true,
                    isWritable = !sdb.isReadOnly,
                    integrityOk = integrityOk && queryOk,
                    latencyMs = latency,
                    cacheSizeKb = 128000L,
                    walSizeBytes = walSize,
                    message = if (integrityOk && queryOk) "Database connection active, verified, peak throughput (${latency}ms latency)" else "Database responsive with warnings"
                )
            } catch (e: Exception) {
                DatabaseHealthReport(
                    isConnected = false,
                    isWritable = false,
                    integrityOk = false,
                    latencyMs = System.currentTimeMillis() - startTime,
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
