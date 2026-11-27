package com.munzo.storepoint.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Encapsulates all forward schema migrations for AppDatabase.
 * Extracted from AppDatabase to adhere to high cohesion, low coupling,
 * and the 800-line maintainability ceiling.
 */
object DatabaseMigrations {
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

        /**
         * Returns true when [table] already has a column named [column] (PRAGMA table_info).
         * Used by migrations below so column additions are idempotent WITHOUT swallowing
         * unrelated failures (audit M4: blanket try/catch previously committed partial schemas).
         */
        private fun SupportSQLiteDatabase.columnExists(table: String, column: String): Boolean {
            query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }

        /** Returns true when [table] exists in the database (sqlite_master lookup). */
        private fun SupportSQLiteDatabase.tableExists(table: String): Boolean {
            query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existence guards replace blanket try/catch so genuine SQL failures
                // propagate and roll the migration transaction back (fail-closed).
                if (!db.columnExists("store_config", "smartLoadBalance")) {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN smartLoadBalance REAL NOT NULL DEFAULT 0.0")
                }
                if (!db.columnExists("store_config", "globeLoadBalance")) {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN globeLoadBalance REAL NOT NULL DEFAULT 0.0")
                }
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existence guards replace blanket try/catch so genuine SQL failures
                // propagate and roll the migration transaction back (fail-closed).
                if (!db.columnExists("store_config", "loadServiceFee")) {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN loadServiceFee REAL NOT NULL DEFAULT 2.0")
                }
                if (!db.columnExists("store_config", "gcashServiceFee")) {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN gcashServiceFee REAL NOT NULL DEFAULT 10.0")
                }
                if (!db.columnExists("store_config", "mayaServiceFee")) {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN mayaServiceFee REAL NOT NULL DEFAULT 10.0")
                }
                if (!db.columnExists("store_config", "mayaBankFee")) {
                    db.execSQL("ALTER TABLE store_config ADD COLUMN mayaBankFee REAL NOT NULL DEFAULT 15.0")
                }
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Idempotent guard: skip if a prior (possibly interrupted) upgrade already ran.
                if (db.columnExists("user_accounts", "pinHash")) return
                if (db.tableExists("user_accounts")) {
                    // Drop any leftover from a previously interrupted run before rebuilding.
                    db.execSQL("DROP TABLE IF EXISTS `user_accounts_new`")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `user_accounts_new` (
                            `username` TEXT NOT NULL PRIMARY KEY,
                            `pinHash` TEXT NOT NULL,
                            `role` TEXT NOT NULL,
                            `barcodeId` TEXT NOT NULL DEFAULT ''
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO `user_accounts_new` (`username`, `pinHash`, `role`, `barcodeId`)
                        SELECT `username`, `passwordHash`, `role`, `barcodeId` FROM `user_accounts`
                        """.trimIndent()
                    )
                    db.execSQL("DROP TABLE `user_accounts`")
                    db.execSQL("ALTER TABLE `user_accounts_new` RENAME TO `user_accounts`")
                } else if (db.tableExists("user_accounts_new")) {
                    // Partial prior run already dropped the old table; finish the rename.
                    db.execSQL("ALTER TABLE `user_accounts_new` RENAME TO `user_accounts`")
                } else {
                    // No recoverable state — throw so the migration transaction rolls back
                    // (fail-closed) instead of silently leaving an inconsistent schema.
                    error("MIGRATION_12_13: user_accounts table missing; cannot migrate 12 -> 13")
                }
            }
        }

        /**
         * Adds biometric enrollment + forced 4-digit PIN reset state to `user_accounts`.
         *
         * Every pre-existing row is marked `pinResetRequired = 1` because all stored
         * credentials at v13 are 6-digit. `biometricEnrolled` defaults to 0 so nobody
         * is implicitly trusted until they actually bind a fingerprint.
         *
         * Non-destructive: uses ALTER TABLE with columnExists guards (the established
         * fail-closed pattern here) so an interrupted run can be re-applied safely.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.tableExists("user_accounts")) {
                    error("MIGRATION_13_14: user_accounts table missing; cannot migrate 13 -> 14")
                }
                if (!db.columnExists("user_accounts", "biometricEnrolled")) {
                    db.execSQL(
                        "ALTER TABLE user_accounts ADD COLUMN biometricEnrolled INTEGER NOT NULL DEFAULT 0"
                    )
                }
                if (!db.columnExists("user_accounts", "pinResetRequired")) {
                    db.execSQL(
                        "ALTER TABLE user_accounts ADD COLUMN pinResetRequired INTEGER NOT NULL DEFAULT 1"
                    )
                }
            }
        }

        /**
         * Adds the analytics hot-path indices.
         *
         * Purely additive and non-destructive: only CREATE INDEX IF NOT EXISTS, so no
         * rows are touched and a re-run after an interrupted upgrade is a no-op. Each
         * index backs a specific query path:
         *  - `transactions(timestamp)`               daily/period sales rollups
         *  - `transactions(cashierUsername)`         per-cashier breakdown
         *  - `transactions(paymentMethod,timestamp)`  payment split over a date range
         *  - `transaction_items(productId,transactionId)` top-sellers grouping
         *  - `products(name)`                        POS catalogue search (hottest read)
         *  - `products(stockCount)`                  low-stock badges
         *
         * Indices are built once here rather than in the entity annotations alone,
         * because Room only emits CREATE INDEX for a *fresh* install.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val statements = listOf(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)",
                    "CREATE INDEX IF NOT EXISTS `index_transactions_cashierUsername` ON `transactions` (`cashierUsername`)",
                    "CREATE INDEX IF NOT EXISTS `index_transactions_paymentMethod_timestamp` ON `transactions` (`paymentMethod`, `timestamp`)",
                    "CREATE INDEX IF NOT EXISTS `index_transaction_items_productId_transactionId` ON `transaction_items` (`productId`, `transactionId`)",
                    "CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)",
                    "CREATE INDEX IF NOT EXISTS `index_products_stockCount` ON `products` (`stockCount`)"
                )
                // No blanket try/catch: a genuine failure here (missing table, disk
                // error) must abort the migration and roll back, rather than silently
                // committing a half-indexed schema (audit M4).
                statements.forEach(db::execSQL)
            }
        }

        /**
         * Adds the auditable wallet ledger.
         *
         * Purely additive and fail-closed, matching MIGRATION_14_15: no blanket
         * try/catch, so a genuine failure aborts and rolls back rather than
         * committing a half-created schema (audit M4).
         *
         * **Seeding matters.** Upgrading terminals already have live balances in
         * `store_config` (the owner has been running the float all along). Creating an
         * empty ledger would make the system-derived balance read ₱0.00 and report a
         * phantom ₱X discrepancy at the next shift close. So each wallet with a
         * non-zero balance gets a single `OPENING_BALANCE` entry carrying that figure,
         * making `SUM(delta)` equal the pre-upgrade balance exactly.
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `wallet_ledger` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `walletType` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `actorUsername` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `delta` REAL NOT NULL,
                        `balanceAfter` REAL NOT NULL,
                        `reference` TEXT NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallet_ledger_walletType` ON `wallet_ledger` (`walletType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallet_ledger_timestamp` ON `wallet_ledger` (`timestamp`)")

                // Seed one opening row per non-zero wallet so the ledger's running
                // total reconciles with the balance the terminal already had.
                db.execSQL("""
                    INSERT INTO `wallet_ledger` (`walletType`, `timestamp`, `actorUsername`, `type`, `delta`, `balanceAfter`, `reference`, `notes`)
                    SELECT 'GCASH', 0, 'system', 'OPENING_BALANCE', `gcashBalance`, `gcashBalance`, '', 'Carried over from pre-ledger balance on upgrade to v16'
                    FROM `store_config` WHERE `id` = 1 AND `gcashBalance` != 0.0
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `wallet_ledger` (`walletType`, `timestamp`, `actorUsername`, `type`, `delta`, `balanceAfter`, `reference`, `notes`)
                    SELECT 'SMART', 0, 'system', 'OPENING_BALANCE', `smartLoadBalance`, `smartLoadBalance`, '', 'Carried over from pre-ledger balance on upgrade to v16'
                    FROM `store_config` WHERE `id` = 1 AND `smartLoadBalance` != 0.0
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `wallet_ledger` (`walletType`, `timestamp`, `actorUsername`, `type`, `delta`, `balanceAfter`, `reference`, `notes`)
                    SELECT 'GLOBE', 0, 'system', 'OPENING_BALANCE', `globeLoadBalance`, `globeLoadBalance`, '', 'Carried over from pre-ledger balance on upgrade to v16'
                    FROM `store_config` WHERE `id` = 1 AND `globeLoadBalance` != 0.0
                """.trimIndent())
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. shift_reconciliations
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `shift_reconciliations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `cashierUsername` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `count1000` INTEGER NOT NULL DEFAULT 0,
                        `count500` INTEGER NOT NULL DEFAULT 0,
                        `count200` INTEGER NOT NULL DEFAULT 0,
                        `count100` INTEGER NOT NULL DEFAULT 0,
                        `count50` INTEGER NOT NULL DEFAULT 0,
                        `count20` INTEGER NOT NULL DEFAULT 0,
                        `countCoins` REAL NOT NULL DEFAULT 0.0,
                        `totalCountedCash` REAL NOT NULL DEFAULT 0.0,
                        `expectedCash` REAL NOT NULL DEFAULT 0.0,
                        `cashVariance` REAL NOT NULL DEFAULT 0.0,
                        `countedGCash` REAL NOT NULL DEFAULT 0.0,
                        `expectedGCash` REAL NOT NULL DEFAULT 0.0,
                        `gcashVariance` REAL NOT NULL DEFAULT 0.0,
                        `countedSmartLoad` REAL NOT NULL DEFAULT 0.0,
                        `expectedSmartLoad` REAL NOT NULL DEFAULT 0.0,
                        `smartLoadVariance` REAL NOT NULL DEFAULT 0.0,
                        `countedGlobeLoad` REAL NOT NULL DEFAULT 0.0,
                        `expectedGlobeLoad` REAL NOT NULL DEFAULT 0.0,
                        `globeLoadVariance` REAL NOT NULL DEFAULT 0.0,
                        `hasDiscrepancy` INTEGER NOT NULL DEFAULT 0,
                        `managerOverrideApproved` INTEGER NOT NULL DEFAULT 0,
                        `managerUsername` TEXT NOT NULL DEFAULT '',
                        `discrepancyReason` TEXT NOT NULL DEFAULT '',
                        `receiptPrinted` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`sessionId`) REFERENCES `cashier_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_reconciliations_sessionId` ON `shift_reconciliations` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_reconciliations_timestamp` ON `shift_reconciliations` (`timestamp`)")

                // 2. bill_of_materials
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bill_of_materials` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `rawMaterialProductId` INTEGER NOT NULL,
                        `targetRetailProductId` INTEGER NOT NULL,
                        `rawQuantityNeeded` REAL NOT NULL,
                        `producedRetailYield` REAL NOT NULL,
                        `wasteLossPercentage` REAL NOT NULL DEFAULT 0.0,
                        `notes` TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(`rawMaterialProductId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`targetRetailProductId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_of_materials_rawMaterialProductId` ON `bill_of_materials` (`rawMaterialProductId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_of_materials_targetRetailProductId` ON `bill_of_materials` (`targetRetailProductId`)")

                // 3. production_runs
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `production_runs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productionCode` TEXT NOT NULL,
                        `rawMaterialProductId` INTEGER NOT NULL,
                        `rawMaterialName` TEXT NOT NULL,
                        `rawMaterialQtyUsed` REAL NOT NULL,
                        `rawMaterialUnitCost` REAL NOT NULL,
                        `targetRetailProductId` INTEGER NOT NULL,
                        `targetRetailProductName` TEXT NOT NULL,
                        `targetRetailQtyProduced` REAL NOT NULL,
                        `derivedRetailUnitCost` REAL NOT NULL,
                        `retailSellingPrice` REAL NOT NULL,
                        `wasteLossQty` REAL NOT NULL DEFAULT 0.0,
                        `operatorUsername` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_runs_productionCode` ON `production_runs` (`productionCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_runs_rawMaterialProductId` ON `production_runs` (`rawMaterialProductId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_runs_targetRetailProductId` ON `production_runs` (`targetRetailProductId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_production_runs_timestamp` ON `production_runs` (`timestamp`)")

                // 4. inventory_movements
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `inventory_movements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `productName` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `actorUsername` TEXT NOT NULL,
                        `movementType` TEXT NOT NULL,
                        `quantityDelta` REAL NOT NULL,
                        `stockBefore` INTEGER NOT NULL,
                        `stockAfter` INTEGER NOT NULL,
                        `reference` TEXT NOT NULL DEFAULT '',
                        `notes` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_movements_productId` ON `inventory_movements` (`productId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_movements_timestamp` ON `inventory_movements` (`timestamp`)")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `trackInventory` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `colorAccent` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `iconBadge` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `product_variants` ADD COLUMN `stockCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Soft catalog archiving columns & composite index
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_isArchived_name` ON `products` (`isArchived`, `name`)")

                // 2. Relational integrity: migrate purchase_orders to ON DELETE RESTRICT
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `purchase_orders_new` (
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
                        FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `purchase_orders_new` (`id`, `poNumber`, `supplierId`, `supplierName`, `orderDate`, `expectedDeliveryDate`, `receivedDate`, `totalCost`, `status`, `paymentStatus`, `notes`)
                    SELECT `id`, `poNumber`, `supplierId`, `supplierName`, `orderDate`, `expectedDeliveryDate`, `receivedDate`, `totalCost`, `status`, `paymentStatus`, `notes` FROM `purchase_orders`
                """.trimIndent())
                db.execSQL("DROP TABLE `purchase_orders`")
                db.execSQL("ALTER TABLE `purchase_orders_new` RENAME TO `purchase_orders`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_orders_supplierId` ON `purchase_orders` (`supplierId`)")
            }
        }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
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
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        DatabaseMigrationsV20.MIGRATION_19_20
    )
}
